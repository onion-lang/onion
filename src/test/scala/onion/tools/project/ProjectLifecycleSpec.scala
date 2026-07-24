package onion.tools.project

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

import onion.tools.OnionCli
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProjectLifecycleSpec extends AnyFunSuite with Matchers:
  private final case class Invocation(exitCode: Int, stdout: String, stderr: String)

  test("drives the complete new -> build -> cached build -> run -> test -> clean journey"):
    val parent = parentDirectory()

    invoke(Seq("new", "hello"), parent) shouldBe Invocation(
      0,
      s"Created ${parent.resolve("hello").toRealPath()}\n",
      ""
    )
    val root = parent.resolve("hello").toRealPath()

    assertBuilt(invoke(Seq("build"), root), "hello")
    invoke(Seq("build"), root) shouldBe Invocation(0, "Built hello (cached)\n", "")
    // The scaffolded program's println writes to the real process stdout, not
    // the injected stream (matching script-runner behavior), so run itself
    // prints nothing on the captured streams.
    invoke(Seq("run"), root) shouldBe Invocation(0, "", "")
    invoke(Seq("test"), root) shouldBe Invocation(
      0,
      "test tests/main_test.on ... ok\n\n1 tests, 1 passed, 0 failed\n",
      ""
    )
    invoke(Seq("clean"), root) shouldBe Invocation(0, "Cleaned target\n", "")
    Files.notExists(root.resolve("target"), NOFOLLOW_LINKS) shouldBe true

  test("every project command discovers the project from a nested working directory"):
    val parent = parentDirectory()
    invoke(Seq("new", "nested-app"), parent)
    val root = parent.resolve("nested-app").toRealPath()
    val nested = Files.createDirectories(root.resolve("work/deep"))

    assertBuilt(invoke(Seq("build"), nested), "nested-app")
    invoke(Seq("build"), nested) shouldBe Invocation(0, "Built nested-app (cached)\n", "")
    invoke(Seq("run"), nested) shouldBe Invocation(0, "", "")
    invoke(Seq("test"), nested) shouldBe Invocation(
      0,
      "test tests/main_test.on ... ok\n\n1 tests, 1 passed, 0 failed\n",
      ""
    )
    invoke(Seq("clean"), nested) shouldBe Invocation(0, "Cleaned target\n", "")
    Files.notExists(root.resolve("target"), NOFOLLOW_LINKS) shouldBe true

  test("clean is idempotent and touches only the canonical project target"):
    val parent = parentDirectory()
    invoke(Seq("new", "tidy"), parent)
    val root = parent.resolve("tidy").toRealPath()
    assertBuilt(invoke(Seq("build"), root), "tidy")
    Files.isDirectory(root.resolve("target")) shouldBe true
    val sibling = Files.writeString(root.resolve("keep.txt"), "keep", UTF_8)

    invoke(Seq("clean"), root) shouldBe Invocation(0, "Cleaned target\n", "")
    Files.notExists(root.resolve("target"), NOFOLLOW_LINKS) shouldBe true
    Files.readString(sibling, UTF_8) shouldBe "keep"

    invoke(Seq("clean"), root) shouldBe Invocation(0, "Cleaned target\n", "")
    Files.readString(sibling, UTF_8) shouldBe "keep"

  test("a symbolic-link target is rejected instead of followed, leaving the linked directory untouched"):
    val parent = parentDirectory()
    invoke(Seq("new", "linked"), parent)
    val root = parent.resolve("linked").toRealPath()
    val external = Files.createTempDirectory("onion-project-lifecycle-external")
    val sentinel = Files.writeString(external.resolve("keep.txt"), "keep", UTF_8)
    Files.createSymbolicLink(root.resolve("target"), external)

    val result = invoke(Seq("clean"), root)

    result.exitCode shouldBe 1
    result.stdout shouldBe empty
    result.stderr should include("symbolic link")
    Files.isSymbolicLink(root.resolve("target")) shouldBe true
    Files.readString(sentinel, UTF_8) shouldBe "keep"

  private def assertBuilt(result: Invocation, name: String): Unit =
    result.exitCode shouldBe 0
    result.stderr shouldBe empty
    result.stdout should fullyMatch regex raw"Built $name \(\d+ classes\)\n"

  private def parentDirectory(): Path =
    Files.createTempDirectory("onion-project-lifecycle")

  private def invoke(args: Seq[String], cwd: Path): Invocation =
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val out = PrintStream(stdout, true, UTF_8)
    val err = PrintStream(stderr, true, UTF_8)
    val exitCode =
      try OnionCli.run(args.toArray, cwd, out, err)
      finally
        out.close()
        err.close()
    Invocation(exitCode, stdout.toString(UTF_8), stderr.toString(UTF_8))
