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

package pact4s.weaver.requestresponse

import cats.effect.{IO, Resource}
import org.http4s.server.Server
import pact4s.MockProviderServer
import pact4s.provider.ProviderInfoBuilder
import pact4s.weaver.PactVerifier
import weaver.IOSuite

object PactVerifierStateChangeFunctionSuite extends IOSuite with PactVerifier[IO] {
  override type Res = Server

  val mock = new MockProviderServer(49170)

  override def sharedResource: Resource[IO, Server] = mock.server

  override val provider: ProviderInfoBuilder = mock
    .fileSourceProviderInfo(
      consumerName = "Pact4sConsumer",
      providerName = "Pact4sProvider",
      fileName = "./scripts/Pact4sConsumer-Pact4sProvider.json",
      useStateChangeFunction = true,
      stateChangePortOverride = Some(64640)
    )

  test("Verify pacts for provider `Pact4sProvider`") {
    verifyPacts().map(succeed)
  }
}
