package onion.tools

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

import onion.compiler.CompiledClass
import org.scalatest.EitherValues.convertEitherToValuable
import org.scalatest.EitherValues.convertLeftProjectionToValuable
import org.scalatest.OptionValues.convertOptionToValuable
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

  test("rejects an output root symlink without writing to its target"):
    val parent = Files.createTempDirectory("onion-compiled-classes")
    val external = Files.createTempDirectory("onion-compiled-classes-external")
    val output = parent.resolve("classes")
    Files.createSymbolicLink(output, external)

    CompiledClassWriter.writeAll(Seq(binary(output, "App", 1))).isLeft shouldBe true

    Files.notExists(external.resolve("App.class")) shouldBe true

  test("rejects a symlink package directory without writing to its target"):
    val output = Files.createTempDirectory("onion-compiled-classes")
    val external = Files.createTempDirectory("onion-compiled-classes-external")
    Files.createSymbolicLink(output.resolve("demo"), external)

    CompiledClassWriter.writeAll(Seq(binary(output, "demo.App", 1))).isLeft shouldBe true

    Files.notExists(external.resolve("App.class")) shouldBe true

  test("rejects a final class-file symlink without overwriting its target"):
    val output = Files.createTempDirectory("onion-compiled-classes")
    val packageDirectory = Files.createDirectory(output.resolve("demo"))
    val external = Files.createTempFile("onion-compiled-class-external", ".class")
    Files.write(external, Array[Byte](7))
    Files.createSymbolicLink(packageDirectory.resolve("App.class"), external)

    CompiledClassWriter.writeAll(Seq(binary(output, "demo.App", 1))).isLeft shouldBe true

    Files.readAllBytes(external).toSeq shouldBe Seq[Byte](7)

  test("rejects a dangling final class-file symlink without creating its target"):
    val output = Files.createTempDirectory("onion-compiled-classes")
    val packageDirectory = Files.createDirectory(output.resolve("demo"))
    val externalDirectory = Files.createTempDirectory("onion-compiled-classes-external")
    val missingExternal = externalDirectory.resolve("missing.class")
    Files.createSymbolicLink(packageDirectory.resolve("App.class"), missingExternal)

    CompiledClassWriter.writeAll(Seq(binary(output, "demo.App", 1))).isLeft shouldBe true

    Files.notExists(missingExternal) shouldBe true

  test("rejects a not-yet-existing output root whose nearest real ancestor is a symlink"):
    val base = Files.createTempDirectory("onion-compiled-classes")
    val external = Files.createTempDirectory("onion-compiled-classes-external")
    val link = base.resolve("linked-ancestor")
    Files.createSymbolicLink(link, external)
    val output = link.resolve("nested/classes")

    CompiledClassWriter.writeAll(Seq(binary(output, "App", 1))).isLeft shouldBe true

    Files.notExists(external.resolve("nested")) shouldBe true

  test("removes output and package directories created before a later write failure"):
    val parent = Files.createTempDirectory("onion-compiled-classes")
    val output = parent.resolve("new-output")
    val blockedOutput = parent.resolve("not-a-directory")
    Files.write(blockedOutput, Array[Byte](9))

    val result = CompiledClassWriter.writeAll(Seq(
      binary(output, "demo.deep.Created", 1),
      binary(blockedOutput, "demo.Blocked", 2)
    ))

    result.isLeft shouldBe true
    Files.notExists(output) shouldBe true

  test("reports cleanup failures while retaining the original write failure"):
    val parent = Files.createTempDirectory("onion-compiled-classes")
    val output = parent.resolve("new-output")
    val blockedOutput = parent.resolve("not-a-directory")
    Files.write(blockedOutput, Array[Byte](9))
    val untracked = output.resolve("demo/untracked.txt")
    val sabotaged = AtomicBoolean(false)
    val sabotage = new Thread(() =>
      val deadline = System.nanoTime() + 5_000_000_000L
      while Files.notExists(untracked.getParent) && System.nanoTime() < deadline do
        Thread.onSpinWait()
      if Files.exists(untracked.getParent) then
        Files.writeString(untracked, "keep")
        sabotaged.set(true)
    )
    sabotage.setDaemon(true)
    sabotage.start()

    val error = CompiledClassWriter.writeAll(Seq(
      CompiledClass("demo.Created", output.toString, Array.fill[Byte](64 * 1024 * 1024)(1)),
      binary(blockedOutput, "demo.Blocked", 3)
    )).left.value
    sabotage.join(5000)

    sabotaged.get shouldBe true
    error.message should include("cleanup incomplete")
    error.cause.value.getSuppressed.toSeq should not be empty
    Files.exists(untracked) shouldBe true

  test("CompilerFrontend writes qualified classes with the shared package layout"):
    val output = Files.createTempDirectory("onion-compiler-frontend")
    val source = Files.createTempFile("onion-qualified-class", ".on")
    Files.writeString(source, "class `demo.App` {}\n", StandardCharsets.UTF_8)

    new CompilerFrontend().run(Array("-d", output.toString, source.toString)) shouldBe 0

    Files.exists(output.resolve("demo/App.class")) shouldBe true
    Files.notExists(output.resolve("App.class")) shouldBe true

  test("CompilerFrontend returns minus one when the shared writer rejects its output"):
    val parent = Files.createTempDirectory("onion-compiler-frontend")
    val external = Files.createTempDirectory("onion-compiler-frontend-external")
    val output = parent.resolve("classes")
    Files.createSymbolicLink(output, external)
    val source = Files.createTempFile("onion-compiler-frontend-failure", ".on")
    Files.writeString(source, "class App {}\n", StandardCharsets.UTF_8)

    new CompilerFrontend().run(Array("-d", output.toString, source.toString)) shouldBe -1

    Files.notExists(external.resolve("App.class")) shouldBe true
