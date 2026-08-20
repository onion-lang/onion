package onion.tools.project

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `onion.lock`.
 *
 * The interesting behaviour is all in the disagreements: a lock that no longer applies, a
 * lock that disagrees about bytes, a lock a future Onion wrote. The happy path — resolve,
 * write, read back — is the easy part and the least likely to break.
 */
class DependencyLockSpec extends AnyFunSuite with Matchers:

  import DependencyLock.{Locked, LockedArtifact}

  private def tempDir(): Path = Files.createTempDirectory("onion-lock").toRealPath()

  private def jar(dir: Path, name: String, content: String): Path =
    val file = dir.resolve(name)
    Files.writeString(file, content, UTF_8)
    file

  private def resolved(files: Path*): ResolvedDependencies =
    ResolvedDependencies(files.map(_.getFileName.toString).sorted, files.toSeq)

  private def manifest(dependencies: Seq[Dependency], repositories: Seq[String] = Seq.empty) =
    ProjectManifest(
      "demo", "0.1.0", Path.of("onion.toml"), Array.emptyByteArray,
      dependencies, repositories)

  private val Postgres = Dependency("org.postgresql", "postgresql", "42.7.3")

  test("round-trips through the file, so the next build reads what this one wrote"):
    val root = tempDir()
    val file = jar(root, "postgresql-42.7.3.jar", "bytes")
    val paths = projectPaths(root)

    DependencyLock.write(paths, manifest(Seq(Postgres)), resolved(file)) shouldBe Right(())
    val read = DependencyLock.read(paths).value

    read.dependencies shouldBe Seq("org.postgresql:postgresql:42.7.3")
    read.artifacts.map(_.file) shouldBe Set("postgresql-42.7.3.jar")

  test("records the whole transitive set, not just what the manifest names"):
    // This is the entire point. A direct dependency's version is written down; a transitive
    // one is chosen at resolution time and changes under the project without `onion.toml`
    // changing a byte.
    val root = tempDir()
    val direct = jar(root, "postgresql-42.7.3.jar", "a")
    val transitive = jar(root, "checker-qual-3.42.0.jar", "b")
    val paths = projectPaths(root)

    DependencyLock.write(
      paths,
      manifest(Seq(Postgres)),
      ResolvedDependencies(
        Seq("org.checkerframework:checker-qual:3.42.0", "org.postgresql:postgresql:42.7.3"),
        Seq(direct, transitive)))

    val read = DependencyLock.read(paths).value
    read.dependencies shouldBe Seq("org.postgresql:postgresql:42.7.3")
    read.coordinates should contain("org.checkerframework:checker-qual:3.42.0")

  test("stops applying when the manifest asks a different question"):
    val locked = Locked(Seq("org.postgresql:postgresql:42.7.3"), Seq.empty, Seq.empty, Set.empty)

    locked.matches(manifest(Seq(Postgres))) shouldBe true
    locked.matches(manifest(Seq(Postgres.copy(version = "42.7.4")))) shouldBe false
    locked.matches(manifest(Seq(Postgres), Seq("https://internal/maven"))) shouldBe false
    locked.matches(manifest(Seq.empty)) shouldBe false

  test("does not care in which order the manifest lists its dependencies"):
    // Reordering `[dependencies]` is not a change to what the project depends on, and
    // discarding the lock over it would re-resolve for nothing.
    val other = Dependency("com.zaxxer", "HikariCP", "5.1.0")
    val locked = Locked(
      Seq("com.zaxxer:HikariCP:5.1.0", "org.postgresql:postgresql:42.7.3"),
      Seq.empty, Seq.empty, Set.empty)

    locked.matches(manifest(Seq(Postgres, other))) shouldBe true
    locked.matches(manifest(Seq(other, Postgres))) shouldBe true

  test("fails the build when a version's bytes have changed"):
    // A published version is supposed to be immutable. A repository serving different bytes
    // is broken or hostile, and either way the build has no idea what it is compiling.
    val root = tempDir()
    val file = jar(root, "postgresql-42.7.3.jar", "the original bytes")
    val paths = projectPaths(root)
    DependencyLock.write(paths, manifest(Seq(Postgres)), resolved(file))
    val locked = DependencyLock.read(paths).value

    Files.writeString(file, "something else entirely", UTF_8)
    val result = DependencyLock.verify(locked, resolved(file))

    result.isLeft shouldBe true
    val message = result.left.toOption.get.message
    message should include("postgresql-42.7.3.jar")
    message should include("different bytes")
    // The way out has to be in the message, or the only option looks like giving up.
    message should include("delete onion.lock")

  test("reports an artifact that appeared and one that vanished, separately"):
    val root = tempDir()
    val kept = jar(root, "kept.jar", "a")
    val gone = jar(root, "gone.jar", "b")
    val locked = Locked(
      Seq.empty, Seq.empty, Seq.empty,
      Set(LockedArtifact("kept.jar", DependencyLock.sha256(kept)),
          LockedArtifact("gone.jar", DependencyLock.sha256(gone))))
    val fresh = jar(root, "new.jar", "c")

    val message = DependencyLock.verify(locked, resolved(kept, fresh)).left.toOption.get.message
    message should include("not in the lock: new.jar")
    message should include("missing: gone.jar")

  test("passes when nothing has changed"):
    val root = tempDir()
    val a = jar(root, "a.jar", "one")
    val b = jar(root, "b.jar", "two")
    val paths = projectPaths(root)
    DependencyLock.write(paths, manifest(Seq(Postgres)), resolved(a, b))

    DependencyLock.verify(DependencyLock.read(paths).value, resolved(b, a)) shouldBe Right(())

  test("compares as a set, so resolution order is not an integrity failure"):
    // coursier returns files in resolution order, which is not stable across runs. Comparing
    // positionally would fail a perfectly good build.
    val root = tempDir()
    val a = jar(root, "a.jar", "one")
    val b = jar(root, "b.jar", "two")
    val paths = projectPaths(root)
    DependencyLock.write(paths, manifest(Seq(Postgres)), resolved(a, b))
    val locked = DependencyLock.read(paths).value

    DependencyLock.verify(locked, resolved(b, a)) shouldBe Right(())

  test("ignores a lock a future Onion wrote rather than reading it wrongly"):
    val root = tempDir()
    Files.writeString(root.resolve("onion.lock"), "version = 99\n[input]\n", UTF_8)

    DependencyLock.read(projectPaths(root)) shouldBe None

  test("ignores a lock that is not valid TOML, so a corrupt file is not a wall"):
    val root = tempDir()
    Files.writeString(root.resolve("onion.lock"), "this is not toml [[[\n", UTF_8)

    DependencyLock.read(projectPaths(root)) shouldBe None

  test("has no lock to read when there is no file"):
    DependencyLock.read(projectPaths(tempDir())) shouldBe None

  test("writes a file a person can read and a resolver can re-read"):
    val root = tempDir()
    val file = jar(root, "postgresql-42.7.3.jar", "bytes")
    DependencyLock.write(projectPaths(root), manifest(Seq(Postgres)), resolved(file))

    val text = Files.readString(root.resolve("onion.lock"), UTF_8)
    // Whoever opens this file first wants to know whether to commit it.
    text should include("Commit this file")
    text should include("[[artifact]]")

  private def projectPaths(root: Path): ProjectPaths =
    ProjectPaths(
      root = root,
      manifest = root.resolve("onion.toml"),
      target = root.resolve("target"),
      classes = root.resolve("target").resolve("classes"),
      onionState = root.resolve("target").resolve("onion"),
      buildState = root.resolve("target").resolve("build.json"))

  extension [A](option: Option[A]) private def value: A = option.getOrElse(fail("expected a lock"))
