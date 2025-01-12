/*
 * Copyright 2021 io.github.jbwheatley
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
import pact4s.provider.StateManagement.StateManagementFunction
import pact4s.provider.VerificationSettings.AnnotatedMethodVerificationSettings

import java.net.URI
import java.net.URL
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.function.Consumer
import scala.annotation.{nowarn, tailrec}
import scala.jdk.CollectionConverters._
import scala.util.Try

/** Interface for defining the provider that consumer pacts are verified against. Internally gets converted to
  * au.com.dius.pact.provider.ProviderInfo during verification.
  *
  * Use the apply methods in the companion object to construct.
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
  * @param stateManagement
  *   Used for the setting of provider state before each interaction with state is run. Can be either:
  *
  * (1) the url of a endpoint on the mock provider that can configure internal state. Can be set using a full url with
  * [[ProviderInfoBuilder#withStateChangeUrl]] or simply by providing the endpoint with
  * [[ProviderInfoBuilder#withStateChangeEndpoint]]. State is sent as a json of the form {"state": "state name",
  * "params": {"param1" : "paramValue"}}. Decoders for [[ProviderState]] can be found in the json-modules, or defined by
  * the user.
  *
  * (2) a partial function [[ProviderState => Unit]] provided by ProviderInfoBuilder#withStateChangeFunction which will
  * be applied before each interaction is run. This works by using a mock internal server, the host of which can be
  * configured using [[ProviderInfoBuilder#withStateChangeFunctionConfigOverrides]]
  *
  * @param verificationSettings
  *   Required if verifying message pacts using the old java-y annotated method search. Not needed if using the response
  *   factory method.
  *
  * @param requestFilter
  *   Apply filters to certain consumer requests. The most common use case for this is adding auth headers to requests
  * @see
  *   https://docs.pact.io/faq/#how-do-i-test-oauth-or-other-security-headers
  */
