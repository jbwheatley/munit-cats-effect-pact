# pact4s

![Tag](https://img.shields.io/github/v/tag/jbwheatley/pact4s?sort=semver)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Mostly dependency-free wrapper of [pact-jvm](https://github.com/pact-foundation/pact-jvm) for commonly used scala testing frameworks. To find out more about consumer-driven contract testing, visit the Pact Foundation website [here](https://docs.pact.io/)! Supported scala versions are 2.12, 2.13 and 3. 

Please consider helping us out by contributing or raising issues :)

- [pact4s](#pact4s)
  * [Getting Started](#getting-started)
    + [Java 8 Support](#java-8-support-deprecated)
  * [Running the examples](#running-the-examples)
  * [Writing Pacts](#writing-pacts)
    + [Pact Builder DSL](#pact-builder-dsl)
      - [Using Pact Matching DSL](#using-pact-matching-dsl)
      - [Using JSON bodies](#using-json-bodies)
    + [Request/Response Pacts](#requestresponse-pacts)
      - [Choosing an address and/or port](#choosing-an-address-andor-port)
      - ['Inline' Style of Processing Request/Response Pacts](#inline-style-of-processing-requestresponse-pacts)
    + [Message Pacts](#message-pacts)
    + [Mixed Pacts](#mixed-pacts)
  * [Publishing Pacts](#publishing-pacts)
  * [Verifying Pacts](#verifying-pacts)
    + [Pact Broker](#pact-broker)
    + [Request/Response Pact Verification](#requestresponse-pact-verification)
      - [Request Filtering](#request-filtering)
    + [Message Pact Verification](#message-pact-verification)
    + [Provider state](#provider-state)
      - [Exposing your own state-change endpoint](#exposing-your-own-state-change-endpoint)
      - [Using a state-change function](#using-a-state-change-function)
  * [Logging](#logging)
  * [Contributing](#contributing)
  * [Adopters](#adopters)

---

## Getting Started

`pact4s` is available through maven-central. 

This library provides support for `munit-cats-effect`, `weaver`, `scalatest` and `zio-test`, to write and verify both request/response and message pacts.

```scala
"io.github.jbwheatley" %% "pact4s-munit-cats-effect" % xxx
"io.github.jbwheatley" %% "pact4s-weaver"            % xxx
"io.github.jbwheatley" %% "pact4s-scalatest"         % xxx
"io.github.jbwheatley" %% "pact4s-zio-test"          % xxx
```

We also offer some additional helpers for using JSON encoders directly in your pact definitions. Currently, support is offered for `circe` and `play-json` in the modules `pact4s-circe` and `pact4s-play-json`, respectively. If you would like to see support for your favourite scala JSON library, consider submitting a PR!

### Compatibility matrix

| Pact4s version  | Pact JVM | Pact Spec | JDK  | Scala           |
|-----------------|----------|-----------|------|-----------------|
| 0.10.x - 0.14.x | 4.6      | V4        | 17+  | 2.12, 2.13, 3.3 |
| 0.9.x           | 4.5      | V4        | 11+  | 2.12, 2.13, 3.2 |
| 0.8.x           | 4.4      | V4        | 11+  | 2.12, 2.13, 3.2 |
| 0.7.x           | 4.4      | V4        | 11+  | 2.12, 2.13, 3.2 |
| 0.6.x           | 4.3      | V4        | 11+  | 2.12, 2.13, 3.2 |
| 0.4.x - 0.5.x   | 4.3      | V4        | 11+  | 2.12, 2.13, 3.1 |
| 0.1.x - 0.3.x   | 4.3      | V4        | 11+  | 2.12, 2.13      |

See also [Pact JVM Compatibility Matrix](https://github.com/pact-foundation/pact-jvm/blob/master/README.md).

#### Java 8 support (Deprecated)

We recommend using recent Java versions for your build.
But if you can't for some reason, and as Pact JVM still supports Java 8 on their 4.1.x versions, pact4s provides
`-java8` suffixed versions:

| Pact4s version              | Pact JVM | Pact Spec | JDK  | Scala           |
|-----------------------------|----------|-----------|------|-----------------|
| 0.10.x-java8 - 0.11.x-java8 | 4.1      | V3        | 8-12 | 2.12, 2.13, 3.3 |
| 0.6.x-java8 - 0.9.x-java8   | 4.1      | V3        | 8-12 | 2.12, 2.13, 3.2 |
| 0.4.x-java8 - 0.5.x-java8   | 4.1      | V3        | 8-12 | 2.12, 2.13, 3.1 |
| 0.1.x-java8 - 0.3.x-java8   | 4.1      | V3        | 8-12 | 2.12, 2.13      |


## Running the examples

In the example directory there are two modules, one for the `consumer`, and one for the `provider`. The consumer has tests to generate pacts using both the `munit` and `scalatest` pact forging interfaces. These should be ran first, as they
publish the pacts to files in `./example/resources/pacts` which the provider tests require to run. The provider has tests that verify the consumer generated pacts using the `munit` and `scalatest` pact verification interfaces. To run the tests from sbt go to `project exampleConsumer` or `project exampleProvider`. 

## Writing Pacts

The modules `pact4s-munit-cats-effect`, `pact4s-weaver`, `pact4s-scalatest` and `pact4s-zio-test` mixins all share common interfaces for defining pacts. The APIs for each of these modules is slightly different to account for the differences between the APIs of the testing frameworks. We recommend looking at the tests in this project for examples of each, or the examples module.

### Pact Builder DSL

Pacts are constructed using the pact-jvm DSL, but with additional helpers for easier interoperability with scala. For example, anywhere a java `Map` is expected, a scala `Map`, or scala tuples can be provided instead.

#### Using Pact matching DSL

Using `PactDslJsonBody` or `PactDslJsonArray` can be painful.
This is why Pact JVM exposes a `LambdaDsl` which aims to be easier to use and read (Read ["Why a new DSL implementation?"](https://docs.pact.io/implementation_guides/jvm/consumer#why-a-new-dsl-implementation)).

But still, in Scala it can be very verbose to use it.
Pact4s provides a `ScalaDsl` trait to reduce verbosity.

For instance, see below how the following JSON body can be expressed with both DSLs:

```json
{
  "keyA": {
    "a1": "...",
    "a2": "..."
  },
  "keyB": [1]
}
```

```scala
// Pact JVM LambdaDsl
val dsl: DslPart = LambdaDsl
  .newJsonBody { rootObj =>
    rootObj.`object`(
      "keyA",
      o => {
        o.stringType("a1")
        o.stringType("a2")
        ()
      }
    )
    rootObj.array(
      "keyB",
      a => {
        a.integerType()
        ()
      }
    )
    ()
  }
  .build()

// Pact4s ScalaDsl
val dsl: DslPart = newJsonObject { rootObj =>
  rootObj.newObject("keyA") { o =>
    o.stringType("a1")
    o.stringType("a2")
  }
  rootObj.newArray("keyB") { a =>
    a.integerType()
  }
}
```

#### Using JSON bodies

If you want to construct simple pacts with bodies that do not use the pact-jvm matching dsl, (`PactDslJsonBody`), a scala data type `A` can be passed to `.body` directly, provided there is an implicit instance of `pact4s.PactBodyEncoder[A]` provided.

Instances of `pact4s.PactBodyEncoder` are provided for:
- any type that has a `circe.Encoder` by adding the additional dependency: ```"io.github.jbwheatley" %% "pact4s-circe" % xxx```
- any type that has a `play.api.libs.json.Writes` by adding the additional dependency: ```"io.github.jbwheatley" %% "pact4s-play-json" % xxx```

This allows the following when using the import `pact4s.circe.implicits._`:
```scala
import pact4s.circe.implicits._

final case class Foo(a: String)

implicit val encoder: Encoder[Foo] = ???

val pact: RequestResponsePact =
  ConsumerPactBuilder
    .consumer("Consumer")
    .hasPactWith("Provider")
    .uponReceiving("a request to say Hello")
    .path("/hello")
    .method("POST")
    .body(Foo("abcde"), "application/json")
    // ...
```

Or the following when using the import `pact4s.playjson.implicits._`:
```scala
import pact4s.playjson.implicits._

final case class Foo(a: String)

implicit val reads: Writes[Foo] = ???

val pact: RequestResponsePact =
  ConsumerPactBuilder
    .consumer("Consumer")
    .hasPactWith("Provider")
    .uponReceiving("a request to say Hello")
    .path("/hello")
    .method("POST")
    .body(Foo("abcde"), "application/json")
    // ...
```

Or similarly when using the import `pact4s.sprayjson.implicits._`
```scala
import pact4s.sprayjson.implicits._

final case class Foo(a: String)

implicit object fooFormat extends JsonFormat[Foo] {
  override def write(object: Foo): JsValue = ???
  override def read(value: JsValue): Foo = ???
}

val pact: RequestResponsePact = {
  ConsumerPactBuilder
    .consumer("Consumer")
    .hasPactWith("Provider")
    .uponReceiving("a request to say Hello")
    .path("/hello")
    .method("POST")
    .body(Foo("abcde"), "application/json")
    // ...
}
```

### Request/Response Pacts

Request/response pacts use the `RequestResponsePactForger` trait. This trait requires that you provide a `RequestResponsePact`, which will be used to stand up a stub of the provider server. Each interaction in the pact should then run against the stub server using client the consumer application uses to interact with the real provider. This ensures that the client, and thus the application, is compatible with the pact being defined.

An example `RequestResponsePactForger` implementation is shown below.
```scala

override val pactTestExecutionContext: PactTestExecutionContext = new PactTestExecutionContext(
    "./my-sub-project/target/pacts" //this is where the pact file gets written to. It defaults to ./target/pacts (relative to the project base)
)

val pact: RequestResponsePact =
  ConsumerPactBuilder
    .consumer("Consumer")
    .hasPactWith("Provider")
    .uponReceiving("a request to say Hello") // this is the description
    .path("/hello")
    .method("POST")
    .body("""{"json": "body"}""", "application/json")
    .headers("other-header" -> "howdy")
    .willRespondWith()
    .status(200)
    .body("""{"response": "body"}""")
    .toPact()

// The client your application uses to consume from the provider
val client: Client = new Client(mockServer.getUrl)

// Now loop through each interaction in the Pact and verify it.
// This is psuedo-code that will need to be adapted to the testing framework you are using.
interactions.foreach(verify)
def verify(interaction: RequestResponseInteraction): Result = interaction.getDescription match {
  case "a request to say Hello" =>
    val response = client(interaction.getRequest)
    assert(response == interaction.getResponse)
  case description =>
    throw NoSuchElementException(s"Missing verification for interaction: '$description'.")
}
```

Upon completion of this test suite (and if all tests have passed) the pact will be written to the file defined in `pactTestExecutionContext`. **N.B.** The pact file will not be written unless the mock server has received a request for every interaction that you have defined in your pact. 

Examples:
- [munit-cats-effect](./example/consumer/src/test/scala/http/consumer/MunitPact.scala)
- [scalatest](./example/consumer/src/test/scala/http/consumer/ScalaTestPact.scala)
- [weaver](./example/consumer/src/test/scala/http/consumer/WeaverPact.scala)
- [ziotest](./example/consumer/src/test/scala/http/consumer/ZiotestPact.scala)

#### Choosing an address and/or port

If your consumer test needs the provider mock server to run on a specific address and/or port, you can override `mockProviderConfig` from `RequestResponsePactForger` like:

```scala
// Mock server will run on localhost, port 9003
override val mockProviderConfig: MockProviderConfig = MockProviderConfig.httpConfig("localhost", 9003)
```

#### 'Inline' Style of Processing Request/Response Pacts

Instead of defining one pact for the whole test class, containing all interactions for all test cases, you may want to define for each test case only the relevant partial pact. This can be done using the trait `InlineRequestResponsePactForging` and the method `withPact` that it provides.

An example ScalaTest using `InlineRequestResponsePactForging` is shown below.
```scala
class TestWithInlinePactDefinitions extends AnyFunSpec with InlineRequestResponsePactForging {

  override val pactTestExecutionContext: PactTestExecutionContext = new PactTestExecutionContext(
    "./my-sub-project/target/pacts" //this is where the pact file gets written to. It defaults to ./target/pacts (relative to the project base)
  )
  
  describe("Some client") {
    it("does one interaction") {
      withPact(
        ConsumerPactBuilder
                .consumer("Consumer")
                .hasPactWith("Provider")
                .uponReceiving("a request to say Hello")
                .path("/hello")
                .method("POST")
                .body("""{"json": "body"}""", "application/json")
                .headers("other-header" -> "howdy")
                .willRespondWith()
                .status(200)
                .body("""{"response": "body"}""")
                .toPact()
      ) { mockServer: BaseMockServer =>
        // The client your application uses to consume from the provider
        val client: Client = new Client(mockServer.getUrl)
        // ... test code that depends on the defined interaction(s)
      }
    }
    
    it("does another interaction") {
      withPact(
        ConsumerPactBuilder
                .consumer("Consumer")
                .hasPactWith("Provider")
                .uponReceiving("a request to say Bye")
                .path("/bye")
                .method("POST")
                .body("""{"json": "body"}""", "application/json")
                .headers("other-header" -> "see ya")
                .willRespondWith()
                .status(200)
                .body("""{"response": "body"}""")
                .toPact()
      ) { mockServer: BaseMockServer =>
        // The client your application uses to consume from the provider
        val client: Client = new Client(mockServer.getUrl)
        // ... test code that depends on the defined interaction(s)
      }
    }
  }
}
```

This style may be useful when it is impractical to write all interactions for all test cases in one single pact.
While in this approach the `BaseMockServer` is created and started for each test case individually, it does not
appear to have a noticeable performance impact.

Note that if you want to set a specific port while using the "inline" style, you cannot use parallel test execution, as
the same port cannot be used by multiple parallel instances of the mock server.
See e.g. [WeaverInlinePactSequential](./example/consumer/src/test/scala/http/consumer/WeaverInlinePact.scala) and [ZiotestInlinePactSequential](./example/consumer/src/test/scala/http/consumer/ZiotestInlinePact.scala)
(MUnit and ScalaTest execute tests sequentially by default).


### Message Pacts

Message pacts use the `MessagePactForger` trait. This trait requires that you provide a `MessagePact`. While the general principles of message forging and verification are the same as with request/response pacts, the guidance here will be a bit more abstract as actual implementations will vary by application and messaging framework. That said, at a high level you will want to generate a message and then feed it to your message handling function, which should expect a concrete class type. You do not want to verify what the message handling function does, only that it can receive the message payload without exception.

An example `MessagePactForger` implementation is shown below:
```scala
val pact: MessagePact = Pact4sMessagePactBuilder()
  .consumer("MessageConsumer")
  .hasPactWith("MessageProvider")
  .expectsToReceive("A message to say hello")
  .withContent(Json.obj("hello" -> "harry".asJson))
  .withMetadata(Map("hi" -> "there"))
  .toMessagePact

// You will need access to the methods that actually handle your messages, for example on the application.
val application: Application = new Application()

// Now loop through each message and verify it.
// This is psuedo-code that will need to be adapted to the testing framework you are using.
messages.foreach(verify)
def verify(message: Message): Result = message.getDescription match {
  case "a request to say hello" =>
    // You will probably need to convert the Pact Message into some other format...
    val applicationMessage = new ApplicationMessage(message.contentAsBytes, message.metadata)
    assertNoException(application.handleMessage(applicationMessage))
  case description =>
    throw NoSuchElementException(s"Missing verification for message: '$description'.")
}
```

If your application framework supports it, another option would be to publish the Pact message and have your application consume it without error. Note that this would be testing more than just the Pact itself, though, and may or may not be beneficial to you.

### Mixed Pacts

Note that if your project has both request/response and message pacts, you will need to write them into separate pact files due to [pact-jvm not currently supporting mixed pacts](https://github.com/pact-foundation/pact-jvm/issues/610). Pact JSON files are written in the format `<consumer_name>_<provider_name>.json`. Realistically, this means that you will need to choose a different provider name for the message pacts, the request/response pacts, or both. For example, you could use `api.provider` as the provider name for the request/response pacts, or `message.provider` as the provider name for the message pacts.

## Publishing Pacts

This library does not (and won't ever) provide native support for publishing consumer pacts to the pact broker. For this, we recommend using the [Pact Broker CLI](https://github.com/pact-foundation/pact_broker-client) provided by the pact foundation as part of your CI pipeline.

If you have previously been relying on the [`scala-pact`](https://github.com/ITV/scala-pact) sbt plugin to publish pacts to a pact broker, compatability with pacts produced by pact-jvm was added in version 3.3.1. By adding the sbt setting `areScalaPactContracts := false`, the scala-pact plugin will be able to publish pacts produced by this library, and any other pact-jvm based consumer pact testing library.

## Verifying Pacts

Verification can either be done as part of your CI pipeline, again by using the `Pact Broker CLI`, or by writing a verification test within your project. The test modules in `pact4s` share the following interface for how pacts are retrieved from either a pact broker, or a file: 

```scala
override val provider: ProviderInfoBuilder = 
  ProviderInfoBuilder(
    name = "Provider",
    protocol = "http",
    host = "localhost",
    port = 1234,
    path = "/",
    pactSource = ???,
    stateManagement = None,
    verificationSettings = None,
    requestFilter = _ => None
  )
```

`PactSource` is an ADT that provides various different configurations for fetching pacts, either from the local filesystem or from a [Pact Broker](https://docs.pact.io/pact_broker).

### Pact Broker

Please note, due to the version of pact-jvm that is underpinning `pact4s`, the verification step uses the `Pacts For Verification` API in the pact broker. See this issue here for more information: https://github.com/pact-foundation/pact_broker/issues/307. This may not be available in earlier versions of the pact-broker, so make sure you are using the latest release of the broker.

Pacts produced by pact-jvm (and by extension pact4s) by default conform to V3 of the pact specification, which *CANNOT* be verified by `scala-pact`.

### Request/Response Pact Verification

Verification of request/response pacts is extremely simple. You will want to extend the trait `PactVerifier` and set up a `ProviderInfoBuilder` (see above), which determines where the Pact files come from. Then you can verify them against your application by calling `verifyPacts`.

```scala
verifyPacts(
  // In this example, the results of verification are being uploaded to the Pact Broker
  publishVerificationResults = Some(
    PublishVerificationResults(
      // Normally this would be a version supplied by the build system, e.g. the Git commit hash, or a semantic version
      // like "1.0.0". See: https://docs.pact.io/getting_started/versioning_in_the_pact_broker
      providerVersion = "SNAPSHOT",
      // Normally this would be the git branch, e.g. "main" or "master"
      // See: https://docs.pact.io/pact_broker/tags/
      providerTags = Nil,
      //how long each interaction has to run before the test timeouts. 
      verificationTimeout = Some(30.seconds)
    )
  )
)
```

The `verifyPacts` method will send requests generated from the pact to your application, and then verify the response it gets back, also against the pact.

#### Request Filtering

It is sometimes necessary to modify the request that pact-jvm generates before it reaches your application. One common use-case for this is the injection of [Authorization headers](https://docs.pact.io/provider/handling_auth/) into the requests.

```scala
val provider: ProviderInfoBuilder = 
  ProviderInfoBuilder()
    // This will add an Authorization header with a bearer token to every request
    .withRequestFilter(request => List(ProviderRequestFilter.SetHeaders("Authorization" -> "bearer <token>")))
```

See [ProviderInfoBuilder](./shared/src/main/scala/pact4s/provider/ProviderInfoBuilder.scala) for more options.

### Message Pact Verification

Verification of message pacts is a little more abstract. You will want to extend the trait `MessagePactVerifier` and set up your `ProviderInfoBuilder` and `verifyPacts` methods just like you would for request/response pacts. You will then need to supply the messages for verification.

```scala
def messages: String => MessageAndMetadataBuilder = {
  case "A message to say hello" =>
    // This is psuedo-code. Normally these data would come from your application implementation.
    // For example, if you have a method which generates the message to publish, you could capture that value and
    // convert it to a MessageAndMetadataBuilder here.
    val metadata = Map("hi" -> "there")
    val body     = """{"hello":"harry"}"""
    MessageAndMetadataBuilder(body, metadata)
  case description =>
    throw new NoSuchElementException(s"Missing generator for message: '$description'")
}
```

### Provider state

Some pacts have requirements on the state of the provider. These are defined by the consumer by creating a pact with the `given` clause: 
```scala
  val pact: RequestResponsePact =
  ConsumerPactBuilder
    .consumer("Consumer")
    .hasPactWith("Provider")
    .`given`("user exists", Map("id" -> "bob")) // provider state id, and parameters (optional)
    .uponReceiving(...)
    ...
```

During the verification process, `pact-jvm` sends the provider state ID and parameters to a given http endpoint, and it is up to the end user to decode the request and manage the state of the provider. We have a domain model `pact4s.providerProviderState` representing this state. For users of `pact4s` we provide 2 ways of managing state in your verification tests. 

#### Exposing your own state-change endpoint

You can receive a state-change request to a POST endpoint on your mock provider server (e.g. named "setup", or something similar) that expects a request body of `{"state" : "the provider state id string", "params": { "id": "bob" } }` (`params` are optional). This endpoint can be configured on the `ProviderInfoBuilder` in your suite by setting the field `stateChangeUrl`:

```scala
val provider: ProviderInfoBuilder = 
  // alternatively: withStateChangeEndpoint("/setup")
  ProviderInfoBuilder().withStateChangeUrl("http://localhost:1234/setup")
```

Json codecs for the `ProviderState` can be found in the json modules in the `implicits` objects. 

See [our internal test setup here](https://github.com/jbwheatley/pact4s/blob/main/shared/src/test/scala/pact4s/MockProviderServer.scala) for an example of how we handle provider state.

#### Using a state-change function

It may not always be possible or desirable to add a endpoint to your provider server for this purpose. We can also simply configure a (maybe partial) function `ProviderState => Unit` for managing provider state on the `ProviderInfoBuilder` in your suite as follows: 

```scala
val provider: ProviderInfoBuilder = 
  ProviderInfoBuilder().withStateChangeFunction { 
   case ProviderState("state", params) => doSomething()
   case _ => ()
  } 
```

In this case, under the hood `pact4s` creates its own http server with an endpoint that receives the state-change requests from `pact-jvm`. By default, this server binds to a random port receives requests to `localhost:{port}/pact4s-state-change`. In case this clashes with any other server you are running, the url components can be overridden with `ProviderInfoBuilder#withStateChangeFunctionConfigOverrides`.

It is also possible to define a before hook (`() => Unit`) that will run at each state change before the state-change function:

```scala
val provider: ProviderInfoBuilder = 
  ProviderInfoBuilder().withStateManagementFunction(
    StateManagementFunction {
      case ProviderState("state", params) => doSomething()
      case _ => ()
    }
    .withBeforeEach(() => cleanTheState())
  )
```

It is also possible to return values from state change function in order to inject them into pact generator's expressions:

```scala
//In cases when consumer uses values from provider state
val consumer =
  ConsumerPactBuilder
    ...
    .pathFromProviderState("/hello/${userId}", "/hello/world")
    .willRespondWith()
    .body(PactDslJsonRootValue.valueFromProviderState("${creationTimestamp}", "2025-01-01'T'00:00:00"))
    .toPact

//Provider can inject those values by returning them from the state management function
val provider: ProviderInfoBuilder =
  ProviderInfoBuilder().withStateChangeFunctionAndResponse {
    case ProviderState("state", params) =>
      val user = createUser()
      Map(
        "userId" -> user.id, 
        "creationTimestamp" -> user.createdAt().toString
      )

    case _ => Map.empty
  }
```

## Logging 

- For consumer tests (forging pacts), you can enable additional logging from `pact-jvm` with the logger `au.com.dius.pact.consumer`.
- For provider tests (verifying pacts), you can enable additional logging from `pact-jvm` with the logger `au.com.dius.pact.provider`.
- Additional logging from `pact4s` is given by the logger `io.github.jbwheatley.pact4s.Pact4sLogger`. 

Here is an example `logback.xml` if you are using logback: 

```
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d [%thread] %-5level  %logger{35} - %msg%n</pattern>
        </encoder>
    </appender>
    <logger name="io.github.jbwheatley.pact4s.Pact4sLogger" level="INFO" />
    <logger name="au.com.dius.pact.consumer" level="DEBUG"/>
    <logger name="au.com.dius.pact.provider" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

---

## Contributing

Thank you for considering contributing to `pact4s`! Before opening a PR, please make sure you have read the [style guide](./docs/style-guide.md), and gone through 
the following checklist: 

- Scaladocs are included where necessary - e.g. where methods or fields have been added.
- Broken or invalidated methods/fields have had a deprecation tag applied.
- Tests for your new feature or bugfix have been included.
- You've run `sbt scalafmtAll` to format your new code and run `sbt headerCreateAll` to add headers to new files. 
- You've run `sbt commitCheck` to check formatting, headers, and run all the test suites across all scala versions. 
- Feature parity between the various library implementations is maintained.

---

## Adopters

You are using pact4s? We would love to know about you! Please [open a PR](https://github.com/jbwheatley/pact4s/edit/main/README.md) to add your project or company to the list below.

||||
| :---: | :---: | :---: |       
| <a href="https://www.kelkoogroup.com"><img src="https://www.kelkoogroup.com/wp-content/uploads/2019/11/logo-1024x189-1.jpg" height="40" alt="KelkooGroup" /></a> |  |  |
