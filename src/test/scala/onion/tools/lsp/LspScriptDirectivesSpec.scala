package onion.tools.lsp

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, LinkedBlockingQueue, TimeUnit}

import scala.jdk.CollectionConverters.*

import onion.tools.ScriptDirectives
import onion.tools.project.FixtureMavenRepository
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * A standalone script's `//> using dep` directives reach the editor's validation classpath.
 *
 * The editor used to validate every script against `.` alone, so a script that `onion
 * script.on` ran happily showed every type its dependency provides as not found (E0003).
 * Resolution goes through the runner's own parser, resolver and cache, off the request
 * thread; until it finishes the script's diagnostics are held, and a malformed directive is
 * reported at its line with the message the CLI prints.
 */
class LspScriptDirectivesSpec extends AnyFunSuite with Matchers:

  /** One cache directory for this JVM, so the suite never writes to the user's real cache. */
  Option(System.getProperty("onion.cache.dir")).getOrElse {
    System.setProperty("onion.cache.dir", Files.createTempDirectory("onion-lsp-script-cache").toString)
  }

  private final class RecordingClient extends LanguageClient:
    val published = new LinkedBlockingQueue[PublishDiagnosticsParams]()
    override def telemetryEvent(o: Object): Unit = ()
    override def publishDiagnostics(p: PublishDiagnosticsParams): Unit = published.add(p)
    override def showMessage(m: MessageParams): Unit = ()
    override def showMessageRequest(r: ShowMessageRequestParams): CompletableFuture[MessageActionItem] =
      CompletableFuture.completedFuture(null)
    override def logMessage(m: MessageParams): Unit = ()

    /** The next publish, waiting up to `seconds` for it. */
    def next(seconds: Long = 120): Seq[Diagnostic] =
      val p = published.poll(seconds, TimeUnit.SECONDS)
      assert(p != null, s"no diagnostics were published within ${seconds}s")
      p.getDiagnostics.asScala.toSeq

  private final case class Opened(client: RecordingClient, service: OnionTextDocumentService, uri: String)

  private def open(content: String, dir: Path = Files.createTempDirectory("onion-lsp-script")): Opened =
    val file = dir.resolve("script.on")
    Files.writeString(file, content, UTF_8)
    val client = RecordingClient()
    val service = OnionTextDocumentService(null)
    service.connect(client)
    val uri = file.toUri.toString
    service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "onion", 1, content)))
    Opened(client, service, uri)

  private def errors(diagnostics: Seq[Diagnostic]): Seq[Diagnostic] =
    diagnostics.filter(_.getSeverity == DiagnosticSeverity.Error)

  private def codes(diagnostics: Seq[Diagnostic]): Seq[String] =
    diagnostics.flatMap(d => Option(d.getCode).map(_.getLeft))

  private def describe(diagnostics: Seq[Diagnostic]): String =
    diagnostics.map(d => s"${d.getSeverity} ${d.getRange.getStart}: ${d.getMessage}").mkString("\n")

  private val usesGreeter =
    """import { com.example.oniontest.Greeter }
      |IO::println(Greeter::greet())
      |""".stripMargin

  private def header(repository: Path): String =
    s"""//> using repository "${repository.toUri}"
       |//> using dep "${FixtureMavenRepository.Coordinate.render}"
       |""".stripMargin

  test("without its directive, the script's use of the dependency is E0003 (the control)"):
    val Opened(client, _, _) = open(usesGreeter)
    codes(client.next()) should contain("E0003")

  test("a script's dependency is resolved off the request thread, and then there is no false E0003"):
    // A fresh repository URL is a directive set the cache has never seen: resolution is cold.
    val Opened(client, _, _) = open(header(FixtureMavenRepository.publish()) + usesGreeter)

    // didOpen returned before resolving; what it published is the hold note, not the
    // phantom errors validating without the jar would have produced.
    val held = client.next()
    withClue(describe(held)) {
      errors(held) shouldBe empty
      held.map(_.getSeverity) shouldBe Seq(DiagnosticSeverity.Information)
      held.head.getRange.getStart.getLine shouldBe 0
      held.head.getRange.getEnd.getLine shouldBe 1
    }

    // Then, once resolution finishes, the server validates the script again on its own.
    val resolved = client.next()
    withClue(describe(resolved)) {
      codes(resolved) should not contain "E0003"
      errors(resolved) shouldBe empty
      resolved.exists(_.getSeverity == DiagnosticSeverity.Information) shouldBe false
    }

  test("an edit to a resolved script validates at once, from the resolved set"):
    val repository = FixtureMavenRepository.publish()
    val Opened(client, service, uri) = open(header(repository) + usesGreeter)
    client.next() // the hold
    client.next() // resolved

    val edited = header(repository) + usesGreeter + "val n: Int = \"not an int\"\n"
    service.didChange(DidChangeTextDocumentParams(
      VersionedTextDocumentIdentifier(uri, 2),
      java.util.List.of(TextDocumentContentChangeEvent(edited))))
    // Synchronous this time: no hold, straight to the compiler's answer.
    val diagnostics = client.next(seconds = 5)
    withClue(describe(diagnostics)) {
      diagnostics.exists(_.getSeverity == DiagnosticSeverity.Information) shouldBe false
      codes(diagnostics) should not contain "E0003"
      errors(diagnostics) should not be empty // the type error, and only that
    }

  test("a malformed directive is an error at its line, with the message the CLI prints"):
    val content = "//> using dep \"a:b\"\nIO::println(\"hi\")\n"
    val Opened(client, _, _) = open(content)
    val expected = ScriptDirectives.parse(content).swap.toOption.get

    val diagnostics = client.next()
    val directive = errors(diagnostics).find(_.getMessage == expected.message)
    withClue(describe(diagnostics)) { directive should not be empty }
    directive.get.getRange.getStart.getLine shouldBe expected.line - 1
    directive.get.getRange.getStart.getCharacter shouldBe expected.column - 1

  test("a directive after code is reported where the CLI reports it"):
    val content = "IO::println(\"hi\")\n//> using dep \"a:b:1\"\n"
    val Opened(client, _, _) = open(content)

    val diagnostics = client.next()
    val directive = errors(diagnostics).find(_.getMessage.contains("must come before any code"))
    withClue(describe(diagnostics)) { directive should not be empty }
    directive.get.getRange.getStart.getLine shouldBe 1

  test("an unresolvable dependency is an error on the directives, not a hang"):
    val repository = FixtureMavenRepository.publish()
    val Opened(client, _, _) = open(
      s"""//> using repository "${repository.toUri}"
         |//> using dep "com.example.oniontest:no-such-artifact:9.9.9"
         |IO::println("hi")
         |""".stripMargin)

    client.next() // the hold
    val failed = client.next()
    withClue(describe(failed)) {
      val resolution = errors(failed).filter(_.getRange.getStart.getLine == 0)
      resolution should have size 1
      resolution.head.getMessage should include("no-such-artifact")
    }

  test("inside a project, directives are not resolved, and a warning says so"):
    val root = Files.createTempDirectory("onion-lsp-script-project").toRealPath()
    Files.writeString(root.resolve("onion.toml"),
      "[package]\nname = \"demo\"\nversion = \"1.0.0\"\n", UTF_8)
    val src = Files.createDirectories(root.resolve("src"))
    val Opened(client, _, _) = open(header(FixtureMavenRepository.publish()) + usesGreeter, src)

    // Published synchronously, from the project's classpath: no hold, no resolution.
    val diagnostics = client.next(seconds = 30)
    withClue(describe(diagnostics)) {
      diagnostics.exists(_.getSeverity == DiagnosticSeverity.Information) shouldBe false
      val warning = diagnostics.filter(_.getSeverity == DiagnosticSeverity.Warning)
        .filter(_.getMessage.contains("onion.toml"))
      warning should have size 1
      warning.head.getRange.getStart.getLine shouldBe 0
    }
    client.published.poll(2, TimeUnit.SECONDS) shouldBe null
