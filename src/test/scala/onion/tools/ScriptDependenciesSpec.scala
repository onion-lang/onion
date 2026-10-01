package onion.tools

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import onion.tools.project.{Dependency, FixtureMavenRepository}
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `//> using dep` end to end through the script runner, against a `file://` Maven repository
 * fixture (so no network): the jars reach compilation and execution, `--plan` and `--effects`
 * work with them, and the resolved classpath is cached per directive set.
 */
class ScriptDependenciesSpec extends AnyFunSuite with Matchers with EitherValues:

  /** One cache directory for this JVM, so the suite never writes to the user's real cache. */
  private val cacheRoot: Path =
    val existing = Option(System.getProperty("onion.cache.dir"))
    existing.map(Path.of(_)).getOrElse {
      val dir = Files.createTempDirectory("onion-script-cache")
      System.setProperty("onion.cache.dir", dir.toString)
      dir
    }

  private lazy val repository: Path = FixtureMavenRepository.publish()

  private def header: String =
    s"""//> using repository "${repository.toUri}"
       |//> using dep "${FixtureMavenRepository.Coordinate.render}"
       |""".stripMargin

  private def writeScript(source: String): Path =
    val dir = Files.createTempDirectory("onion-script-deps")
    val script = dir.resolve("deps.on")
    Files.writeString(script, source, UTF_8)
    script

  private def capture(body: => Int): (Int, String, String) =
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val outStream = PrintStream(out, true, UTF_8)
    val errStream = PrintStream(err, true, UTF_8)
    val (savedOut, savedErr) = (System.out, System.err)
    val code =
      try
        System.setOut(outStream); System.setErr(errStream)
        Console.withOut(outStream)(Console.withErr(errStream)(body))
      finally
        System.setOut(savedOut); System.setErr(savedErr)
    (code, out.toString(UTF_8), err.toString(UTF_8))

  test("a script compiles and runs against a //> using dep, transitives included"):
    val script = writeScript(
      header +
        """import { com.example.oniontest.Greeter }
          |IO::println(Greeter::greet())
          |""".stripMargin)

    val (code, out, err) = capture(ScriptRunner.runMain(Array(script.toString)))

    withClue(err) { code shouldBe 0 }
    // Greeter.greet() calls Core.message(): only the transitive dependency makes this link.
    out should include(FixtureMavenRepository.Greeting)

  test("a tool script with a directive gets --plan, and a normal run does the work"):
    val script = writeScript(
      header +
        """import { com.example.oniontest.Greeter }
          |tool save(dst: String): Int requires { write(dst), unknown } {
          |  Files::writeText(dst, Greeter::greet())
          |  return 0
          |}
          |""".stripMargin)
    val target = script.resolveSibling("out.txt")

    val (planCode, plan, planErr) =
      capture(ScriptRunner.runMain(Array(script.toString, target.toString, "--plan")))
    withClue(planErr) { planCode shouldBe 0 }
    plan should include("plan: `save` would")
    plan should include("derived from dst = " + target)
    Files.exists(target) shouldBe false

    val (runCode, _, runErr) = capture(ScriptRunner.runMain(Array(script.toString, target.toString)))
    withClue(runErr) { runCode shouldBe 0 }
    Files.readString(target, UTF_8) shouldBe FixtureMavenRepository.Greeting

  test("--effects works with directives"):
    val script = writeScript(
      header +
        """import { com.example.oniontest.Greeter }
          |def greeting(): String = Greeter::greet()
          |IO::println(greeting())
          |""".stripMargin)

    val (code, _, err) = capture(ScriptRunner.runMain(Array("--effects", script.toString)))

    withClue(err) { code shouldBe 0 }
    err should include("greeting")
    err should include("unknown")

  test("a directive error stops the run before compiling, naming the script and line"):
    val script = writeScript(
      """IO::println("never")
        |//> using dep "a:b:1"
        |""".stripMargin)

    val (code, out, err) = capture(ScriptRunner.runMain(Array(script.toString)))

    code should not be 0
    out should not include "never"
    err should include("deps.on:2:1: error:")
    err should include("must come before any code")

  test("an unresolvable dependency fails the run with the resolver's message"):
    val script = writeScript(
      s"""//> using repository "${repository.toUri}"
         |//> using dep "com.example.oniontest:no-such-artifact:9.9.9"
         |IO::println("never")
         |""".stripMargin)

    val (code, out, err) = capture(ScriptRunner.runMain(Array(script.toString)))

    code should not be 0
    out should not include "never"
    err should include("Could not resolve dependencies")

  test("the resolved classpath is cached per directive set and reused while its jars exist"):
    val cache = Files.createTempDirectory("onion-script-cache-unit")
    val directives = ScriptDirectives.Directives(
      Seq(FixtureMavenRepository.Coordinate), Seq(repository.toUri.toString))
    val sink = PrintStream(ByteArrayOutputStream(), true, UTF_8)

    val first = ScriptDependencies.resolve(directives, Some(cache), sink).value
    first.exists(_.endsWith("greeter-1.0.0.jar")) shouldBe true
    first.exists(_.endsWith("core-1.0.0.jar")) shouldBe true
    val cacheFile = cache.resolve("script-deps").resolve(ScriptDependencies.cacheKey(directives) + ".classpath")
    Files.isRegularFile(cacheFile) shouldBe true

    // A hit is served from the file alone: point it at a stand-in jar and it comes back.
    val standIn = Files.createTempFile("stand-in", ".jar")
    Files.write(cacheFile, Seq("onion-script-classpath-v1", standIn.toString).asJava, UTF_8)
    ScriptDependencies.resolve(directives, Some(cache), sink).value shouldBe Seq(standIn.toString)

    // A jar that has gone away invalidates the entry: resolution runs again and rewrites it.
    Files.delete(standIn)
    ScriptDependencies.resolve(directives, Some(cache), sink).value shouldBe first
    Files.readAllLines(cacheFile, UTF_8).asScala.drop(1) shouldBe first

  test("the cache key ignores dependency order but not repository order"):
    val a = Dependency("a", "a", "1")
    val b = Dependency("b", "b", "1")
    val key = ScriptDependencies.cacheKey
    key(ScriptDirectives.Directives(Seq(a, b), Seq("https://x", "https://y"))) shouldBe
      key(ScriptDirectives.Directives(Seq(b, a), Seq("https://x", "https://y")))
    key(ScriptDirectives.Directives(Seq(a, b), Seq("https://x", "https://y"))) should not be
      key(ScriptDirectives.Directives(Seq(a, b), Seq("https://y", "https://x")))
    key(ScriptDirectives.Directives(Seq(a), Seq.empty)) should not be
      key(ScriptDirectives.Directives(Seq(Dependency("a", "a", "2")), Seq.empty))

  test("the runner's own cache directory is the one this suite configured"):
    ScriptDependencies.cacheDirectory() shouldBe Some(cacheRoot)
