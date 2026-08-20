package onion.tools.project

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BuildFingerprintSpec extends AnyFunSuite with Matchers:
  private def bytes(value: String): Array[Byte] = value.getBytes(UTF_8)

  private val manifest = bytes("[package]\nname = \"demo\"\nversion = \"1.0.0\"\n")
  private val sources = Vector(
    "src/main.on" -> bytes("class Main {}\n"),
    "src/demo/Widget.on" -> bytes("class Widget {}\n")
  )

  private def fingerprint(
    manifestBytes: Array[Byte] = manifest,
    sourceBytes: Vector[(String, Array[Byte])] = sources,
    compilerVersion: String = "test-compiler",
    javaFeature: Int = 21,
    dependencies: Seq[String] = Seq.empty
  ): String =
    BuildFingerprint.compute(manifestBytes, sourceBytes, compilerVersion, javaFeature, dependencies)

  test("returns a lowercase SHA-256 fingerprint"):
    fingerprint() should fullyMatch regex "[0-9a-f]{64}"

  test("depends only on sorted project-relative sources rather than enumeration order"):
    fingerprint(sourceBytes = sources.reverse) shouldBe fingerprint()

  test("does not include the absolute project root"):
    def materialize(root: Path): Vector[(String, Array[Byte])] =
      sources.foreach { case (relative, content) =>
        val path = root.resolve(relative)
        Files.createDirectories(path.getParent)
        Files.write(path, content)
      }
      sources.map { case (relative, _) =>
        val path = root.resolve(relative)
        root.relativize(path).toString.replace('\\', '/') -> Files.readAllBytes(path)
      }

    val firstRoot = Files.createTempDirectory("onion-fingerprint-first")
    val secondRoot = Files.createTempDirectory("onion-fingerprint-second")

    fingerprint(sourceBytes = materialize(firstRoot)) shouldBe
      fingerprint(sourceBytes = materialize(secondRoot))

  test("changes when any declared input changes"):
    val baseline = fingerprint()
    val variants = Vector(
      fingerprint(manifestBytes = bytes("[package]\nname = \"other\"\nversion = \"1.0.0\"\n")),
      fingerprint(compilerVersion = "other-compiler"),
      fingerprint(javaFeature = 22),
      fingerprint(sourceBytes = sources :+ ("src/extra.on" -> bytes("class Extra {}\n"))),
      fingerprint(sourceBytes = sources.take(1)),
      fingerprint(sourceBytes = sources.updated(0, "src/renamed.on" -> sources(0)._2)),
      fingerprint(sourceBytes = sources.updated(0, sources(0)._1 -> bytes("class Changed {}\n"))),
      fingerprint(dependencies = Seq("org.postgresql:postgresql:42.7.3"))
    )

    variants.foreach(_ should not be baseline)

  test("changes when a transitive dependency version changes without the manifest changing"):
    // The manifest pins direct dependencies only. A transitive can move underneath it, and
    // if that did not reach the fingerprint the cache would serve classes compiled against
    // the old classpath as current.
    fingerprint(dependencies = Seq("a:b:1.0.0", "c:d:2.0.0")) should not be
      fingerprint(dependencies = Seq("a:b:1.0.0", "c:d:2.0.1"))

  test("does not depend on the order dependencies are listed in"):
    fingerprint(dependencies = Seq("c:d:2.0.0", "a:b:1.0.0")) shouldBe
      fingerprint(dependencies = Seq("a:b:1.0.0", "c:d:2.0.0"))

  test("length-delimits source paths and contents to avoid concatenation ambiguity"):
    fingerprint(sourceBytes = Vector("ab" -> bytes("c"))) should not be
      fingerprint(sourceBytes = Vector("a" -> bytes("bc")))
