# LLM battery (`onion-llm`)

`onion-llm` is Onion's first *battery*: an optional library, published as its own
Maven artifact next to `onion.jar` rather than inside it. It calls Claude through the
Anthropic Messages API and reads the answer back as a typed record, through the same
`Shape` that already reads your JSON files:

```onion
//> using dep "org.onion_lang:onion-llm:<version>"
//> using repository "file:///C:/Users/you/.m2/repository"   // local install, for now
import { onion.llm.Llm; onion.llm.LlmError }

record Action(owner: String, task: String) { shape doc = json }
record Summary(title: String, actions: List[Action], risk: Int?) { shape doc = json }

val claude = Llm::claude().effort("low").system("You summarize meetings.")
val r: Result[Summary, LlmError] = claude.ask(Summary::doc(), "Summarize: " + notes)
select r {
  case ok is Result.Ok:   println(ok.value().title())
  case err is Result.Err:
    select err.error() {
      case e is LlmError.Refusal:   println("declined: " + e.category())
      case e is LlmError.Invalid:   foreach d: Defect in e.defects() { println(d.describe()) }
      case e is LlmError.Truncated: println("output cut at max_tokens")
      case e is LlmError.Api:       println("api " + e.status() + ": " + e.message())
      case e is LlmError.Transport: println("network: " + e.message())
    }
}

val t: Result[String, LlmError] = claude.text("Say hi in Japanese")
```

The record is the single description of the answer. `Summary::doc().jsonSchema()` is
sent as the structured-output format, so the model is constrained to that shape, and
`Summary::doc().parse(...)` reads the reply, so anything that still does not fit comes
back as defects with paths such as `actions[1].owner`, not as an exception.

## Install

The battery is not published to a public repository yet. Install it into your local
Maven repository from an Onion checkout:

```bash
sbt llm/publishM2
```

The version is the Onion build's version (`sbt "print llm/version"`); on a release tag
that is the release, for example `0.135.0`. From a working tree sbt-dynver adds a commit
and date suffix, so pin something easier to type when you install for local use:

```bash
sbt 'set llm / version := "0.135.0-local"' llm/publishM2
```

| | |
|---|---|
| Coordinates | `org.onion_lang:onion-llm:<version>` |
| Package | `onion.llm` (`Llm`, `Claude`, `LlmError`) |
| Depends on | `com.anthropic:anthropic-java` 2.68.0 (the official Java SDK) |
| Onion runtime | `provided`: scripts already run with `onion.jar`, so the battery does not bring a second copy |

A script names it with `//> using dep` and the local repository with
`//> using repository` (see the [script runner](../tools/script-runner.md)):

```onion
//> using dep "org.onion_lang:onion-llm:0.135.0-local"
//> using repository "file:///C:/Users/you/.m2/repository"
```

A project lists it in `onion.toml`:

```toml
[dependencies]
"org.onion_lang:onion-llm" = "0.135.0-local"

[[repositories]]
url = "file:///C:/Users/you/.m2/repository"
```

`onion.jar` itself does not change: the battery and the SDK are only on the classpath of
the scripts and projects that ask for them.

## The API

`Llm::claude()` returns a `Claude`: an immutable description of a client. Every builder
step returns a new `Claude` and leaves the receiver as it was, so a base client can be
shared and specialised. Building is effect-free; only `ask` and `text` call the API.

| Method | Meaning |
|---|---|
| `Llm::claude()` | The defaults below |
| `Llm::claude(model)` | The defaults with another model |
| `.model(id)` | The model id, e.g. `"claude-sonnet-5-5"` |
| `.effort(level)` | `output_config.effort`: `"low"`, `"medium"`, `"high"`, `"xhigh"`, `"max"` |
| `.maxTokens(n)` | `max_tokens`, the cap on the output |
| `.system(text)` | The system prompt (`null` removes it) |
| `.fallbacks(on)` | The server-side refusal fallback, on by default |
| `.baseUrl(url)` | Send requests elsewhere: a gateway, a proxy, a local fake in tests |
| `.apiKey(key)` | Use this key instead of resolving one from the environment |
| `.ask(shape, prompt)` | `Result[T, LlmError]`: an answer read through a `json` shape |
| `.text(prompt)` | `Result[String, LlmError]`: a free-text answer |

