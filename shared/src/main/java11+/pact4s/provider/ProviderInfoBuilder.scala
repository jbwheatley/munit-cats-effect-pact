/*
 * Copyright 2021-2021 io.github.jbwheatley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pact4s
package provider

import au.com.dius.pact.core.model.{FileSource => PactJVMFileSource}
import au.com.dius.pact.core.support.Auth
import au.com.dius.pact.provider.{PactBrokerOptions, PactVerification, ProviderInfo}
import org.apache.hc.core5.http.HttpRequest
import pact4s.provider.Authentication.{BasicAuth, TokenAuth}
import pact4s.provider.PactSource.{FileSource, PactBroker, PactBrokerWithSelectors, PactBrokerWithTags}
import pact4s.provider.VerificationSettings.AnnotatedMethodVerificationSettings

import java.net.URL
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.function.Consumer
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

/** Interface for defining the provider that consumer pacts are verified against. Internally gets converted to
  * au.com.dius.pact.provider.ProviderInfo during verification.
  *
  * @param name
  *   the name of the provider
  * @param protocol
  *   e.g. http or https
  * @param host
  *   mock provider host
  * @param port
  *   mock provider port
  * @param path
  *   address of the mock provider server is {protocol}://{host}:{port}{path}
  * @param pactSource
  *   pacts to verify can come either from a file location, or from a pact broker.
  * @param stateChangeUrl
  *   full url of the mock provider endpoint that can be used for setting provider state before each pact with state is
  *   run. state is sent as JSON of the form {"state": "state goes here"}. Can also be set using
  *   [[ProviderInfoBuilder#withStateChangeEndpoint]] just by providing the path.
  * @param verificationSettings
  *   Required if verifying message pacts using the old java-y annotated method search. Not needed if using the response
  *   factory method.
  *
  * @param requestFilter
  *   Apply filters to certain consumer requests. The most common use case for this is adding auth headers to requests
  * @see
  *   https://docs.pact.io/faq/#how-do-i-test-oauth-or-other-security-headers
  */
