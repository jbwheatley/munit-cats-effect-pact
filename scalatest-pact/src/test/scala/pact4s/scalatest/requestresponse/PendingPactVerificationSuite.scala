package pact4s.scalatest.requestresponse

import org.scalatest.flatspec.AnyFlatSpec
import pact4s.PendingPactVerificationFixture
import pact4s.scalatest.PactVerifier

class PendingPactVerificationSuite extends AnyFlatSpec with PactVerifier with PendingPactVerificationFixture {
  "pending pact failure" should "be skipped" in verifyPacts()
}
