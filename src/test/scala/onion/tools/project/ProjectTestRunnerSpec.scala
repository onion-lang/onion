package onion.tools.project

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

import scala.collection.mutable.ArrayBuffer

import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProjectTestRunnerSpec extends AnyFunSuite with Matchers:
  private final case class Invocation(result: TestRunResult, stdout: String, stderr: String)

  private final class FakeBackend(
    programs: Map[String, () => ProgramResult] = Map.empty,
    compileFailures: Map[String, ProjectTestFailure] = Map.empty,
    compileThrows: Map[String, Throwable] = Map.empty,
    cleanupFailures: Set[String] = Set.empty
  ) extends ProjectTestBackend:
    val events = ArrayBuffer.empty[String]

    override def createWorkspace(
      build: ProjectBuild,
      source: ProjectSource
    ): ProjectTestWorkspace =
      events += s"create:${source.relative}"
      val root = Files.createTempDirectory("onion-project-test-unit")
      ProjectTestWorkspace(root, Files.createDirectory(root.resolve("classes")))

    override def compile(
      build: ProjectBuild,
      source: ProjectSource,
      workspace: ProjectTestWorkspace
    ): Either[ProjectTestFailure, PreparedProjectTest] =
      events += s"compile:${source.relative}"
      compileThrows.get(source.relative).foreach(throw _)
      compileFailures.get(source.relative) match
        case Some(failure) => Left(failure)
        case None => Right(PreparedProjectTest(source.relative + "Main", ""))

    override def invoke(
      build: ProjectBuild,
      workspace: ProjectTestWorkspace,
      prepared: PreparedProjectTest
    ): ProgramResult =
      val source = prepared.className.stripSuffix("Main")
      events += s"invoke:$source"
      programs.getOrElse(source, () => ProgramResult.Success(null))()

    override def cleanup(
      build: ProjectBuild,
      source: ProjectSource,
      workspace: ProjectTestWorkspace
    ): Unit =
      events += s"cleanup:${source.relative}"
      if cleanupFailures(source.relative) then
        throw IOException("cleanup exploded")
      else
        FileTree.delete(workspace.root, workspace.root.getParent)

  test("derives deterministic summary counts"):
    val result = TestRunResult(
      Vector(
        TestCaseResult("tests/a_test.on", passed = true, "", "", None),
        TestCaseResult("tests/b_test.on", passed = false, "", "", Some("boom")),
        TestCaseResult("tests/c_test.on", passed = true, "", "", None)
      )
    )

    result.passed shouldBe 2
    result.failed shouldBe 1
    result.successful shouldBe false
    TestRunResult(Vector.empty).successful shouldBe true

  test("sorts tests, executes sequentially, continues after failure, and renders exact output"):
    val backend = FakeBackend(
      programs = Map(
        "tests/alpha_test.on" -> (() =>
          System.out.print("hidden-alpha")
          ProgramResult.Success(null)
        ),
        "tests/beta_test.on" -> (() =>
          System.out.print("beta-out")
          System.err.print("beta-err")
          ProgramResult.Failure(
            "IllegalStateException: boom",
            Some(IllegalStateException("boom"))
          )
        ),
        "tests/zeta_test.on" -> (() => ProgramResult.Success(null))
      )
    )
    val build = fixture(Vector(
      "tests/zeta_test.on",
      "tests/beta_test.on",
      "tests/alpha_test.on"
    ))

    val invocation = invoke(ProjectTestRunner(backend), build)

    invocation.result.cases.map(_.source) shouldBe Vector(
      "tests/alpha_test.on",
      "tests/beta_test.on",
      "tests/zeta_test.on"
    )
    invocation.result.passed shouldBe 2
    invocation.result.failed shouldBe 1
    backend.events.toVector shouldBe Vector(
      "create:tests/alpha_test.on",
      "compile:tests/alpha_test.on",
      "invoke:tests/alpha_test.on",
      "cleanup:tests/alpha_test.on",
      "create:tests/beta_test.on",
      "compile:tests/beta_test.on",
      "invoke:tests/beta_test.on",
      "cleanup:tests/beta_test.on",
      "create:tests/zeta_test.on",
      "compile:tests/zeta_test.on",
      "invoke:tests/zeta_test.on",
      "cleanup:tests/zeta_test.on"
    )
    invocation.stdout shouldBe
      """test tests/alpha_test.on ... ok
        |test tests/beta_test.on ... FAILED
        |beta-out
        |test tests/zeta_test.on ... ok
        |
        |3 tests, 2 passed, 1 failed
        |""".stripMargin
    invocation.stderr shouldBe
      """beta-err
        |error: tests/beta_test.on: IllegalStateException: boom
        |""".stripMargin

  test("renders compile diagnostics as a case failure and still runs later tests"):
    val backend = FakeBackend(
      compileFailures = Map(
        "tests/a_bad_test.on" ->
          ProjectTestFailure(
            "Test compilation failed",
            "tests/a_bad_test.on:1:1: unknown name\n1 error(s)\n"
          )
      )
    )
    val build = fixture(Vector("tests/z_good_test.on", "tests/a_bad_test.on"))

    val invocation = invoke(ProjectTestRunner(backend), build)

    invocation.result.failed shouldBe 1
    backend.events should contain("invoke:tests/z_good_test.on")
    backend.events should not contain "invoke:tests/a_bad_test.on"
    invocation.stdout shouldBe
      """test tests/a_bad_test.on ... FAILED
        |test tests/z_good_test.on ... ok
        |
        |2 tests, 1 passed, 1 failed
        |""".stripMargin
    invocation.stderr shouldBe
      """tests/a_bad_test.on:1:1: unknown name
        |1 error(s)
        |error: tests/a_bad_test.on: Test compilation failed
        |""".stripMargin

  test("turns a compile infrastructure exception into one case failure and continues"):
    val backend = FakeBackend(
      compileThrows = Map(
        "tests/a_broken_test.on" -> IOException("compiler unavailable")
      )
    )
    val build = fixture(Vector("tests/b_good_test.on", "tests/a_broken_test.on"))

    val invocation = invoke(ProjectTestRunner(backend), build)

    invocation.result.failed shouldBe 1
    backend.events should contain("cleanup:tests/a_broken_test.on")
    backend.events should contain("invoke:tests/b_good_test.on")
    invocation.stderr should include(
      "error: tests/a_broken_test.on: Could not prepare test: IOException: compiler unavailable\n"
    )

  test("verbose mode shows passed stdout and stderr with deterministic line boundaries"):
    val backend = FakeBackend(
      programs = Map(
        "tests/passing_test.on" -> (() =>
          System.out.print("passed-out")
          System.err.print("passed-err")
          ProgramResult.Success(null)
        )
      )
    )

    val invocation = invoke(
      ProjectTestRunner(backend),
      fixture(Vector("tests/passing_test.on")),
      verbose = true
    )

    invocation.stdout shouldBe
      """test tests/passing_test.on ... ok
        |passed-out
        |
        |1 tests, 1 passed, 0 failed
        |""".stripMargin
    invocation.stderr shouldBe "passed-err\n"

  test("restores the exact global streams after an invocation throws"):
    val originalOut = System.out
    val originalErr = System.err
    val backend = FakeBackend(
      programs = Map(
        "tests/throws_test.on" -> (() =>
          System.out.print("before-throw")
          System.err.print("error-before-throw")
          throw IllegalStateException("injected runtime throw")
        )
      )
    )

    val invocation = invoke(
      ProjectTestRunner(backend),
      fixture(Vector("tests/throws_test.on"))
    )

    System.out should be theSameInstanceAs originalOut
    System.err should be theSameInstanceAs originalErr
    invocation.result.failed shouldBe 1
    invocation.result.cases.head.stdout shouldBe "before-throw"
    invocation.result.cases.head.stderr shouldBe "error-before-throw"
    invocation.result.cases.head.failure.value shouldBe
      "Could not invoke test: IllegalStateException: injected runtime throw"

  test("cleanup failures retain the primary test failure"):
    val backend = FakeBackend(
      programs = Map(
        "tests/failing_test.on" -> (() =>
          ProgramResult.Failure(
            "AssertionError: primary",
            Some(AssertionError("primary"))
          )
        )
      ),
      cleanupFailures = Set("tests/failing_test.on")
    )

    val invocation = invoke(
      ProjectTestRunner(backend),
      fixture(Vector("tests/failing_test.on"))
    )

    invocation.result.cases.head.failure.value shouldBe
      "AssertionError: primary; additionally, could not clean temporary test output: cleanup exploded"
    invocation.result.failed shouldBe 1

  test("an empty test set prints exactly the zero summary"):
    val invocation = invoke(ProjectTestRunner(FakeBackend()), fixture(Vector.empty))

    invocation.result shouldBe TestRunResult(Vector.empty)
    invocation.stdout shouldBe "0 tests, 0 passed, 0 failed\n"
    invocation.stderr shouldBe empty

  private def fixture(sources: Vector[String]): ProjectBuild =
    val root = Files.createTempDirectory("onion-project-test-runner").toRealPath()
    val manifestPath = Files.writeString(
      root.resolve("onion.toml"),
      """[package]
        |name = "demo"
        |version = "1.0.0"
        |""".stripMargin,
      UTF_8
    )
    val testSources = sources.map { relative =>
      val path = root.resolve(relative)
      Files.createDirectories(path.getParent)
      Files.writeString(path, "", UTF_8)
      ProjectSource(path, relative)
    }
    val paths = ProjectLocator.locate(root).toOption.get
    ProjectBuild(
      paths,
      ProjectManifest("demo", "1.0.0", manifestPath, Files.readAllBytes(manifestPath)),
      ProjectLayout(Vector.empty, testSources),
      BuildState(BuildFingerprint.SchemaVersion, "fingerprint", Vector("mainMain"), Vector.empty),
      cached = false
    )

  private def invoke(
    runner: ProjectTestRunner,
    build: ProjectBuild,
    verbose: Boolean = false
  ): Invocation =
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val out = PrintStream(stdout, true, UTF_8)
    val err = PrintStream(stderr, true, UTF_8)
    val result =
      try runner.run(build, out, err, verbose)
      finally
        out.close()
        err.close()
    Invocation(result, stdout.toString(UTF_8), stderr.toString(UTF_8))
