package onion.tools.project

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

import scala.util.control.NonFatal

import onion.compiler.CompilerConfig
import onion.compiler.OnionCompiler
import onion.compiler.StreamInputSource
import onion.compiler.WarningLevel
import onion.compiler.diagnostics.DiagnosticRenderer
import onion.tools.CompiledClassWriter

final case class TestCaseResult(
  source: String,
  passed: Boolean,
  stdout: String,
  stderr: String,
  failure: Option[String]
)

final case class TestRunResult(cases: Vector[TestCaseResult]):
  def passed: Int = cases.count(_.passed)
  def failed: Int = cases.size - passed
  def successful: Boolean = failed == 0

private[project] final case class ProjectTestWorkspace(
  root: Path,
  classes: Path
)

private[project] final case class PreparedProjectTest(
  className: String,
  diagnostics: String
)

private[project] final case class ProjectTestFailure(
  message: String,
  stderr: String = ""
)

private[project] trait ProjectTestBackend:
  def createWorkspace(
    build: ProjectBuild,
    source: ProjectSource
  ): ProjectTestWorkspace

  def compile(
    build: ProjectBuild,
    source: ProjectSource,
    workspace: ProjectTestWorkspace
  ): Either[ProjectTestFailure, PreparedProjectTest]

  def invoke(
    build: ProjectBuild,
    workspace: ProjectTestWorkspace,
    prepared: PreparedProjectTest
  ): ProgramResult

  def cleanup(
    build: ProjectBuild,
    source: ProjectSource,
    workspace: ProjectTestWorkspace
  ): Unit

private[project] object ProjectTestBackend:
  val system: ProjectTestBackend = SystemProjectTestBackend

private object SystemProjectTestBackend extends ProjectTestBackend:
  override def createWorkspace(
    build: ProjectBuild,
    source: ProjectSource
  ): ProjectTestWorkspace =
    validateOutputTopology(build.paths)
    var root: Option[Path] = None
    try
      val created = ProjectTemporaryDirectory.create(
        build.paths.onionState,
        "test-",
        build.paths.target
      )
      root = Some(created)
      val classes = Files.createDirectory(created.resolve("classes"))
      requireRealDirectory(classes, "temporary test classes")
      ProjectTestWorkspace(created, classes)
    catch
      case NonFatal(error) =>
        root.foreach(path =>
          try FileTree.delete(path, build.paths.target)
          catch case NonFatal(cleanup) => error.addSuppressed(cleanup)
        )
        throw error

  override def compile(
    build: ProjectBuild,
    source: ProjectSource,
    workspace: ProjectTestWorkspace
  ): Either[ProjectTestFailure, PreparedProjectTest] =
    val bytes = Files.readAllBytes(source.path)
    val config = CompilerConfig(
      // A test compiles against the project's own classes *and* its dependencies;
      // without the latter a test cannot mention any type it depends on.
      classPath = build.paths.classes.toString +: build.dependencies.classpath.map(_.toString),
      superClass = "",
      encoding = UTF_8.name(),
      outputDirectory = workspace.classes.toString,
      maxErrorReports = 10,
      verbose = false,
      warningLevel = WarningLevel.On,
      checkLaws = true
    )
    val input = StreamInputSource(
      () => InputStreamReader(ByteArrayInputStream(bytes), UTF_8),
      source.path.toString
    )
    val diagnosticBytes = ByteArrayOutputStream()
    val diagnosticStream = PrintStream(diagnosticBytes, true, UTF_8)
    val compiled =
      try
        val result = OnionCompiler(config).compileDetailed(Seq(input))
        DiagnosticRenderer.printDiagnostics(result.diagnostics, diagnosticStream)
        result
      finally diagnosticStream.close()
    val diagnostics =
      diagnosticBytes.toString(UTF_8).replace(source.path.toString, source.relative)

    if compiled.hasErrors then
      Left(ProjectTestFailure("Test compilation failed", diagnostics))
    else
      for
        units <- ProjectBuilder.requireParsedUnits(compiled.debugArtifacts.parsedUnits)
          .left.map(error => ProjectTestFailure(error.message, diagnostics))
        unit <-
          if units.size == 1 then Right(units.head)
          else Left(ProjectTestFailure(
            s"Internal project error: one test source produced ${units.size} parsed units",
            diagnostics
          ))
        entry <- EntryPointDiscovery.testEntry(build.paths.root, unit)
          .left.map(error => ProjectTestFailure(error.message, diagnostics))
        _ <-
          if compiled.classes.nonEmpty then Right(())
          else Left(ProjectTestFailure(
            "Internal project error: successful test compilation returned no classes",
            diagnostics
          ))
        _ <- CompiledClassWriter.writeAll(compiled.classes)
          .left.map(error => ProjectTestFailure(error.message, diagnostics))
      yield PreparedProjectTest(entry.className, diagnostics)

  override def invoke(
    build: ProjectBuild,
    workspace: ProjectTestWorkspace,
    prepared: PreparedProjectTest
  ): ProgramResult =
    ProjectClassRunner.run(
      Vector(workspace.classes, build.paths.classes) ++ build.dependencies.classpath,
      prepared.className,
      Array.empty
    )

  override def cleanup(
    build: ProjectBuild,
    source: ProjectSource,
    workspace: ProjectTestWorkspace
  ): Unit =
    FileTree.delete(workspace.root, build.paths.target)

  private def validateOutputTopology(paths: ProjectPaths): Unit =
    val root = paths.root.toAbsolutePath.normalize
    if root.toRealPath() != root then
      throw IOException(s"Project root is not canonical: $root")
    val target = paths.target.toAbsolutePath.normalize
    val onionState = paths.onionState.toAbsolutePath.normalize
    val classes = paths.classes.toAbsolutePath.normalize
    if target != root.resolve("target") ||
      onionState != target.resolve(".onion") ||
      classes != target.resolve("classes")
    then
      throw IOException("Project test output paths do not belong to the canonical project root")
    requireRealDirectory(target, "project target")
    requireRealDirectory(onionState, "project state directory")
    requireRealDirectory(classes, "project classes")

  private def requireRealDirectory(path: Path, description: String): Unit =
    if Files.isSymbolicLink(path) ||
      !Files.isDirectory(path, NOFOLLOW_LINKS)
    then
      throw IOException(s"$description is not a real directory or is symbolic: $path")
    if path.toRealPath() != path.toAbsolutePath.normalize then
      throw IOException(s"$description is not canonical: $path")