A builder step given a bad value (`effort("extreme")`, `maxTokens(0)`, a relative
`baseUrl`) throws `IllegalArgumentException` where it is written. So does
`ask` with a shape that has no JSON Schema: only a `shape name = json` shape has one
(`Shape.hasJsonSchema()`), and passing a regex or config shape is a programming error,
not something to discover from a response. These are the same rules as
`Http::request`'s builder.

### Defaults

| Setting | Default | Change it with |
|---|---|---|
| Model | `claude-opus-5-5` | `.model(...)` |
| Effort | `"medium"` | `.effort(...)` |
| `max_tokens` | 16000 | `.maxTokens(...)` |
| Refusal fallback | **on**: `fallbacks: "default"` with the `server-side-fallback-2026-07-01` beta header | `.fallbacks(false)` |
| Credentials | `ANTHROPIC_API_KEY`, or an `ant auth login` profile | `.apiKey(...)` |
| Retries | the SDK's: 2 retries with backoff for 429, 5xx and connection failures | — |

The refusal fallback is on by default. When a safety classifier declines a request, the
API re-runs it server-side on the fallback model Anthropic recommends for that refusal
category, instead of returning the refusal to you. You only see `LlmError.Refusal` when
every model in the chain declined. `.fallbacks(false)` sends neither the `fallbacks`
field nor the beta header, so a declined request comes straight back as a refusal.

The battery never sends a `thinking` parameter. Claude Opus 5.5 always thinks, and it
rejects both `thinking: {type: "disabled"}` and a token budget with a 400. Effort is
the control: lower effort means less thinking. Requests are non-streaming.

Credentials resolve as the SDK's `fromEnv()` resolves them: `ANTHROPIC_API_KEY` (or
`ANTHROPIC_AUTH_TOKEN`), else an `ant auth login` profile. `ANTHROPIC_BASE_URL` is
honoured, and `.baseUrl(...)` overrides it. With no credentials at all the request still
goes out, unauthenticated, and comes back as `LlmError.Api` 401 `authentication_error`.

### How a response is read

Each call checks the response in a fixed order:

1. `stop_reason: "refusal"` becomes `LlmError.Refusal`, with the `stop_details` category
   and explanation when the API gave them. A refusal is an HTTP 200, so it is checked
   before anything reads the content.
2. `stop_reason: "max_tokens"` (or `"model_context_window_exceeded"`) becomes
   `LlmError.Truncated`, keeping the partial text.
3. Otherwise the answer is the text of the response's text blocks; thinking blocks are
   skipped. `text` returns it. `ask` reads it with `shape.parse`, and an `Outcome.Bad`
   becomes `LlmError.Invalid` with every defect and the raw text.

## Errors

`LlmError` is a sealed Java interface with five records nested in it. Onion names
nested Java types with dots in patterns and checks a sealed scrutinee for
exhaustiveness, so the `select` at the top of this page needs no `else`, and leaving a
case out is a compile error (E0042).

| Case | Components | When |
|---|---|---|
| `LlmError.Transport` | `message()`, `cause()` | No HTTP response: the connection failed or broke, the response could not be read, or a configured credential source could not be resolved |
| `LlmError.Api` | `status()`, `errorType()`, `message()`, `retryable()` | The API answered with an error status. `errorType()` is the API's error type (`"invalid_request_error"`, `"rate_limit_error"`, `"overloaded_error"`, ...). `retryable()` is true for 429 and 5xx; the SDK has already retried those |
| `LlmError.Refusal` | `category()`, `explanation()` | `stop_reason: "refusal"`. Both components may be null |
| `LlmError.Truncated` | `stopReason()`, `partialText()` | The output hit `max_tokens`: raise `.maxTokens(...)` |
| `LlmError.Invalid` | `defects()`, `rawText()` | The answer does not read as the shape |

