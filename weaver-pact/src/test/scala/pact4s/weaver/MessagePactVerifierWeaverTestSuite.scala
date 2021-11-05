package pact4s.weaver

import pact4s.MockProviderServer
import pact4s.messages.MessagesProvider
import pact4s.provider.ProviderInfoBuilder
import weaver.SimpleIOSuite

object MessagePactVerifierWeaverTestSuite extends SimpleIOSuite with MessagePactVerifier {
  val mock = new MockProviderServer(isRequestResponse = false)

  def messages: ResponseFactory = MessagesProvider.messages
  override val provider: ProviderInfoBuilder = mock.fileSourceProviderInfo(
    consumerName = "Pact4sMessageConsumer",
    providerName = "Pact4sMessageProvider",
    fileName = "./scripts/Pact4sMessageConsumer-Pact4sMessageProvider.json"
  )

  pureTest("Verify pacts for provider `MessageProvider`") {
    succeed(verifyPacts())
  }
}