final class ProjectTestRunner private[project] (backend: ProjectTestBackend):
  private final case class CompletedTestCase(
    result: TestCaseResult,
    diagnostics: String
  )

  def this() = this(ProjectTestBackend.system)

  def run(
    build: ProjectBuild,
    out: PrintStream,
    err: PrintStream,
    verbose: Boolean
  ): TestRunResult =
    val results = Vector.newBuilder[TestCaseResult]
    val sources = build.layout.testSources.sortBy(_.relative)

    sources.foreach { source =>
      val completed = runCase(build, source)
      results += completed.result
      renderCase(completed, out, err, verbose)
    }

    val completed = TestRunResult(results.result())
    if completed.cases.nonEmpty then out.println()
    out.println(s"${completed.cases.size} tests, ${completed.passed} passed, ${completed.failed} failed")
    completed

  private def runCase(
    build: ProjectBuild,
    source: ProjectSource
  ): CompletedTestCase =
    var workspace: Option[ProjectTestWorkspace] = None
    var completed: CompletedTestCase = null
    var escaping: Throwable = null
    var cleanupFailure: Option[Throwable] = None
    try
      completed =
        val created = backend.createWorkspace(build, source)
        workspace = Some(created)
        backend.compile(build, source, created) match
          case Left(failure) =>
            CompletedTestCase(
              failed(source, "", "", failure.message),
              failure.stderr
            )
          case Right(prepared) =>
            invoke(build, source, created, prepared)
    catch
      case error: Throwable if NonFatal(error) =>
        completed = CompletedTestCase(
          failed(
            source,
            "",
            "",
            s"Could not prepare test: ${describe(error)}"
          ),
          ""
        )
      case error: Throwable =>
        escaping = error
        throw error
    finally
      workspace.foreach { created =>
        try backend.cleanup(build, source, created)
        catch
          case error: Throwable =>
            if escaping != null then escaping.addSuppressed(error)
            else cleanupFailure = Some(error)
      }

    cleanupFailure match
      case None => completed
      case Some(error) =>
        val cleanup = s"could not clean temporary test output: ${describeMessage(error)}"
        completed.copy(
          result = completed.result.copy(
            passed = false,
            failure = Some(completed.result.failure match
              case Some(primary) => s"$primary; additionally, $cleanup"
              case None => cleanup.capitalize
            )
          )
        )

  private def invoke(
    build: ProjectBuild,
    source: ProjectSource,
    workspace: ProjectTestWorkspace,
    prepared: PreparedProjectTest
  ): CompletedTestCase =
    val captured = RuntimeOutputCapture.capture {
      backend.invoke(build, workspace, prepared)
    }
    val result =
      captured.result match
        case Left(error) =>
          failed(
            source,
            captured.stdout,
            captured.stderr,
            s"Could not invoke test: ${describe(error)}"
          )
        case Right(ProgramResult.Failure(message, _)) =>
          failed(source, captured.stdout, captured.stderr, message)
        case Right(ProgramResult.Success(value))
            if ProjectCommands.programExitCode(value) != 0 =>
          failed(
            source,
            captured.stdout,
            captured.stderr,
            s"Test returned nonzero numeric result: ${String.valueOf(value)}"
          )
        case Right(ProgramResult.Success(_)) =>
          TestCaseResult(
            source.relative,
            passed = true,
            captured.stdout,
            captured.stderr,
            None
          )
    val withLifecycleFailures = captured.lifecycleFailures.foldLeft(result) {
      (current, lifecycle) =>
        val message = s"could not restore captured test streams: ${describe(lifecycle)}"
        current.copy(
          passed = false,
          failure = Some(current.failure match
            case Some(primary) => s"$primary; additionally, $message"
            case None => message.capitalize
          )
        )
    }
    CompletedTestCase(withLifecycleFailures, prepared.diagnostics)

  private def failed(
    source: ProjectSource,
    stdout: String,
    stderr: String,
    message: String
  ): TestCaseResult =
    TestCaseResult(source.relative, passed = false, stdout, stderr, Some(message))

  private def renderCase(
    completed: CompletedTestCase,
    out: PrintStream,
    err: PrintStream,
    verbose: Boolean
  ): Unit =
    val result = completed.result
    val status = if result.passed then "ok" else "FAILED"
    out.println(s"test ${result.source} ... $status")
    printCaptured(err, completed.diagnostics)
    if verbose || !result.passed then
      printCaptured(out, result.stdout)
      printCaptured(err, result.stderr)
    result.failure.foreach(message =>
      err.println(s"error: ${result.source}: $message")
    )

  private def printCaptured(stream: PrintStream, value: String): Unit =
    if value.nonEmpty then
      stream.print(value)
      if !value.endsWith("\n") && !value.endsWith("\r") then stream.println()

  private def describe(error: Throwable): String =
    val name =
      Option(error.getClass.getSimpleName)
        .filter(_.nonEmpty)
        .getOrElse(error.getClass.getName)
    s"$name: ${describeMessage(error)}"

  private def describeMessage(error: Throwable): String =
    Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)

