package onion.llm

import onion.{Function0, Function1, Result, Shape, Shapes}
import org.scalatest.funspec.AnyFunSpec

import java.util.{List as JList}
import scala.jdk.CollectionConverters.*

/** The battery's Java API against a local fake of the Messages API (no key, no network). */
class ClaudeSpec extends AnyFunSpec {
  import ClaudeSpec.*
  import FakeAnthropic.*

  private def withFake[A](respond: Request => Reply)(body: (FakeAnthropic, Claude) => A): A = {
    val fake = new FakeAnthropic(respond)
    try body(fake, Llm.claude().baseUrl(fake.url).apiKey("test-key"))
    finally fake.close()
  }

  private def err[T](r: Result[T, LlmError]): LlmError = {
    assert(r.isErr, s"expected an Err, got $r")
    r.getError
  }

  private val okAnswer =
    """{"title":"Weekly sync","actions":[{"owner":"Sato","task":"design review"},{"owner":"Suzuki","task":"re-estimate"}],"risk":2}"""

  describe("ask(shape, prompt)") {
    it("returns Ok with a nested-record value read through the shape") {
      withFake(_ => message(okAnswer)) { (_, claude) =>
        val r = claude.ask(summaryShape, "Summarize: notes")
        assert(r.isOk, s"got $r")
        val s = r.get
        assert(s.title == "Weekly sync")
        assert(s.actions.asScala.map(_.owner) == Seq("Sato", "Suzuki"))
        assert(s.risk == Integer.valueOf(2))
      }
    }

    it("sends model, effort, the shape's JSON Schema as output_config.format, and fallbacks: \"default\"") {
      withFake(_ => message(okAnswer)) { (fake, claude) =>
        claude.system("You summarize meetings.").ask(summaryShape, "Summarize: notes")
        val req = fake.requests.last
        assert(req.method == "POST")
        assert(req.uri.startsWith("/v1/messages"))
        assert(req.headers.get("x-api-key").contains("test-key"))
        assert(req.headers.getOrElse("anthropic-beta", "").split(",").map(_.trim).contains("server-side-fallback-2026-07-01"))
        val json = req.json
        assert(json.path("model").asText() == "claude-opus-5-5")
        assert(json.path("max_tokens").asLong() == 16000L)
        assert(json.path("output_config").path("effort").asText() == "medium")
        assert(json.path("output_config").path("format").path("type").asText() == "json_schema")
        assert(json.path("output_config").path("format").path("schema") == Json.readTree(summaryShape.jsonSchema()))
        assert(json.path("fallbacks").isTextual && json.path("fallbacks").asText() == "default")
        assert(!json.has("thinking"), "Claude Opus 5.5 rejects any thinking: disabled/budget; none is sent")
        assert(!json.has("temperature"))
        assert(json.path("system").toString.contains("You summarize meetings."))
        assert(req.prompt == "Summarize: notes")
      }
    }

    it("returns Invalid with every path defect and the raw text when the answer does not fit") {
      val bad = """{"title":"x","actions":[{"owner":"Sato","task":"a"},{"owner":7,"task":"b"}],"risk":"high"}"""
      withFake(_ => message(bad)) { (_, claude) =>
        err(claude.ask(summaryShape, "p")) match {
          case e: LlmError.Invalid =>
            val paths = e.defects.asScala.map(_.path).toSet
            assert(paths.contains("actions[1].owner"), s"paths: $paths")
            assert(paths.contains("risk"), s"paths: $paths")
            assert(e.rawText == bad)
          case other => fail(s"expected Invalid, got $other")
        }
      }
    }

    it("throws IllegalArgumentException, before any request, for a shape with no JSON Schema") {
      withFake(_ => message(okAnswer)) { (fake, claude) =>
        val noSchema: Shape[String] = Shapes.regex[String](
          "(.*)", JList.of("s"), JList.of("String"),
          ((l: JList[Object]) => l.get(0).asInstanceOf[String]): Function1[JList[Object], String], null)
        val e = intercept[IllegalArgumentException](claude.ask(noSchema, "p"))
        assert(e.getMessage.contains("JSON Schema"))
        assert(fake.requests.isEmpty)
      }
    }
  }

  describe("stop_reason is checked before content") {
    it("maps a refusal to Refusal with the stop_details category and explanation") {
      withFake(_ => refusal("cyber", "This request was declined.")) { (_, claude) =>
        assert(err(claude.ask(summaryShape, "p")) == LlmError.Refusal("cyber", "This request was declined."))
        assert(err(claude.text("p")) == LlmError.Refusal("cyber", "This request was declined."))
      }
    }

    it("maps stop_reason max_tokens to Truncated, keeping the partial text") {
      withFake(_ => message("""{"title":"cut""", "max_tokens")) { (_, claude) =>
        assert(err(claude.ask(summaryShape, "p")) == LlmError.Truncated("max_tokens", """{"title":"cut"""))
      }
    }
  }

