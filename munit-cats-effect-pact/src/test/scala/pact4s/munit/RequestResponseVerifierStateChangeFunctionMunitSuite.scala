package pact4s.munit

import munit.CatsEffectSuite
import pact4s.MockProviderServer
import pact4s.provider.{ProviderInfoBuilder, ProviderState}

class RequestResponseVerifierStateChangeFunctionMunitSuite extends CatsEffectSuite with PactVerifier {
  val mock = new MockProviderServer(49155)

  override val staticStateChangePort: Int = 64643

  override val provider: ProviderInfoBuilder = mock
    .fileSourceProviderInfo(
      consumerName = "Pact4sConsumer",
      providerName = "Pact4sProvider",
      fileName = "./scripts/Pact4sConsumer-Pact4sProvider.json"
    )
    .withStateChangeFunction { case ProviderState("bob exists") =>
      mock.stateRef.set(Some("bob")).unsafeRunSync()
    }

  override val munitFixtures: Seq[Fixture[_]] = Seq(
    ResourceSuiteLocalFixture(
      "Mock Provider Server",
      mock.server
    )
  )

  test("Verify pacts for provider `Pact4sProvider`") {
    verifyPacts()
  }
}