final case class ProviderInfoBuilder(
    name: String,
    protocol: String,
    host: String,
    port: Int,
    path: String,
    pactSource: PactSource,
    stateChangeUrl: Option[String] = None,
    verificationSettings: Option[VerificationSettings] = None,
    requestFilter: ProviderRequest => Option[ProviderRequestFilter] = _ => None
) {
  def withProtocol(protocol: String): ProviderInfoBuilder = this.copy(protocol = protocol)
  def withHost(host: String): ProviderInfoBuilder         = this.copy(host = host)
  def withPort(port: Int): ProviderInfoBuilder            = this.copy(port = port)
  def withPath(path: String): ProviderInfoBuilder         = this.copy(path = path)
  def withVerificationSettings(settings: VerificationSettings): ProviderInfoBuilder =
    this.copy(verificationSettings = Some(settings))
  def withOptionalVerificationSettings(settings: Option[VerificationSettings]): ProviderInfoBuilder =
    this.copy(verificationSettings = settings)
  def withStateChangeUrl(url: String): ProviderInfoBuilder = this.copy(stateChangeUrl = Some(url))
  def withStateChangeEndpoint(endpoint: String): ProviderInfoBuilder = {
    val endpointWithLeadingSlash = if (!endpoint.startsWith("/")) "/" + endpoint else endpoint
    this.copy(stateChangeUrl = Some(s"$protocol://$host:$port$endpointWithLeadingSlash"))
  }

  @deprecated("use withRequestFiltering instead, where request filters are composed with .andThen", "")
  def withRequestFilter(requestFilter: ProviderRequest => List[ProviderRequestFilter]): ProviderInfoBuilder =
    this.copy(requestFilter = request => requestFilter(request).reduceLeftOption(_ andThen _))

  def withRequestFiltering(requestFilter: ProviderRequest => ProviderRequestFilter): ProviderInfoBuilder =
    this.copy(requestFilter = request => Some(requestFilter(request)))

  private def pactJvmRequestFilter: HttpRequest => Unit = { request =>
    val providerRequest = ProviderRequest(
      request.getMethod,
      request.getUri,
      request.getHeaders.toList.map(h => (h.getName, h.getValue))
    )
    requestFilter(providerRequest).foreach(_.filter(request))
  }

  private[pact4s] def toProviderInfo: ProviderInfo = {
    val p = new ProviderInfo(name, protocol, host, port, path)
    verificationSettings.foreach { case AnnotatedMethodVerificationSettings(packagesToScan) =>
      p.setVerificationType(PactVerification.ANNOTATED_METHOD)
      p.setPackagesToScan(packagesToScan.asJava)
    }
    stateChangeUrl.foreach(s => p.setStateChangeUrl(new URL(s)))
    p.setRequestFilter {
      // because java
      new Consumer[HttpRequest] {
        def accept(t: HttpRequest): Unit =
          pactJvmRequestFilter(t)
      }
    }
    pactSource match {
      case broker: PactBroker => applyBrokerSourceToProvider(p, broker)
      case FileSource(consumers) =>
        consumers.foreach { case (consumer, file) =>
          p.hasPactWith(
            consumer,
            { consumer =>
              consumer.setPactSource(new PactJVMFileSource(file))
              kotlin.Unit.INSTANCE
            }
          )
        }
        p
    }
  }

  @tailrec
  private def applyBrokerSourceToProvider(
      providerInfo: ProviderInfo,
      pactSource: PactBroker
  ): ProviderInfo =
    pactSource match {
      case p @ PactBrokerWithSelectors(
            brokerUrl,
            insecureTLS,
            auth,
            enablePending,
            includeWipPactsSince,
            providerTags,
            selectors
          ) =>
        p.validate()
        val pactJvmAuth: Option[Auth] = auth.map {
          case TokenAuth(token)      => new Auth.BearerAuthentication(token)
          case BasicAuth(user, pass) => new Auth.BasicAuthentication(user, pass)
        }
        val brokerOptions: PactBrokerOptions = new PactBrokerOptions(
          enablePending,
          providerTags.map(_.toList).getOrElse(Nil).asJava,
          includeWipPactsSince.since.map(instantToDateString).orNull,
          insecureTLS,
          pactJvmAuth.orNull
        )
        providerInfo.hasPactsFromPactBrokerWithSelectors(
          brokerUrl,
          selectors.map(_.toPactJVMSelector).asJava,
          brokerOptions
        )
        auth.foreach(configureConsumers(providerInfo))
        providerInfo
      case PactBrokerWithTags(brokerUrl, insecureTLS, auth, tags) =>
        applyBrokerSourceToProvider(
          providerInfo,
          PactBrokerWithSelectors(
            brokerUrl
          )
            .withOptionalAuth(auth)
            .withSelectors(tags.map(tag => ConsumerVersionSelector().withTag(tag)))
            .withInsecureTLS(insecureTLS)
        )
    }

  private def configureConsumers(providerInfo: ProviderInfo)(auth: Authentication): Unit = {
    val authAsStringList = auth match {
      case TokenAuth(token)      => "Bearer" :: token :: Nil
      case BasicAuth(user, pass) => "Basic" :: user :: pass :: Nil
    }
    val consumers = providerInfo.getConsumers.asScala
    providerInfo.setConsumers(consumers.map { c =>
      c.setPactFileAuthentication(authAsStringList.asJava)
      c
    }.asJava)
  }

  private def instantToDateString(instant: Instant): String =
    instant
      .atOffset(
        ZoneOffset.UTC // Apologies for the euro-centrism, but as we use time relative to the epoch it doesn't really matter
      )
      .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

object ProviderInfoBuilder {

  /** Auxiliary constructor that provides some common defaults for the the mock provider address
    *
    * @param name
    *   [[ProviderInfoBuilder.name]]
    * @param pactSource
    *   [[ProviderInfoBuilder.pactSource]]
    * @return
    *   [[ProviderInfoBuilder]]
    *
    * Example usage:
    * {{{
    *   ProviderInfoBuilder(
    *       name = "Provider Service",
    *       pactSource = FileSource("Consumer Service", new File("./pacts/pact.json"))
    *     ).withPort(80)
    *     .withStateChangeEndpoint("setup")
    * }}}
    */
  def apply(name: String, pactSource: PactSource): ProviderInfoBuilder =
    ProviderInfoBuilder(name, "http", "localhost", 0, "/", pactSource)
}