final class ProviderInfoBuilder private (
    name: String,
    protocol: String,
    host: String,
    port: Int,
    path: String,
    pactSource: PactSource,
    stateManagement: Option[StateManagement],
    verificationSettings: Option[VerificationSettings],
    requestFilter: ProviderRequest => Option[ProviderRequestFilter]
) {
  private def copy(
      name: String = name,
      protocol: String = protocol,
      host: String = host,
      port: Int = port,
      path: String = path,
      pactSource: PactSource = pactSource,
      stateManagement: Option[StateManagement] = stateManagement,
      verificationSettings: Option[VerificationSettings] = verificationSettings,
      requestFilter: ProviderRequest => Option[ProviderRequestFilter] = requestFilter
  ) = new ProviderInfoBuilder(
    name,
    protocol,
    host,
    port,
    path,
    pactSource,
    stateManagement,
    verificationSettings,
    requestFilter
  )

  private[pact4s] def getStateManagement: Option[StateManagement] = stateManagement

  def withProtocol(protocol: String): ProviderInfoBuilder = copy(protocol = protocol)
  def withHost(host: String): ProviderInfoBuilder         = copy(host = host)
  def withPort(port: Int): ProviderInfoBuilder            = copy(port = port)
  def withPath(path: String): ProviderInfoBuilder         = copy(path = path)
  def withVerificationSettings(settings: VerificationSettings): ProviderInfoBuilder =
    copy(verificationSettings = Some(settings))
  def withOptionalVerificationSettings(settings: Option[VerificationSettings]): ProviderInfoBuilder =
    copy(verificationSettings = settings)

  def withStateChangeUrl(url: String): ProviderInfoBuilder =
    copy(stateManagement = Some(StateManagement.ProviderUrl(url)))
  def withStateChangeEndpoint(endpoint: String): ProviderInfoBuilder = {
    val endpointWithLeadingSlash: String = if (!endpoint.startsWith("/")) "/" + endpoint else endpoint
    withStateChangeUrl(s"$protocol://$host:$port$endpointWithLeadingSlash")
  }

  def withStateChangeFunctionAndResponse(
      stateChange: PartialFunction[ProviderState, Map[String, String]]
  ): ProviderInfoBuilder =
    withStateManagementFunction(StateManagementFunction.withResponse(stateChange))

  def withStateChangeFunction(stateChange: PartialFunction[ProviderState, Unit]): ProviderInfoBuilder =
    withStateManagementFunction(StateManagementFunction(stateChange))
  def withStateChangeFunction(stateChange: ProviderState => Unit): ProviderInfoBuilder =
    withStateChangeFunction({ case x => stateChange(x) }: PartialFunction[ProviderState, Unit])

  def withStateManagementFunction(stateManagementFunction: StateManagementFunction): ProviderInfoBuilder =
    copy(stateManagement = Some(stateManagementFunction))

  def withStateChangeFunctionConfigOverrides(
      overrides: StateManagement.StateManagementFunction => StateManagement.StateManagementFunction
  ): ProviderInfoBuilder = {
    val withOverrides: Option[StateManagement] = stateManagement.map {
      case x: StateManagement.ProviderUrl             => x
      case x: StateManagement.StateManagementFunction => overrides(x)
    }
    copy(stateManagement = withOverrides)
  }

  @deprecated("use withRequestFiltering instead, where request filters are composed with .andThen", "0.0.19")
  def withRequestFilter(requestFilter: ProviderRequest => List[ProviderRequestFilter]): ProviderInfoBuilder =
    copy(requestFilter = request => requestFilter(request).reduceLeftOption(_ andThen _))

  def withRequestFiltering(requestFilter: ProviderRequest => ProviderRequestFilter): ProviderInfoBuilder =
    copy(requestFilter = request => Some(requestFilter(request)))

  private def pactJvmRequestFilter: HttpRequest => Unit = { request =>
    val providerRequest = ProviderRequest(
      request.getMethod,
      request.getUri,
      request.getHeaders.toList.map(h => (h.getName, h.getValue))
    )
    requestFilter(providerRequest).foreach(_.filterImpl(request))
  }

  private[pact4s] def build(
      providerBranch: Option[Branch],
      responseFactory: Option[String => ResponseBuilder],
      stateChangeUrl: Option[String]
  ): Either[Throwable, ProviderInfo] = {
    val p = new ProviderInfo(name, protocol, host, port, path)
    responseFactory.foreach(_ => p.setVerificationType(PactVerification.RESPONSE_FACTORY))
    verificationSettings.foreach { case AnnotatedMethodVerificationSettings(packagesToScan) =>
      p.setVerificationType(PactVerification.ANNOTATED_METHOD)
      p.setPackagesToScan(packagesToScan.asJava)
    }
    stateChangeUrl.foreach(s => p.setStateChangeUrl(new URI(s).toURL))
    p.setRequestFilter {
      // because java
      new Consumer[HttpRequest] {
        def accept(t: HttpRequest): Unit =
          pactJvmRequestFilter(t)
      }
    }
    pactSource match {
      case broker: PactBroker => applyBrokerSourceToProvider(p, broker, providerBranch)
      case f: FileSource =>
        f.consumers.foreach { case (consumer, file) =>
          p.hasPactWith(
            consumer,
            { consumer =>
              consumer.setPactSource(new PactJVMFileSource(file))
              kotlin.Unit.INSTANCE
            }
          )
        }
        Right(p)
    }
  }

  @tailrec
  private def applyBrokerSourceToProvider(
      providerInfo: ProviderInfo,
      pactSource: PactBroker,
      providerBranch: Option[Branch]
  ): Either[Throwable, ProviderInfo] =
    pactSource match {
      case p: PactBrokerWithSelectors =>
        p.validate(providerBranch) match {
          case Left(value) => Left(value)
          case Right(_) =>
            val pactJvmAuth: Auth = p.auth match {
              case None                        => Auth.None.INSTANCE
              case Some(TokenAuth(token))      => new Auth.BearerAuthentication(token, Auth.DEFAULT_AUTH_HEADER)
              case Some(BasicAuth(user, pass)) => new Auth.BasicAuthentication(user, pass)
            }
            @nowarn("cat=deprecation")
            val brokerOptions: PactBrokerOptions = new PactBrokerOptions(
              p.enablePending,
              p.providerTags.map(_.toList).getOrElse(Nil).asJava,
              providerBranch.map(_.branch).orNull,
              p.includeWipPactsSince.since.map(instantToDateString).orNull,
              p.insecureTLS,
              pactJvmAuth
            )
            val applySelectors: Try[Unit] =
              Try {
                providerInfo.hasPactsFromPactBrokerWithSelectorsV2(
                  p.brokerUrl,
                  p.consumerVersionSelectors.asJava,
                  brokerOptions
                )
                ()
              }
            applySelectors.toEither.map(_ => providerInfo)
        }
      case p: PactBrokerWithTags =>
        applyBrokerSourceToProvider(
          providerInfo,
          p.toPactBrokerWithSelectors,
          providerBranch
        )
    }

  private def instantToDateString(instant: Instant): String =
    instant
      .atOffset(
        ZoneOffset.UTC // Apologies for the euro-centrism, but as we use time relative to the epoch it doesn't really matter
      )
      .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

object ProviderInfoBuilder {
  def apply(
      name: String,
      protocol: String,
      host: String,
      port: Int,
      path: String,
      pactSource: PactSource
  ): ProviderInfoBuilder = new ProviderInfoBuilder(
    name,
    protocol,
    host,
    port,
    path,
    pactSource,
    stateManagement = None,
    verificationSettings = None,
    requestFilter = _ => None
  )

  /** Create a ProviderInfoBuilder by providing a [[java.net.URL]] rather than specifying the URL components separately
    */
  def apply(name: String, providerUrl: URL, pactSource: PactSource): ProviderInfoBuilder =
    new ProviderInfoBuilder(
      name,
      providerUrl.getProtocol,
      providerUrl.getHost,
      providerUrl.getPort,
      providerUrl.getPath,
      pactSource,
      stateManagement = None,
      verificationSettings = None,
      requestFilter = _ => None
    )

  /** Auxiliary constructor that provides some common defaults for the mock provider address
    *
    * Example usage:
    * {{{
    *   ProviderInfoBuilder(
    *       name = "Provider Service",
    *       pactSource = FileSource("Consumer Service" -> new File("./pacts/pact.json"))
    *     ).withPort(80)
    *     .withStateChangeEndpoint("setup")
    * }}}
    */
  def apply(name: String, pactSource: PactSource): ProviderInfoBuilder =
    new ProviderInfoBuilder(
      name,
      "http",
      "localhost",
      0,
      "/",
      pactSource,
      stateManagement = None,
      verificationSettings = None,
      requestFilter = _ => None
    )
}
