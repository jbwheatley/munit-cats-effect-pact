package pact4s

import cats.data.Kleisli
import cats.effect.kernel.{Deferred, Ref}
import cats.effect.{IO, Resource}
import com.comcast.ip4s.{Host, Port}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, HCursor, Json, JsonObject}
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.jsonOf
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Server
import pact4s.provider.Authentication.BasicAuth
import pact4s.provider.PactSource.{FileSource, PactBrokerWithSelectors}
import pact4s.provider._
import sourcecode.{File => SCFile}

import java.io.File
import java.net.URL
import scala.concurrent.duration.DurationInt

class MockProviderServer(port: Int, hasFeatureX: Boolean = false)(implicit file: SCFile) {

  val featureXState: Deferred[IO, Boolean] = Deferred.unsafe

  def server: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("localhost").get)
      .withPort(Port.fromInt(port).get)
      .withHttpApp(middleware(app))
      .withShutdownTimeout(1.seconds)
      .build

  private implicit val entityDecoder: EntityDecoder[IO, ProviderState] = {
    // Note: this is a simplified version of the decoder actually provided in circe module because the tests below do not use parameters other than string
    implicit val decoder: Decoder[ProviderState] = (c: HCursor) =>
      for {
        state  <- c.get[String]("state")
        params <- c.get[Option[JsonObject]]("params")
        stringParams = params
          .map(
            _.toMap
              .map { case (k, v) => k -> v.asString }
              .collect { case (k, Some(v)) => k -> v }
          )
          .getOrElse(Map.empty)
      } yield ProviderState(state, stringParams)

    jsonOf
  }

  private[pact4s] val stateRef: Ref[IO, Option[String]] = Ref.unsafe(None)

  private def middleware: HttpApp[IO] => HttpApp[IO] = { app =>
    Kleisli { (req: Request[IO]) =>
      app(req).timed.flatMap { case (time, resp) =>
        IO.println(
          Console.BLUE +
            s"[PACT4S TEST INFO] Request to mock provider server with port $port in test suite ${file.value.split("/").takeRight(3).mkString("/")}" +
            s"\n[PACT4S TEST INFO] ${req.toString()}\n[PACT4S TEST INFO] ${resp
                .toString()}\n[PACT4S TEST INFO] Duration: ${time.toMillis} millis" +
            Console.WHITE
        ).as(resp)
      }
    }
  }

  private def app: HttpApp[IO] =
    HttpRoutes
      .of[IO] {
        case GET -> Root / "goodbye" =>
          NoContent()
        case req @ POST -> Root / "hello" =>
          req.as[Name].flatMap {
            case Name("harry") => Ok(Json.obj("hello" -> "harry".asJson))
            case _             => NotFound()
          }
        case GET -> Root / "anyone-there" =>
          stateRef.get.flatMap {
            case Some(s) => Ok(Json.obj("found" -> s.asJson))
            case None    => NotFound()
          }
        case req @ GET -> Root / "authorized" =>
          req.headers
            .get[headers.Authorization]
            .map(_.credentials)
            .flatMap {
              case Credentials.Token(AuthScheme.Bearer, token) => Some(token)
              case _                                           => None
            }
            .map { token =>
              if (token == "token") Ok()
              else Forbidden()
            }
            .getOrElse(Unauthorized(`WWW-Authenticate`(Challenge(AuthScheme.Bearer.toString, "Authorized endpoints."))))
        case req @ POST -> Root / "setup" =>
          req.as[ProviderState].flatMap {
            case ProviderState("bob exists", _) =>
              stateRef.set(Some("bob")) *> Ok()
            case _ => Ok()
          }
        case GET -> Root / "feature-x" if hasFeatureX =>
          featureXState.complete(true) *>
            NoContent()
      }
      .orNotFound

  private def requestFilter(request: ProviderRequest): ProviderRequestFilter =
    request.uri.getPath match {
      case s if s.matches(".*/authorized") => ProviderRequestFilter.SetHeaders(("Authorization", "Bearer token"))
      case _                               => ProviderRequestFilter.NoOpFilter
    }

  def fileSourceProviderInfo(
      consumerName: String,
      providerName: String,
      fileName: String,
      verificationSettings: Option[VerificationSettings] = None
  ): ProviderInfoBuilder =
    ProviderInfoBuilder(
      name = providerName,
      providerUrl = new URL("http://localhost:0/"),
      pactSource = FileSource(Map(consumerName -> new File(fileName)))
    ).withPort(port)
      .withOptionalVerificationSettings(verificationSettings)
      .withStateChangeEndpoint("setup")
      .withRequestFiltering(requestFilter)

  def brokerProviderInfo(
      providerName: String,
      verificationSettings: Option[VerificationSettings] = None,
      consumerVersionSelector: ConsumerVersionSelectors = ConsumerVersionSelectors().latestTag("pact4s-test")
  ): ProviderInfoBuilder =
    ProviderInfoBuilder(
      name = providerName,
      pactSource = PactBrokerWithSelectors(
        brokerUrl = "https://test.pactflow.io"
      ).withPendingPactsEnabled(ProviderTags("SNAPSHOT"))
        .withAuth(BasicAuth("dXfltyFMgNOFZAxr8io9wJ37iUpY42M", "O5AIZWxelWbLvqMd8PkAVycBJh2Psyg1"))
        .withConsumerVersionSelectors(consumerVersionSelector)
    ).withPort(port)
      .withOptionalVerificationSettings(verificationSettings)
      .withStateChangeEndpoint("setup")
      .withRequestFiltering(requestFilter)
}

private[pact4s] final case class Name(name: String)

private[pact4s] object Name {
  implicit val decoder: Decoder[Name]                 = Decoder.forProduct1("name")(Name(_))
  implicit val entityDecoder: EntityDecoder[IO, Name] = jsonOf
}
