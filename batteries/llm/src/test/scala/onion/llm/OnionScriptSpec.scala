package onion.llm

import onion.tools.Shell
import org.scalatest.funspec.AnyFunSpec

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets

/**
 * The battery as an Onion script sees it: records with `shape doc = json`, `ask`, and a
 * `select` over `Result` and the sealed `LlmError` with no `else`. Compiled and run
 * in-process by the Onion compiler against the fake Messages API.
 */
class OnionScriptSpec extends AnyFunSpec {
  import FakeAnthropic.*

  private val okAnswer =
    """{"title":"Weekly sync","actions":[{"owner":"Sato","task":"design"},{"owner":"Suzuki","task":"estimate"}],"risk":2}"""

  private def respond(req: Request): Reply = req.prompt match {
    case "case:ok"        => message(okAnswer)
    case "case:invalid"   => message("""{"title":"x","actions":[{"owner":"Sato","task":"a"},{"owner":7,"task":"b"}]}""")
    case "case:refusal"   => refusal("cyber", "declined")
    case "case:truncated" => message("""{"title":""", "max_tokens")
    case "case:api"       => error(400, "invalid_request_error", "bad request")
    case _                => message("こんにちは")
  }

  private val script =
    """import { onion.llm.Llm; onion.llm.LlmError }
      |record Action(owner: String, task: String) { shape doc = json }
      |record Summary(title: String, actions: List[Action], risk: Int?) { shape doc = json }
      |
      |def show(r: Result[Summary, LlmError]): void {
      |  select r {
      |    case ok is Result.Ok:   println("ok: " + ok.value().title() + " / " + ok.value().actions()[1].owner() + " / " + ok.value().risk())
      |    case err is Result.Err:
      |      select err.error() {
      |        case e is LlmError.Refusal:   println("declined: " + e.category())
      |        case e is LlmError.Invalid:   foreach d: Defect in e.defects() { println("defect: " + d.path()) }
      |        case e is LlmError.Truncated: println("output cut at max_tokens")
      |        case e is LlmError.Api:       println("api " + e.status() + ": " + e.message())
      |        case e is LlmError.Transport: println("network")
      |      }
      |  }
      |}
      |
      |val fake: String = System::getProperty("onion.llm.fake")
      |val claude = Llm::claude().effort("low").system("You summarize meetings.").baseUrl(fake).apiKey("test-key")
      |foreach c: String in ["ok", "invalid", "refusal", "truncated", "api"] {
      |  show(claude.ask(Summary::doc(), "case:" + c))
      |}
      |show(Llm::claude().baseUrl(System::getProperty("onion.llm.dead")).apiKey("test-key").ask(Summary::doc(), "case:ok"))
      |val t: Result[String, LlmError] = claude.text("Say hi in Japanese")
      |println("text: " + t.getOrElse("?"))
      |println("schema-sent: " + Summary::doc().jsonSchema())
      |""".stripMargin

  private def shell = new Shell(getClass.getClassLoader, Seq())

  private def runCapturing(source: String): (Shell.Result, String) = {
    val buffer = new ByteArrayOutputStream()
    val out = new PrintStream(buffer, true, StandardCharsets.UTF_8)
    val previous = System.out
    System.setOut(out)
    try {
      val result = shell.run(source, "LlmScript.on", Array())
      out.flush()
      (result, buffer.toString(StandardCharsets.UTF_8))
    } finally System.setOut(previous)
  }

  describe("an Onion script on the battery") {
    it("compiles the exhaustive select with no else and handles every outcome") {
      val fake = new FakeAnthropic(respond)
      try {
        System.setProperty("onion.llm.fake", fake.url)
        System.setProperty("onion.llm.dead", deadUrl())
        val (result, output) = runCapturing(script)
        assert(result.isInstanceOf[Shell.Success], output)
        val lines = output.linesIterator.toList
        assert(lines.contains("ok: Weekly sync / Suzuki / 2"), output)
        assert(lines.contains("defect: actions[1].owner"), output)
        assert(lines.contains("declined: cyber"), output)
        assert(lines.contains("output cut at max_tokens"), output)
        assert(lines.contains("api 400: bad request"), output)
        assert(lines.contains("network"), output)
        assert(lines.contains("text: こんにちは"), output)

        // What went over the wire for an ask: the record's own JSON Schema, effort low.
        val schema = lines.find(_.startsWith("schema-sent: ")).get.stripPrefix("schema-sent: ")
        val ask = fake.requests.find(_.prompt == "case:ok").get.json
        assert(ask.path("output_config").path("format").path("schema") == Json.readTree(schema))
        assert(ask.path("output_config").path("effort").asText() == "low")
        assert(ask.path("fallbacks").asText() == "default")
      } finally {
        fake.close()
        System.clearProperty("onion.llm.fake")
        System.clearProperty("onion.llm.dead")
      }
    }

    it("rejects a select that leaves out an LlmError case (E0042)") {
      val missing = script.linesIterator.filterNot(_.contains("LlmError.Transport")).mkString("\n")
      val compiler = new onion.compiler.OnionCompiler(
        new onion.compiler.CompilerConfig(Seq(), null, "UTF-8", "", 10))
      val thread = Thread.currentThread
      val previous = thread.getContextClassLoader
      thread.setContextClassLoader(getClass.getClassLoader)
      val result =
        try compiler.compileDetailed(Seq(new onion.compiler.StreamInputSource(
          () => new java.io.StringReader(missing), "Missing.on")))
        finally thread.setContextClassLoader(previous)
      assert(result.hasErrors)
      assert(result.diagnostics.allErrors.flatMap(_.code).contains("E0042"), result.diagnostics.allErrors)
    }
  }
}