  describe("API errors") {
    it("maps 400 to a non-retryable Api with the API's error type and message, sent once") {
      withFake(_ => error(400, "invalid_request_error", "max_tokens: too large")) { (fake, claude) =>
        assert(err(claude.text("p")) == LlmError.Api(400, "invalid_request_error", "max_tokens: too large", false))
        assert(fake.requests.size == 1)
      }
    }

    it("maps 429 to a retryable Api") {
      withFake(_ => error(429, "rate_limit_error", "slow down")) { (_, claude) =>
        assert(err(claude.text("p")) == LlmError.Api(429, "rate_limit_error", "slow down", true))
      }
    }

    it("maps 500 to a retryable Api after the SDK's default retries") {
      withFake(_ => error(500, "api_error", "internal", retry = true)) { (fake, claude) =>
        assert(err(claude.text("p")) == LlmError.Api(500, "api_error", "internal", true))
        assert(fake.requests.size == 3, "the SDK's default of 2 retries is kept")
      }
    }

    it("maps a refused connection to Transport") {
      err(Llm.claude().baseUrl(deadUrl()).apiKey("test-key").text("p")) match {
        case e: LlmError.Transport =>
          assert(e.message.nonEmpty)
          assert(e.cause != null)
        case other => fail(s"expected Transport, got $other")
      }
    }
  }

  describe("text(prompt)") {
    it("returns the text blocks' text, skipping thinking blocks, with no output_config.format") {
      withFake(_ => message("こんにちは")) { (fake, claude) =>
        assert(claude.effort("low").text("Say hi in Japanese").get == "こんにちは")
        val json = fake.lastJson
        assert(json.path("output_config").path("effort").asText() == "low")
        assert(!json.path("output_config").has("format"))
      }
    }
  }

  describe("the builder") {
    it("is immutable and validates where it is written") {
      val base = Llm.claude()
      val tuned = base.model("claude-sonnet-5-5").effort("HIGH").maxTokens(2000).fallbacks(false)
      assert(base.model == "claude-opus-5-5" && base.effort == "medium" && base.maxTokens == 16000 && base.fallbacksEnabled)
      assert(tuned.model == "claude-sonnet-5-5" && tuned.effort == "high" && tuned.maxTokens == 2000 && !tuned.fallbacksEnabled)
      intercept[IllegalArgumentException](base.effort("extreme"))
      intercept[IllegalArgumentException](base.maxTokens(0))
      intercept[IllegalArgumentException](base.baseUrl("localhost:8080"))
      intercept[IllegalArgumentException](base.model(" "))
    }

    it("turning the fallback off drops both the fallbacks field and the beta header") {
      withFake(_ => message("ok")) { (fake, claude) =>
        claude.fallbacks(false).text("p")
        val req = fake.requests.last
        assert(!req.json.has("fallbacks"))
        assert(!req.headers.getOrElse("anthropic-beta", "").contains("server-side-fallback"))
      }
    }
  }

  describe("the effect table") {
    it("ships in the jar and names only public methods of the battery's classes") {
      val in = getClass.getClassLoader.getResourceAsStream("META-INF/onion/effect-table.txt")
      assert(in != null)
      val lines = scala.io.Source.fromInputStream(in, "UTF-8").getLines()
        .map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#")).toList
      val entries = lines.map { l =>
        val Array(spec, effects) = l.split("=", 2)
        val Array(cls, method) = spec.split("#", 2)
        (cls, method, effects)
      }
      entries.foreach { case (cls, method, _) =>
        val c = Class.forName(cls)
        if (method != "*") assert(c.getMethods.exists(_.getName == method), s"$cls#$method")
      }
      val effects = entries.map { case (c, m, e) => s"$c#$m" -> e }.toMap
      assert(effects("onion.llm.Claude#ask") == "net:api.anthropic.com,env:ANTHROPIC_API_KEY")
      assert(effects("onion.llm.Claude#text") == "net:api.anthropic.com,env:ANTHROPIC_API_KEY")
      assert(effects("onion.llm.Claude#*") == "pure")
      assert(effects("onion.llm.LlmError#*") == "pure")
    }
  }
}

object ClaudeSpec {
  final case class Action(owner: String, task: String)
  final case class Summary(title: String, actions: JList[Action], risk: Integer)

  // Each build function is its own lambda: MappedShape tells records apart (to detect a
  // recursive shape) by the build function's class.
  val actionShape: Shape[Action] = Shapes.json[Action](
    JList.of("owner", "task"), JList.of("String", "String"),
    ((l: JList[Object]) => Action(l.get(0).asInstanceOf[String], l.get(1).asInstanceOf[String])): Function1[JList[Object], Action],
    ((a: Action) => JList.of[Object](a.owner, a.task)): Function1[Action, JList[Object]])

  val summaryShape: Shape[Summary] = Shapes.json[Summary](
    JList.of("title", "actions", "risk"), JList.of("String", "List[Nested]", "Int?"),
    java.util.Arrays.asList[Object](null, ((() => actionShape): Function0[Object]), null),
    ((l: JList[Object]) => Summary(l.get(0).asInstanceOf[String], l.get(1).asInstanceOf[JList[Action]],
      l.get(2).asInstanceOf[Integer])): Function1[JList[Object], Summary],
    ((s: Summary) => java.util.Arrays.asList[Object](s.title, s.actions, s.risk)): Function1[Summary, JList[Object]])
}
