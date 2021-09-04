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

package pact4s

import au.com.dius.pact.consumer.PactTestExecutionContext
import au.com.dius.pact.core.model.PactSpecVersion

trait BasePactForgerResources extends Pact4sLogger {
  val pactTestExecutionContext: PactTestExecutionContext = new PactTestExecutionContext()

  private[pact4s] def validatePactVersion(version: PactSpecVersion): Either[Throwable, Unit]

  private[pact4s] type Effect[_]

  /** This effect runs after the consumer pact tests are run, but before they get written to a file. An example use is
    * to check that all the defined pacts have a corresponding test.
    */
  def beforeWritePacts(): Effect[Unit]
}