private object RuntimeOutputCapture:
  final case class Captured[A](
    result: Either[Throwable, A],
    stdout: String,
    stderr: String,
    lifecycleFailures: Vector[Throwable]
  )

  private val lock = Object()

  def capture[A](body: => A): Captured[A] =
    lock.synchronized {
      val originalOut = System.out
      val originalErr = System.err
      val stdout = ByteArrayOutputStream()
      val stderr = ByteArrayOutputStream()
      val capturedOut = PrintStream(stdout, true, UTF_8)
      val capturedErr = PrintStream(stderr, true, UTF_8)
      var result: Either[Throwable, A] = Left(IOException("Test invocation did not complete"))
      val lifecycleFailures = Vector.newBuilder[Throwable]

      try
        System.setOut(capturedOut)
        System.setErr(capturedErr)
        try result = Right(body)
        catch case error: Throwable => result = Left(error)
      catch
        case error: Throwable => result = Left(error)
      finally
        capturedOut.flush()
        capturedErr.flush()
        try System.setOut(originalOut)
        catch
          case error: Throwable => lifecycleFailures += error
        try System.setErr(originalErr)
        catch
          case error: Throwable => lifecycleFailures += error

      val captured = Captured(
        result,
        stdout.toString(UTF_8),
        stderr.toString(UTF_8),
        lifecycleFailures.result()
      )
      capturedOut.close()
      capturedErr.close()
      captured
    }
