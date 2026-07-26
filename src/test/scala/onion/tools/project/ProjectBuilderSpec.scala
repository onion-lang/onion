package onion.tools.project

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

import scala.jdk.CollectionConverters.*
import scala.util.Using

import onion.tools.CompiledClassWriter
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProjectBuilderSpec extends AnyFunSuite with Matchers:
  private final case class Fixture(
    root: Path,
    paths: ProjectPaths,
    manifest: ProjectManifest,
    layout: ProjectLayout
  )

  private final class CountingMover(failure: Option[Int] = None) extends PathMover:
    var calls = 0

    override def move(source: Path, target: Path): Unit =
      calls += 1
      if failure.contains(calls) then throw IOException(s"injected builder move failure $calls")
      Files.move(source, target)

  private def fixture(
    sources: Map[String, String] = Map(
      "src/main.on" ->
        """module demo
          |class Helper {}
          |def main(): void {}
          |""".stripMargin
    ),
    manifestText: String =
      """[package]
        |name = "demo"
        |version = "1.0.0"
        |""".stripMargin
  ): Fixture =
    val root = Files.createTempDirectory("onion-project-builder").toRealPath()
    Files.writeString(root.resolve("onion.toml"), manifestText, UTF_8)
    sources.foreach { case (relative, contents) =>
      val path = root.resolve(relative)
      Files.createDirectories(path.getParent)
      Files.writeString(path, contents, UTF_8)
    }
    reload(root)

  private def reload(root: Path): Fixture =
    val paths = ProjectLocator.locate(root).toOption.value
    Fixture(
      root,
      paths,
      ProjectManifest.load(paths.manifest).toOption.value,
      ProjectLayout.discover(paths).toOption.value
    )

  private def errStream(): (ByteArrayOutputStream, PrintStream) =
    val bytes = ByteArrayOutputStream()
    bytes -> PrintStream(bytes, true, UTF_8)

  private def build(
    fixture: Fixture,
    builder: ProjectBuilder = ProjectBuilder()
  ): (Either[ProjectError, ProjectBuild], String) =
    val (bytes, err) = errStream()
    val result =
      try builder.build(fixture.paths, fixture.manifest, fixture.layout, err)
      finally err.close()
    result -> bytes.toString(UTF_8)

  private def temporaryDirectories(paths: ProjectPaths): Vector[Path] =
    if !Files.isDirectory(paths.onionState, NOFOLLOW_LINKS) then Vector.empty
    else
      Using.resource(Files.list(paths.onionState)) { stream =>
        stream.iterator.asScala
          .filter(path =>
            val name = path.getFileName.toString
            name.startsWith("staging-") || name.startsWith("backup-")
          )
          .toVector
      }

  private def outputSnapshot(paths: ProjectPaths): (Map[String, Vector[Byte]], Vector[Byte]) =
    val classes =
      Using.resource(Files.walk(paths.classes)) { stream =>
        stream.iterator.asScala
          .filter(Files.isRegularFile(_, NOFOLLOW_LINKS))
          .map(path =>
            paths.classes.relativize(path).iterator.asScala.mkString("/") ->
              Files.readAllBytes(path).toVector
          )
          .toMap
      }
    classes -> Files.readAllBytes(paths.buildState).toVector

  test("rejects an empty production source set before creating staging output"):
    val project = fixture(sources = Map.empty)

    val (result, diagnostics) = build(project)

    result.swap.toOption.value.message should include("production")
    diagnostics shouldBe empty
    Files.notExists(project.paths.target, NOFOLLOW_LINKS) shouldBe true

  test("rejects existing and dangling classes symlinks before cache or staging"):
    Vector(false, true).foreach { dangling =>
      val project = fixture()
      Files.createDirectory(project.paths.target)
      val external = Files.createTempDirectory("onion-project-builder-external-classes")
      val sentinel = Files.writeString(external.resolve("keep.txt"), "keep", UTF_8)
      val target =
        if dangling then external.resolve("missing")
        else external
      Files.createSymbolicLink(project.paths.classes, target)

      val (result, diagnostics) = build(project)

      withClue(s"dangling=$dangling") {
        result.swap.toOption.value.message should include("symbolic link")
        diagnostics shouldBe empty
        Files.isSymbolicLink(project.paths.classes) shouldBe true
        Files.readString(sentinel, UTF_8) shouldBe "keep"
        temporaryDirectories(project.paths) shouldBe Vector.empty
      }
    }

  test("rejects existing and dangling build-state symlinks before cache or staging"):
    Vector(false, true).foreach { dangling =>
      val project = fixture()
      Files.createDirectories(project.paths.onionState)
      val external = Files.createTempDirectory("onion-project-builder-external-state")
      val externalState = external.resolve("state.json")
      if !dangling then Files.writeString(externalState, "external-state", UTF_8)
      Files.createSymbolicLink(project.paths.buildState, externalState)

      val (result, diagnostics) = build(project)

      withClue(s"dangling=$dangling") {
        result.swap.toOption.value.message should include("symbolic link")
        diagnostics shouldBe empty
        Files.isSymbolicLink(project.paths.buildState) shouldBe true
        if !dangling then Files.readString(externalState, UTF_8) shouldBe "external-state"
        temporaryDirectories(project.paths) shouldBe Vector.empty
      }
    }

  test("compiles real sources to qualified class paths and creates deterministic state"):
    val project = fixture(
      sources = Map(
        "src/zeta.on" ->
          """module demo.z
            |class Zeta {}
            |def main(): void {}
            |""".stripMargin,
        "src/alpha.on" ->
          """module demo.a
            |class Alpha {}
            |def main(): void {}
            |""".stripMargin
      )
    )

    val (result, diagnostics) = build(project)
    val completed = result.toOption.value

    diagnostics shouldBe empty
    completed.cached shouldBe false
    completed.state.classes shouldBe completed.state.classes.distinct.sorted
    completed.state.entryPoints shouldBe Vector(
      EntryPoint("demo.a.alphaMain", "src/alpha.on", 3, 1),
      EntryPoint("demo.z.zetaMain", "src/zeta.on", 3, 1)
    )
    completed.state.classes.foreach { className =>
      val relative = CompiledClassWriter.relativePath(className).toOption.value
      Files.isRegularFile(project.paths.classes.resolve(relative), NOFOLLOW_LINKS) shouldBe true
    }
    completed.state.classes should contain allOf (
      "demo.a.Alpha",
      "demo.a.alphaMain",
      "demo.z.Zeta",
      "demo.z.zetaMain"
    )
    BuildState.load(project.paths.buildState).toOption.value shouldBe completed.state
    temporaryDirectories(project.paths) shouldBe Vector.empty

  test("returns an exact cache hit without moving or staging output"):
    val project = fixture()
    val first = build(project)._1.toOption.value
    val mover = CountingMover()
    val before = outputSnapshot(project.paths)

    val (result, diagnostics) = build(project, ProjectBuilder(mover = mover))
    val cached = result.toOption.value

    diagnostics shouldBe empty
    cached.cached shouldBe true
    cached.state shouldBe first.state
    mover.calls shouldBe 0
    outputSnapshot(project.paths) shouldBe before
    temporaryDirectories(project.paths) shouldBe Vector.empty

  test("invalidates every manifest source compiler and Java fingerprint input"):
    val project = fixture(
      sources = Map(
        "src/main.on" -> "def main(args: String[]): void {}\n",
        "src/helper.on" -> "class Helper {}\n"
      )
    )
    var previousFingerprint = build(project)._1.toOption.value.state.fingerprint

    def expectRebuild(
      mutate: => Unit,
      builder: ProjectBuilder = ProjectBuilder()
    ): ProjectBuild =
      mutate
      val refreshed = reload(project.root)
      val rebuilt = build(refreshed, builder)._1.toOption.value
      rebuilt.cached shouldBe false
      rebuilt.state.fingerprint should not be previousFingerprint
      previousFingerprint = rebuilt.state.fingerprint
      rebuilt

    val originalManifest = Files.readString(project.paths.manifest, UTF_8)
    expectRebuild(Files.writeString(
      project.paths.manifest,
      originalManifest + "# exact manifest bytes changed\n",
      UTF_8
    ))

    val main = project.root.resolve("src/main.on")
    val originalMain = Files.readString(main, UTF_8)
    expectRebuild(Files.writeString(main, originalMain + "// exact source bytes changed\n", UTF_8))

    val renamed = project.root.resolve("src/renamed.on")
    expectRebuild(Files.move(project.root.resolve("src/helper.on"), renamed))

    val added = project.root.resolve("src/added.on")
    expectRebuild(Files.writeString(added, "class Added {}\n", UTF_8))

    expectRebuild(Files.delete(added))

    expectRebuild((), ProjectBuilder(compilerVersion = "different-compiler"))
    expectRebuild(
      (),
      ProjectBuilder(
        compilerVersion = "different-compiler",
        javaFeature = Runtime.version.feature + 1
      )
    )

  test("treats missing malformed incompatible state and missing recorded classes as cache misses"):
    val project = fixture()
    val initial = build(project)._1.toOption.value

    Files.delete(project.paths.buildState)
    build(reload(project.root))._1.toOption.value.cached shouldBe false

    Files.writeString(project.paths.buildState, "{", UTF_8)
    build(reload(project.root))._1.toOption.value.cached shouldBe false

    val current = BuildState.load(project.paths.buildState).toOption.value
    Files.writeString(
      project.paths.buildState,
      BuildState.encode(current).replace(
        s""""schemaVersion": ${BuildFingerprint.SchemaVersion}""",
        s""""schemaVersion": ${BuildFingerprint.SchemaVersion + 1}"""
      ),
      UTF_8
    )
    build(reload(project.root))._1.toOption.value.cached shouldBe false

    val recorded = initial.state.classes.head
    val relative = CompiledClassWriter.relativePath(recorded).toOption.value
    Files.delete(project.paths.classes.resolve(relative))
    val rebuilt = build(reload(project.root))._1.toOption.value
    rebuilt.cached shouldBe false
    Files.isRegularFile(project.paths.classes.resolve(relative), NOFOLLOW_LINKS) shouldBe true

  test("renders compiler warnings and errors only to the injected error stream"):
    val warningProject = fixture(
      sources = Map(
        "src/main.on" ->
          """class Warning {
            |public:
            |  static def text(): String {
            |    val value: String = "hello"
            |    return "value is $value"
            |  }
            |}
            |""".stripMargin
      )
    )

    val (warningResult, warnings) = build(warningProject)

    warningResult.isRight shouldBe true
    warnings should include("W0013")
    warnings should include("warning(s)")

    val errorProject = fixture(
      sources = Map("src/main.on" -> "def main(: void {\n")
    )
    val (errorResult, errors) = build(errorProject)

    errorResult.isLeft shouldBe true
    // The `path:line:col:` prefix is structural; the message text and the closing
    // "N errors are found." trailer are localized (error.count), so asserting on
    // them passes in an English locale and fails only under -Duser.language=ja.
    errors should include("src/main.on:1:10:")
    temporaryDirectories(errorProject.paths) shouldBe Vector.empty

  test("requires successful compiler results to contain parsed source units"):
    val missing = ProjectBuilder.requireParsedUnits(None).swap.toOption.value
    val empty =
      ProjectBuilder.requireParsedUnits(Some(Seq.empty)).swap.toOption.value

    missing.message should include("Internal project error")
    missing.message should include("parsed source units")
    empty.message shouldBe missing.message

  test("a failed rebuild preserves the prior class and state pair byte for byte"):
    val project = fixture()
    build(project)._1.toOption.value
    val before = outputSnapshot(project.paths)
    Files.writeString(project.root.resolve("src/main.on"), "def main(: void {\n", UTF_8)

    val (result, diagnostics) = build(reload(project.root))

    result.isLeft shouldBe true
    // Locale-agnostic: the diagnostic's position prefix, not its localized text.
    diagnostics should include("src/main.on:1:10:")
    outputSnapshot(project.paths) shouldBe before
    temporaryDirectories(project.paths) shouldBe Vector.empty

  test("cleans staging output when promotion fails"):
    val project = fixture()
    val mover = CountingMover(failure = Some(1))

    val (result, _) = build(project, ProjectBuilder(mover = mover))

    result.isLeft shouldBe true
    Files.notExists(project.paths.classes, NOFOLLOW_LINKS) shouldBe true
    Files.notExists(project.paths.buildState, NOFOLLOW_LINKS) shouldBe true
    temporaryDirectories(project.paths) shouldBe Vector.empty

  test("build command locates a nested project and prints only exact build status"):
    val project = fixture()
    val nested = Files.createDirectories(project.root.resolve("nested/work"))
    val firstOut = ByteArrayOutputStream()
    val firstErr = ByteArrayOutputStream()

    val firstExit = ProjectCommands().build(
      nested,
      verbose = true,
      PrintStream(firstOut, true, UTF_8),
      PrintStream(firstErr, true, UTF_8)
    )

    firstExit shouldBe 0
    firstOut.toString(UTF_8) shouldBe "Built demo (2 classes)\n"
    firstErr.toString(UTF_8) shouldBe empty

    val cachedOut = ByteArrayOutputStream()
    val cachedErr = ByteArrayOutputStream()
    val cachedExit = ProjectCommands().build(
      nested,
      verbose = false,
      PrintStream(cachedOut, true, UTF_8),
      PrintStream(cachedErr, true, UTF_8)
    )

    cachedExit shouldBe 0
    cachedOut.toString(UTF_8) shouldBe "Built demo (cached)\n"
    cachedErr.toString(UTF_8) shouldBe empty

  test("build command reports project failures only to stderr"):
    val root = Files.createTempDirectory("onion-project-builder-command")
    Files.writeString(
      root.resolve("onion.toml"),
      "[package]\nname = 1\nversion = \"1.0.0\"\n",
      UTF_8
    )
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()

    val exitCode = ProjectCommands().build(
      root,
      verbose = false,
      PrintStream(stdout, true, UTF_8),
      PrintStream(stderr, true, UTF_8)
    )

    exitCode shouldBe 1
    stdout.toString(UTF_8) shouldBe empty
    stderr.toString(UTF_8) should startWith("error: ")
