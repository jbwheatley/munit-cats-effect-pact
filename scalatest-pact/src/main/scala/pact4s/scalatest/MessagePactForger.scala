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

package pact4s.scalatest

import au.com.dius.pact.core.model.messaging.Message
import org.scalatest._
import pact4s.MessagePactForgerResources
import pact4s.Pact4sLogger.{notWritingPactMessage, pact4sLogger}

import scala.jdk.CollectionConverters._

trait MessagePactForger extends MessagePactForgerResources with SuiteMixin { self: Suite =>

  def messages: List[Message] = pact.getMessages.asScala.toList

  private var testFailed = false

  abstract override def run(testName: Option[String], args: Args): Status =
    if (expectedTestCount(args.filter) == 0) {
      new CompositeStatus(Set.empty)
    } else {
      validatePactVersion(pactSpecVersion).left.foreach(throw _)
      try {
        val result = super.run(testName, args)
        if (!result.succeeds())
          testFailed = true
        result
      } finally
        if (testFailed) {
          pact4sLogger.error(
            notWritingPactMessage(pact)
          )
        } else {
          beforeWritePacts().flatMap { _ =>
            writeMessagePactToFile()
          } match {
            case Left(e)  => throw e
            case Right(_) => ()
          }
        }
    }

  override private[pact4s] type Effect[_] = Either[Throwable, _]

  def beforeWritePacts(): Either[Throwable, Unit] = Right(())
}
