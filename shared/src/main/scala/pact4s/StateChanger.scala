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

package pact4s

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import pact4s.Pact4sLogger.pact4sLogger
import pact4s.provider.ProviderState
import ujson.Value
import upickle.core.LinkedHashMap
import upickle.default._

import java.net.InetSocketAddress
import scala.util.{Failure, Success, Try}

private[pact4s] sealed trait StateChanger {
  def start(): Unit
  def shutdown(): Unit
}

private[pact4s] object StateChanger {
  class SimpleServer(
      stateChange: PartialFunction[ProviderState, Map[String, String]],
      stateChangeBeforeHook: () => Unit,
      host: String,
      port: Int,
      endpoint: String
  ) extends StateChanger {
    private var isShutdown: Boolean = true
    private var _server: HttpServer = _
    var boundPort: Int              = _

    def start(): Unit = {
      val server = HttpServer.create(new InetSocketAddress(host, port), 0)
      _server = server
      val slashedEndpoint = if (endpoint.startsWith("/")) endpoint else "/" + endpoint
      server.createContext(slashedEndpoint, RootHandler)
      server.setExecutor(null)
      isShutdown = false
      server.start()
      boundPort = server.getAddress.getPort
      println(s"State change server bound to address $host:$boundPort:$slashedEndpoint")
      pact4sLogger.info(s"State change server bound to address $host:$boundPort:$slashedEndpoint")
    }

    def shutdown(): Unit =
      if (!isShutdown) {
        _server.stop(0)
        isShutdown = true
        pact4sLogger.info(s"Shutting down state change server")
      }

    private object RootHandler extends HttpHandler {

      def handle(t: HttpExchange): Unit = {
        val stateAndParams: Try[(String, Option[LinkedHashMap[String, Value]])] =
          Try {
            val obj: LinkedHashMap[String, Value]                 = read[ujson.Obj](t.getRequestBody).obj
            val state                                             = obj.get("state").map(_.str).getOrElse("")
            val maybeParams: Option[LinkedHashMap[String, Value]] = obj.get("params").flatMap(_.objOpt)

            (state, maybeParams)
          }

        val stateChangeMaybeApplied: Try[Map[String, Value]] =
          stateAndParams.flatMap { case (s, maybeParams) =>
            Try {
              // Apply before hook
              stateChangeBeforeHook.apply()
              val params: Map[String, String] =
                maybeParams
                  .map {
                    _.map { case (key, value) =>
                      key -> value.strOpt.getOrElse(value.toString())
                    }.toMap
                  }
                  .getOrElse(Map.empty[String, String])
              // Apply state change function
              val result =
                stateChange
                  .lift(ProviderState(s, params))
                  .getOrElse {
                    pact4sLogger.warn(
                      s"No state change definition was provided for received state $s with parameters $params"
                    )
                    Map.empty
                  }
              val results: Map[String, Value] = result.map { case (k, v) => (k, ujson.Str(v)) }
              maybeParams.fold(results)(_.toMap ++ results)
            }
          }
        stateChangeMaybeApplied match {
          case Failure(exception) =>
            pact4sLogger.error(exception)("State change application failed.")
            sendResponse(t, "{}", 400)
          case Success(result) =>
            val body = if (result.nonEmpty) write(ujson.Obj.from(result)) else "{}"
            sendResponse(t, body, 200)
        }
      }

      private def sendResponse(t: HttpExchange, body: String, code: Int): Unit = {
        val response = body
        t.getResponseHeaders.set("Content-Type", "application/json")
        t.sendResponseHeaders(code, response.length().toLong)
        val os = t.getResponseBody
        os.write(response.getBytes)
        os.close()
      }
    }
  }
}