Every case also has `describe()`, a one-line account of it.

The API error's type is `errorType()`, not `type()`: `type` is a reserved word in
Onion, so `e.type()` would not parse.

Errors are values: `ask` and `text` return them in an `Err` and never throw. The
exceptions are the programming errors above (`IllegalArgumentException`), and an
interrupted thread.

## Effects and `--plan`

The battery jar ships an effect table at `META-INF/onion/effect-table.txt`:

```
onion.llm.Llm#*=pure
onion.llm.Claude#*=pure
onion.llm.Claude#ask=net:api.anthropic.com,env:ANTHROPIC_API_KEY
onion.llm.Claude#text=net:api.anthropic.com,env:ANTHROPIC_API_KEY
onion.llm.LlmError#*=pure
```

Building a client is `pure`. `ask` and `text` are `net` (to `api.anthropic.com`) and
`env` (they read `ANTHROPIC_API_KEY`). The error values are `pure`.

!!! note "Depends on library effect tables"
    The compiler reads effect tables from classpath jars, and the `effect:operand`
    syntax above, once the *library effect tables* change (branch
    `feat/library-effect-tables`) is merged. Until then the compiler does not see this
    table, so a call into the battery is `unknown` (see the
    [effects reference](../reference/effects.md)), and a `tool` that uses it must say so:

    ```onion
    tool summarize(notes: String, out: String): Int
      requires { read(notes), write(out), console, unknown }
    { ... }
    ```

    With the table loaded, the same tool declares `requires { read(notes), write(out),
    net, env, console }`, and `--plan` lists `net api.anthropic.com` and
    `env ANTHROPIC_API_KEY` alongside the file operands, without calling the API.

## Testing without an API key

Point the client at a local server that answers like the Messages API. `baseUrl` and
`apiKey` exist for this. A test that should never reach the network can start an
`onion.Server`, serve a canned response, and stop it afterwards:

```onion
import { onion.llm.Llm; onion.llm.Claude; onion.llm.LlmError }

def summarizeAgainst(fixture: String): Result[Summary, LlmError] {
  val body = Files::readText("tests/fixtures/" + fixture)
  val server = Server::start("127.0.0.1", 0).handleAll { req ->
    Server::status(200, body).withHeader("Content-Type", "application/json")
  }
  try {
    val claude: Claude = Llm::claude().baseUrl("http://127.0.0.1:" + server.port()).apiKey("test-key")
    return claude.ask(Summary::doc(), "notes")
  } finally {
    server.stop()   // the JDK's HTTP dispatcher thread is not a daemon
  }
}
```

A canned response has the Messages API's shape: `content` with a thinking block and
then a text block, and a `stop_reason`. For a refusal, `content` is empty,
`stop_reason` is `"refusal"`, and `stop_details` carries `category` and
`explanation`.

## Building the battery

The battery is the sbt subproject `llm` (`batteries/llm`). The root project neither
aggregates it nor depends on it, so `sbt test`, `sbt assembly` and `sbt dist` are
unchanged. Work on it by name:

```bash
sbt llm/test          # Java API and Onion-script tests against a local fake API
sbt llm/package       # target/out/jvm/u/onion-llm/onion-llm-<version>.jar
sbt llm/publishM2     # install into ~/.m2/repository
```

The tests never reach the network. They point the client at a local
`com.sun.net.httpserver.HttpServer` that returns canned Messages API JSON and records
each request, so they can check what was sent: the model, `output_config.effort`, the
shape's JSON Schema as `output_config.format`, `fallbacks: "default"` and the beta header.
