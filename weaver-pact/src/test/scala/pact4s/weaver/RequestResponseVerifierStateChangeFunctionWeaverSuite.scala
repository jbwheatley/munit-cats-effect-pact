package pact4s.weaver

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import org.http4s.server.Server
import pact4s.MockProviderServer
import pact4s.provider.{ProviderInfoBuilder, ProviderState}
import weaver.IOSuite

object RequestResponseVerifierStateChangeFunctionWeaverSuite extends IOSuite with PactVerifierWithResources[IO] {
  override type Resources = Server

  val mock = new MockProviderServer(49164)

  override val staticStateChangePort: Int = 64645

  override def additionalSharedResource: Resource[IO, Server] = mock.server

  override val provider: ProviderInfoBuilder = mock
    .fileSourceProviderInfo(
      consumerName = "Pact4sConsumer",
      providerName = "Pact4sProvider",
      fileName = "./scripts/Pact4sConsumer-Pact4sProvider.json"
    )
    .withStateChangeFunction { case ProviderState("bob exists") =>
      mock.stateRef.set(Some("bob")).unsafeRunSync()
    }

  pureTest("Verify pacts for provider `Pact4sProvider`") {
    succeed(verifyPacts())
  }
}
