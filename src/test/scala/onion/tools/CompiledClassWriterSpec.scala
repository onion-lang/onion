package onion.tools

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import onion.compiler.CompiledClass
import org.scalatest.EitherValues.convertEitherToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CompiledClassWriterSpec extends AnyFunSuite with Matchers:
  private def binary(root: Path, className: String, bytes: Byte*): CompiledClass =
    CompiledClass(className, root.toString, bytes.toArray)

  test("writes a qualified class name below matching package directories"):
    val output = Files.createTempDirectory("onion-compiled-classes")
    val content = Seq[Byte](1, 2, 3)

    val written = CompiledClassWriter.writeAll(Seq(binary(output, "demo.App", content*))).value

    val target = output.resolve("demo/App.class")
    written shouldBe Vector(target)
    Files.readAllBytes(target).toSeq shouldBe content

  test("writes a default-package class directly below the output directory"):
    val output = Files.createTempDirectory("onion-compiled-classes")

    val written = CompiledClassWriter.writeAll(Seq(binary(output, "App", 4, 5))).value

    written shouldBe Vector(output.resolve("App.class"))
    Files.readAllBytes(output.resolve("App.class")).toSeq shouldBe Seq[Byte](4, 5)

  test("allows dollar signs in synthetic and inner class names"):
    val output = Files.createTempDirectory("onion-compiled-classes")

    val written = CompiledClassWriter.writeAll(Seq(binary(output, "demo.App$Worker$1", 6))).value

    written shouldBe Vector(output.resolve("demo/App$Worker$1.class"))

  test("rejects empty, absolute, separator-containing, traversal, and unsafe class names"):
    val invalidNames = Seq(
      "",
      "/demo.App",
      "demo/App",
      """demo\App""",
      "demo..App",
      ".demo.App",
      "demo.App.",
      "..",
      "demo.App-name",
      "démø.App"
    )

    invalidNames.foreach { className =>
      withClue(s"className=$className: ") {
        CompiledClassWriter.relativePath(className).isLeft shouldBe true
      }
    }

  test("returns written paths in deterministic input order"):
    val output = Files.createTempDirectory("onion-compiled-classes")
    val binaries = Seq(
      binary(output, "zeta.Last", 1),
      binary(output, "alpha.First", 2),
      binary(output, "Middle", 3)
    )

    CompiledClassWriter.writeAll(binaries).value shouldBe Vector(
      output.resolve("zeta/Last.class"),
      output.resolve("alpha/First.class"),
      output.resolve("Middle.class")
    )

  test("deletes files created by the current call after a later write failure"):
    val output = Files.createTempDirectory("onion-compiled-classes")
    val blockedOutput = output.resolve("not-a-directory")
    Files.write(blockedOutput, Array[Byte](9))
    val created = output.resolve("demo/Created.class")

    val result = CompiledClassWriter.writeAll(Seq(
      binary(output, "demo.Created", 1),
      binary(blockedOutput, "demo.Blocked", 2)
    ))

    result.isLeft shouldBe true
    Files.notExists(created) shouldBe true

  test("does not delete a pre-existing output after a later write failure"):
    val output = Files.createTempDirectory("onion-compiled-classes")
    val existing = output.resolve("demo/Existing.class")
    Files.createDirectories(existing.getParent)
    Files.write(existing, Array[Byte](7))
    val blockedOutput = output.resolve("not-a-directory")
    Files.write(blockedOutput, Array[Byte](9))

    val result = CompiledClassWriter.writeAll(Seq(
      binary(output, "demo.Existing", 8),
      binary(blockedOutput, "demo.Blocked", 2)
    ))

    result.isLeft shouldBe true
    Files.exists(existing) shouldBe true
    Files.readAllBytes(existing).toSeq shouldBe Seq[Byte](8)

  test("CompilerFrontend writes qualified classes with the shared package layout"):
    val output = Files.createTempDirectory("onion-compiler-frontend")
    val source = Files.createTempFile("onion-qualified-class", ".on")
    Files.writeString(source, "class `demo.App` {}\n", StandardCharsets.UTF_8)

    new CompilerFrontend().run(Array("-d", output.toString, source.toString)) shouldBe 0

    Files.exists(output.resolve("demo/App.class")) shouldBe true
    Files.notExists(output.resolve("App.class")) shouldBe true
