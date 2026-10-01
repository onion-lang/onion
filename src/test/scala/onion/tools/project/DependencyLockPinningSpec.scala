package onion.tools.project

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * How a build honours `onion.lock` once it has one: by forcing the locked versions onto the
 * manifest's own resolution, and — when this machine already resolved and verified that
 * exact lock — by not resolving at all.
 *
 * The first half is a correctness bug found by dogfooding: the lock used to be honoured by
 * resolving every locked coordinate as a direct dependency. For anthropic-java that picks
 * kotlin-reflect 1.9.25 where the manifest's own resolution picks 1.9.0, so `run` rejected
 * the lock `build` had just written — and blamed the repository for it.
 */
class DependencyLockPinningSpec extends AnyFunSuite with Matchers:

  import FixtureMavenRepository.{Group, Managed}

  private val LibCoordinate = s"$Group:lib:${Managed.Lib.version}"
  private val NewerLibCoordinate = s"$Group:lib:${Managed.NewerLib.version}"

  // ------------------------------------------------------------------ the resolver

  test("flattening the locked set into roots answers a different question"):
    // The root cause, pinned down so the fixture keeps meaning what the tests below need it
    // to mean: if coursier ever stopped applying app's management to mid's request, the
    // regression tests would pass for the wrong reason.
    val repository = Seq(Managed.publish().toUri.toString)

    val fromManifest = DependencyResolver.resolve(Seq(Managed.App), repository).toOption.value
    fromManifest.coordinates should contain(LibCoordinate)

    val flattened = DependencyResolver.resolve(
      Seq(Managed.App, Managed.Mid, Managed.Lib), repository).toOption.value
    flattened.coordinates should contain(NewerLibCoordinate)

  test("forcing the locked versions reproduces the manifest's answer"):
    val repository = Seq(Managed.publish().toUri.toString)
    val fromManifest = DependencyResolver.resolve(Seq(Managed.App), repository).toOption.value

    val pinned = fromManifest.coordinates.map { coordinate =>
      val Array(group, artifact, version) = coordinate.split(":")
      Dependency(group, artifact, version)
    }
    val forced = DependencyResolver.resolve(
      Seq(Managed.App), repository, None, forced = pinned).toOption.value

    forced.coordinates shouldBe fromManifest.coordinates

  test("a forced version wins over what the graph would otherwise pick"):
    val repository = Seq(Managed.publish().toUri.toString)

    val forced = DependencyResolver.resolve(
      Seq(Managed.App), repository, None, forced = Seq(Managed.NewerLib)).toOption.value

    forced.coordinates should contain(NewerLibCoordinate)
    forced.coordinates should not contain LibCoordinate

  // ------------------------------------------------------------------ the build

  test("a lock written under version management still verifies on the next build"):
    // The dogfood repro, in miniature: build, then build again with nothing but a source
    // change. The record is removed so the second build really does resolve.
    val project = fixture(Managed.publish())
    build(project).exitCode shouldBe 0
    val lock = Files.readString(project.root.resolve("onion.lock"), UTF_8)
    lock should include(LibCoordinate)
    lock should not include NewerLibCoordinate

    Files.delete(DependencyClasspathRecord.path(project.paths))
    touchSource(project, "2")
    val second = build(project)

    withClue(s"stderr was: ${second.stderr}") { second.exitCode shouldBe 0 }
    Files.readString(project.root.resolve("onion.lock"), UTF_8) shouldBe lock

  test("the second build takes the recorded classpath and does not resolve"):
    val project = fixture(Managed.publish())
    val calls = AtomicInteger()
    val counting = countingResolver(calls)

    build(project, counting).exitCode shouldBe 0
    calls.get shouldBe 1
    Files.isRegularFile(DependencyClasspathRecord.path(project.paths)) shouldBe true

    touchSource(project, "2")
    val second = build(project, counting)

    withClue(s"stderr was: ${second.stderr}") { second.exitCode shouldBe 0 }
    calls.get shouldBe 1
    // Same classpath either way, so the build fingerprint cannot tell the paths apart.
    second.build.value.dependencies.coordinates should contain(LibCoordinate)
    second.build.value.dependencies.classpath should have size 3

  test("a build with a record is served from the build cache, not recompiled"):
    // The fast path must reproduce the resolved coordinates exactly, or every command after
    // the first would miss the build cache — slower than resolving was.
    val project = fixture(Managed.publish())
    build(project).exitCode shouldBe 0

    build(project).build.value.cached shouldBe true

  test("a lock that no longer matches the record falls back to resolving"):
    val project = fixture(Managed.publish())
    val calls = AtomicInteger()
    val counting = countingResolver(calls)
    build(project, counting).exitCode shouldBe 0

    // A lock whose comment was rewritten says the same thing and keeps the record; one
    // whose content changed does not.
    val lockFile = project.root.resolve("onion.lock")
    val original = Files.readString(lockFile, UTF_8)
    Files.writeString(lockFile, original.replace("# Generated by onion.", "# Regenerated."), UTF_8)
    touchSource(project, "2")
    build(project, counting).exitCode shouldBe 0
    calls.get shouldBe 1

    Files.writeString(lockFile, original.replace(LibCoordinate, s"$Group:lib:0.0.9"), UTF_8)
    touchSource(project, "3")
    build(project, counting)
    calls.get shouldBe 2

  test("a recorded jar whose bytes changed is caught, and blamed on the bytes"):
    val repository = Managed.publish()
    val project = fixture(repository)
    build(project).exitCode shouldBe 0

    // Stand in for a repository that re-published a version. The jar is resolved in place
    // from the file:// repository, so this is the very file the record points at: its size
    // moves, the record re-hashes it, disagrees, and the build resolves — which then fails
    // verification with the wording for bytes that really did change.
    val jar = repository.resolve(Group.replace('.', '/'))
      .resolve("lib").resolve(Managed.Lib.version).resolve(s"lib-${Managed.Lib.version}.jar")
    Files.write(jar, Files.readAllBytes(jar) ++ Array[Byte](0, 0, 0, 0))
    touchSource(project, "2")
    val result = build(project)

    result.exitCode should not be 0
    result.stderr should include(s"lib-${Managed.Lib.version}.jar")
    result.stderr should include("different bytes for the same file")
    result.stderr should include("A published version's bytes should never change")

  test("a resolution difference is not blamed on the repository"):
    val root = Files.createTempDirectory("onion-lock-wording").toRealPath()
    val a = root.resolve("lib-1.0.0.jar")
    val b = root.resolve("lib-2.0.0.jar")
    Files.writeString(a, "one", UTF_8)
    Files.writeString(b, "two", UTF_8)
    val locked = DependencyLock.Locked(
      Seq(s"$Group:app:1.0.0"), Seq.empty, Seq(LibCoordinate),
      Set(DependencyLock.LockedArtifact(a.getFileName.toString, DependencyLock.sha256(a))))

    val message = DependencyLock.verify(
      locked, ResolvedDependencies(Seq(NewerLibCoordinate), Seq(b))).left.toOption.value.message

    message should include(s"$Group:lib  locked 1.0.0  resolved 2.0.0")
    message should include("no file's bytes changed")
    message should not include "should never change"
    message should include("Delete onion.lock")

  // ------------------------------------------------------------------ plumbing

  private final case class Fixture(root: Path, paths: ProjectPaths)

  private final case class Invocation(
    exitCode: Int,
    stderr: String,
    build: Option[ProjectBuild]
  )

  private def fixture(repository: Path): Fixture =
    val root = Files.createTempDirectory("onion-lock-pinning").toRealPath()
    Files.writeString(
      root.resolve("onion.toml"),
      s"""[package]
         |name = "demo"
         |version = "1.0.0"
         |
         |${Managed.manifestStanzas(repository)}""".stripMargin,
      UTF_8)
    Files.createDirectories(root.resolve("src"))
    touchSource(Fixture(root, null), "1")
    Fixture(root, ProjectLocator.locate(root).toOption.value)

  private def touchSource(project: Fixture, tag: String): Unit =
    Files.writeString(
      project.root.resolve("src").resolve("main.on"),
      s"""import { $Group.* }
         |val v: String = Lib::version()
         |IO::println("$tag " + v)
         |""".stripMargin,
      UTF_8)

  private def countingResolver(calls: AtomicInteger): DependencyResolver.Resolution =
    (dependencies, repositories, _, forced) =>
      calls.incrementAndGet()
      DependencyResolver.resolve(dependencies, repositories, None, forced)

  private def build(
    project: Fixture,
    resolver: DependencyResolver.Resolution = (d, r, _, f) => DependencyResolver.resolve(d, r, None, f)
  ): Invocation =
    val stderr = ByteArrayOutputStream()
    val err = PrintStream(stderr, true, UTF_8)
    val result =
      try
        for
          manifest <- ProjectManifest.load(project.paths.manifest)
          layout <- ProjectLayout.discover(project.paths)
          built <- ProjectBuilder(resolver = resolver).build(project.paths, manifest, layout, err)
        yield built
      finally err.close()
    result.left.foreach(error => stderr.writeBytes(error.message.getBytes(UTF_8)))
    Invocation(if result.isRight then 0 else 1, stderr.toString(UTF_8), result.toOption)
