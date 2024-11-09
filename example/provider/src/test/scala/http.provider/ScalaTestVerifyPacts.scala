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

import cats.effect
import cats.effect.IO
import cats.effect.unsafe.implicits._
import cats.implicits._
import com.comcast.ip4s.{Host, Port}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Authorization
import org.http4s.server.Server
import org.http4s.{BasicCredentials, HttpRoutes}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import pact4s.provider.ProviderRequestFilter.{NoOpFilter, SetHeaders}
import pact4s.provider.StateManagement.StateManagementFunction
import pact4s.provider._
import pact4s.scalatest.PactVerifier

import java.io.File
import scala.concurrent.duration.DurationInt

class ScalaTestVerifyPacts extends AnyFlatSpec with BeforeAndAfterAll with PactVerifier {

  val store: MockResourceStore[IO] = MockResourceStore.unsafe[IO]()

  val apiKey: String = "1dcbkjabyge1g273ie1u2"

  val fetchRoute: HttpRoutes[IO] = FetchRoute[IO](store.fetch, apiKey)

  val createRoute: HttpRoutes[IO] = CreateRoute[IO](store.create, apiKey)

  val server: effect.Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("localhost").get)
      .withPort(Port.fromInt(1234).get)
      .withHttpApp((fetchRoute <+> createRoute).orNotFound)
      .withShutdownTimeout(0.seconds)
      .build

  var cleanUp: IO[Unit] = IO.unit

  override def beforeAll(): Unit = {
    val (_, shutdown) = server.allocated.unsafeRunSync()
    cleanUp = shutdown
    // Insert deliberately data that the provider state before hook should clean so that tests succeed
    store.create(ProviderResource("missingID", 99)).unsafeRunSync()
    store.create(ProviderResource("newID", 66)).unsafeRunSync()
    ()
  }

  override def afterAll(): Unit =
    cleanUp.unsafeRunSync()

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
    "scalatest-provider",
    PactSource.FileSource(
      Map("scalatest-consumer" -> new File("./example/resources/pacts/scalatest-consumer-scalatest-provider.json"))
    )
  ).withHost("localhost")
    .withPort(1234)
    .withStateManagementFunction(
      StateManagementFunction {
        case ProviderState("resource exists", params) =>
          val id    = params.get("id")
          val value = params.get("value").map(_.toInt)
          (id, value).mapN(ProviderResource.apply).traverse_(store.create).unsafeRunSync()
        case ProviderState("resource does not exist", _) => () // Nothing to do
        case _                                           => ()
      }
        .withBeforeEach(() => store.empty.unsafeRunSync())
    )
    .withRequestFiltering(requestFilter)

  it should "Verify pacts" in {
    verifyPacts(
      publishVerificationResults = None,
      providerVerificationOptions = Nil,
      verificationTimeout = Some(10.seconds)
    )
  }
}
