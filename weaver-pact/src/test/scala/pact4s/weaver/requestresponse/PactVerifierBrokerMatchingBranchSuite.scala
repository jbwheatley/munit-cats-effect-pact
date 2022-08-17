package pact4s.weaver.requestresponse

import cats.effect.{IO, Resource}
import org.http4s.server.Server
import pact4s.MockProviderServer
import pact4s.provider.{Branch, ConsumerVersionSelectors, ProviderInfoBuilder}
import pact4s.weaver.PactVerifier
import weaver.IOSuite

object PactVerifierBrokerMatchingBranchSuite extends IOSuite with PactVerifier {
  type Res = Server

  val mock = new MockProviderServer(49274, hasFeatureX = true)

  override def sharedResource: Resource[IO, Server] = mock.server

  override val provider: ProviderInfoBuilder =
    mock.brokerProviderInfo(
      "Pact4sProvider",
      consumerVersionSelector = ConsumerVersionSelectors.matchingBranch
    )

  test("Verify pacts for provider `Pact4sProvider` with a feature branch and matching branch selector, weaver") {
    for {
      a <- IO(
        succeed(
          verifyPacts(
            Some(
              Branch("feat/x")
            )
          )
        )
      )
      x <- mock.featureXState.get
    } yield a && assert(x)
  }
}
