package pact4s.weaver

import cats.effect.IO
import pact4s.{MockProviderServer, ProviderInfoBuilder}
import pact4s.VerificationSettings.AnnotatedMethodVerificationSettings
import weaver.SimpleIOSuite

object MessagePactVerifierWeaverTestSuite extends SimpleIOSuite with PactVerifier {
  val mock = new MockProviderServer(1237)

  override val provider: ProviderInfoBuilder = mock.fileSourceProviderInfo(
    consumerName = "Pact4sMessageConsumer",
    providerName = "Pact4sMessageProvider",
    fileName = "./scripts/Pact4sMessageConsumer-Pact4sMessageProvider.json",
    verificationSettings = Some(AnnotatedMethodVerificationSettings(packagesToScan = List("pact4s.messages")))
  )

  test("Verify pacts for provider `MessageProvider`") {
    IO(verifyPacts()).map(succeed)
  }
}
