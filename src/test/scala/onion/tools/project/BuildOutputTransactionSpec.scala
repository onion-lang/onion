package onion.tools.project

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BuildOutputTransactionSpec extends AnyFunSuite with Matchers:
  private final class FailingMover(failures: Set[Int] = Set.empty) extends PathMover:
    var calls = 0

    override def move(source: Path, target: Path): Unit =
      calls += 1
      if failures.contains(calls) then throw IOException(s"injected move failure $calls")
      Files.move(source, target)

  private def paths(): ProjectPaths =
    val root = Files.createTempDirectory("onion-build-transaction").toRealPath()
    val target = Files.createDirectory(root.resolve("target")).toRealPath()
    val onionState = Files.createDirectory(target.resolve(".onion")).toRealPath()
    ProjectPaths(
      root,
      root.resolve("onion.toml"),
      target,
      target.resolve("classes"),
      onionState,
      onionState.resolve("build-state.json")
    )

  private def staging(paths: ProjectPaths, label: String): Path =
    val root = Files.createTempDirectory(paths.onionState, s"staging-$label-").toRealPath()
    writeClass(root.resolve("classes"), "demo/Current.class", s"$label-current")
    Files.writeString(root.resolve("build-state.json"), s"$label-state", UTF_8)
    root

  private def writeClass(classes: Path, relative: String, contents: String): Unit =
    val path = classes.resolve(relative)
    Files.createDirectories(path.getParent)
    Files.writeString(path, contents, UTF_8)

  private def installOldOutput(paths: ProjectPaths): Unit =
    writeClass(paths.classes, "demo/Old.class", "old-class")
    writeClass(paths.classes, "demo/Stale.class", "stale-class")
    Files.writeString(paths.buildState, "old-state", UTF_8)

  private def classSnapshot(classes: Path): Map[String, String] =
    if !Files.exists(classes, NOFOLLOW_LINKS) then Map.empty
    else
      Using.resource(Files.walk(classes)) { stream =>
        stream.iterator.asScala
          .filter(Files.isRegularFile(_, NOFOLLOW_LINKS))
          .map(path =>
            classes.relativize(path).iterator.asScala.mkString("/") ->
              Files.readString(path, UTF_8)
          )
          .toMap
      }

  private def temporaryDirectories(paths: ProjectPaths): Vector[Path] =
    Using.resource(Files.list(paths.onionState)) { stream =>
      stream.iterator.asScala
        .filter(path =>
          val name = path.getFileName.toString
          name.startsWith("staging-") || name.startsWith("backup-")
        )
        .toVector
    }

  test("promotes the first staged classes and state"):
    val project = paths()
    val staged = staging(project, "first")

    BuildOutputTransaction.promote(project, staged, FailingMover()).toOption.value

    classSnapshot(project.classes) shouldBe Map("demo/Current.class" -> "first-current")
    Files.readString(project.buildState, UTF_8) shouldBe "first-state"
    temporaryDirectories(project) shouldBe Vector.empty

  test("replaces a prior classes and state pair and removes stale classes"):
    val project = paths()
    installOldOutput(project)
    val staged = staging(project, "replacement")

    BuildOutputTransaction.promote(project, staged, FailingMover()).toOption.value

    classSnapshot(project.classes) shouldBe
      Map("demo/Current.class" -> "replacement-current")
    Files.readString(project.buildState, UTF_8) shouldBe "replacement-state"
    Files.notExists(project.classes.resolve("demo/Stale.class"), NOFOLLOW_LINKS) shouldBe true
    temporaryDirectories(project) shouldBe Vector.empty

  Vector(
    1 -> "old classes",
    2 -> "old state",
    3 -> "new classes",
    4 -> "new state"
  ).foreach { case (failure, description) =>
    test(s"restores the exact prior pair when moving $description fails"):
      val project = paths()
      installOldOutput(project)
      val expectedClasses = classSnapshot(project.classes)
      val expectedState = Files.readAllBytes(project.buildState).toVector
      val staged = staging(project, s"failure-$failure")

      val result =
        BuildOutputTransaction.promote(project, staged, FailingMover(Set(failure)))

      result.isLeft shouldBe true
      result.swap.toOption.value.cause.value.getMessage should include(s"failure $failure")
      classSnapshot(project.classes) shouldBe expectedClasses
      Files.readAllBytes(project.buildState).toVector shouldBe expectedState
      temporaryDirectories(project) shouldBe Vector.empty
  }

  test("keeps the primary move error and continues restoration after rollback fails"):
    val project = paths()
    installOldOutput(project)
    val staged = staging(project, "rollback-failure")
    val mover = FailingMover(Set(4, 5))

    val error =
      BuildOutputTransaction.promote(project, staged, mover).swap.toOption.value

    mover.calls shouldBe 6
    error.cause.value.getMessage should include("failure 4")
    error.cause.value.getSuppressed.toVector.map(_.getMessage) shouldBe
      Vector("injected move failure 5")
    classSnapshot(project.classes) should contain("demo/Old.class" -> "old-class")
    val backups = temporaryDirectories(project).filter(_.getFileName.toString.startsWith("backup-"))
    backups should have size 1
    Files.readString(backups.head.resolve("build-state.json"), UTF_8) shouldBe "old-state"

  test("tree deletion removes a directory symlink without traversing it"):
    val project = paths()
    val staged = Files.createTempDirectory(project.onionState, "staging-links-").toRealPath()
    val outside = Files.createTempDirectory("onion-build-transaction-outside")
    val sentinel = Files.writeString(outside.resolve("keep.txt"), "keep", UTF_8)
    Files.createSymbolicLink(staged.resolve("outside"), outside)

    FileTree.delete(staged, project.target)

    Files.notExists(staged, NOFOLLOW_LINKS) shouldBe true
    Files.readString(sentinel, UTF_8) shouldBe "keep"

  test("tree deletion rejects its trusted boundary and paths outside it"):
    val project = paths()
    val outside = Files.createTempDirectory("onion-build-transaction-untrusted")

    intercept[IOException](FileTree.delete(project.target, project.target))
    intercept[IOException](FileTree.delete(outside, project.target))

    Files.isDirectory(project.target, NOFOLLOW_LINKS) shouldBe true
    Files.isDirectory(outside, NOFOLLOW_LINKS) shouldBe true
