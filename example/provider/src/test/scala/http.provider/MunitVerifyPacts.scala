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

package http.provider

import cats.effect.IO
import cats.implicits._
import com.comcast.ip4s.{Host, Port}
import munit.{AnyFixture, CatsEffectSuite}
import munit.catseffect.IOFixture
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Authorization
import org.http4s.server.Server
import org.http4s.{BasicCredentials, HttpRoutes}
import pact4s.munit.PactVerifier
import pact4s.provider.ProviderRequestFilter.{NoOpFilter, SetHeaders}
import pact4s.provider.StateManagement.StateManagementFunction
import pact4s.provider._

import java.io.File
import scala.concurrent.duration.{Duration, DurationInt}

class MunitVerifyPacts extends CatsEffectSuite with PactVerifier {

  override def munitIOTimeout: Duration = 1.minute

  // Insert deliberately data that the provider state before hook should clean so that tests succeed
  val store: MockResourceStore[IO] = MockResourceStore.unsafe[IO](Map("missingId" -> 99, "newId" -> 66))

  val apiKey: String = "1dcbkjabyge1g273ie1u2"

  val fetchRoute: HttpRoutes[IO] = FetchRoute[IO](store.fetch, apiKey)

  val createRoute: HttpRoutes[IO] = CreateRoute[IO](store.create, apiKey)

  val server: IOFixture[Server] = ResourceSuiteLocalFixture(
    "provider-server",
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("localhost").get)
      .withPort(Port.fromInt(1235).get)
      .withHttpApp((fetchRoute <+> createRoute).orNotFound)
      .withShutdownTimeout(0.seconds)
      .build
  )

  override def munitFixtures: Seq[AnyFixture[_]] = Seq(server)

  // If the auth header in the request is "correct", we can replace it with an auth header that will actually work with our API,
  // else we leave it as is to be rejected.
  def requestFilter: ProviderRequest => ProviderRequestFilter = req =>
    req.getFirstHeader("Authorization") match {
      case Some((_, value)) =>
        Authorization
          .parse(value)
          .map {
            case Authorization(BasicCredentials("user", "pass")) =>
              SetHeaders("Authorization" -> s"Basic ${BasicCredentials("user", apiKey).token}")
            case _ => NoOpFilter
          }
          .getOrElse(NoOpFilter)
      case None => NoOpFilter
    }

  val provider: ProviderInfoBuilder = ProviderInfoBuilder(
    "munit-provider",
    PactSource.FileSource(
      Map("munit-consumer" -> new File("./example/resources/pacts/munit-consumer-munit-provider.json"))
    )
  ).withHost("localhost")
    .withPort(1235)
    .withStateManagementFunction(
      StateManagementFunction {
        case ProviderState("resource exists", params) =>
          val id    = params.get("id")
          val value = params.get("value").map(_.toInt)
          (id, value).mapN(ProviderResource.apply).traverse_(store.create).unsafeRunSync()
        case ProviderState("resource does not exist", _) => () // Nothing to do
        case _: ProviderState                            => ()
      }
        .withBeforeEach(() => store.empty.unsafeRunSync())
    )
    .withRequestFiltering(requestFilter)

  test("Verify pacts") {
    verifyPacts(
      publishVerificationResults = None,
      providerVerificationOptions = Nil,
      verificationTimeout = Some(10.seconds)
    )
  }
}
