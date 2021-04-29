/*
 * Copyright 2021-2021 io.github.jbwheatley
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

package pact4s.munit

import au.com.dius.pact.consumer.BaseMockServer
import cats.effect.{IO, Resource}
import munit.internal.PlatformCompat
import munit.{CatsEffectSuite, Location, TestOptions}
import pact4s.PactForgerResources

import scala.concurrent.Future
import scala.util.control.NonFatal

trait PactForger extends CatsEffectSuite with PactForgerResources {

  @volatile private var testFailed: Boolean = false

  override def munitFixtures: Seq[Fixture[_]] = serverFixture +: additionalMunitFixtures

  def additionalMunitFixtures: Seq[Fixture[_]] = Seq.empty

  private val serverFixture: Fixture[BaseMockServer] = ResourceSuiteLocalFixture(
    "mockHttpServer",
    serverResource
  )

  private def serverResource: Resource[IO, BaseMockServer] =
    Resource.make[IO, BaseMockServer] {
      for {
        _ <- validatePactVersion.fold(IO.unit)(IO.raiseError)
        _ <- IO.delay(server.start())
        _ <- IO.delay(server.waitForServer())
      } yield server
    } { s =>
      if (testFailed) {
        logger.info(
          s"Not writing pacts for consumer ${pact.getConsumer} and provider ${pact.getProvider} to file because tests failed."
        )
        IO.unit
      } else {
        logger.info(
          s"Writing pacts for consumer ${pact.getConsumer} and provider ${pact.getProvider} to ${pactTestExecutionContext.getPactFolder}"
        )
        IO.delay(s.verifyResultAndWritePact(null, pactTestExecutionContext, pact, mockProviderConfig.getPactVersion))
      } >>
        IO.delay(s.stop())
    }

  def pactTest(name: String)(test: BaseMockServer => Any): Unit = this.test(name)(test(serverFixture.apply()))

  override def test(options: TestOptions)(body: => Any)(implicit loc: Location): Unit =
    munitTestsBuffer += munitTestTransform(
      new Test(
        options.name, { () =>
          try {
            PlatformCompat.waitAtMost(munitValueTransform(body), munitTimeout)
          } catch {
            case NonFatal(e) =>
              testFailed = true
              Future.failed(e)
          }
        },
        options.tags,
        loc
      )
    )
}
