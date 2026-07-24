package onion.tools.project

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues.convertOptionToValuable

class ProjectManifestSpec extends AnyFunSuite with Matchers:
  private def load(contents: String) =
    val directory = Files.createTempDirectory("onion-project-manifest")
    val path = directory.resolve("onion.toml")
    Files.writeString(path, contents, StandardCharsets.UTF_8)
    ProjectManifest.load(path)

  private def error(contents: String): String =
    load(contents).left.toOption.value.message

  test("loads the exact minimal manifest and preserves its bytes"):
    val contents = "[package]\nname = \"hello\"\nversion = \"1.2.3\"\n"
    val manifest = load(contents).toOption.value
    manifest.name shouldBe "hello"
    manifest.version shouldBe "1.2.3"
    manifest.bytes should contain theSameElementsInOrderAs contents.getBytes(StandardCharsets.UTF_8)

  test("rejects a manifest without a package table"):
    error("name = \"hello\"\nversion = \"1.2.3\"\n") shouldBe
      "Missing required [package] table"

  test("rejects a package without a name"):
    error("[package]\nversion = \"1.2.3\"\n") shouldBe
      "Missing required package key: name"

  test("rejects a package without a version"):
    error("[package]\nname = \"hello\"\n") shouldBe
      "Missing required package key: version"

  test("rejects non-string package values"):
    error("[package]\nname = 1\nversion = \"1.2.3\"\n") shouldBe
      "onion.toml:2:1: package.name must be a string"
    error("[package]\nname = \"hello\"\nversion = 1\n") shouldBe
      "onion.toml:3:1: package.version must be a string"

  test("accepts package names matching the binding grammar"):
    Seq("hello", "Hello", "hello_world", "hello-", "hello--world").foreach { name =>
      load(s"[package]\nname = \"$name\"\nversion = \"1.2.3\"\n").toOption.value.name shouldBe name
    }

  test("rejects package names outside the binding grammar"):
    Seq("", "-hello", "1hello", "hello world", "hello!").foreach { name =>
      error(s"[package]\nname = \"$name\"\nversion = \"1.2.3\"\n") shouldBe
        s"onion.toml:2:1: Invalid package name: $name"
    }

  test("accepts SemVer 2.0 versions"):
    Seq("0.0.0", "1.2.3", "1.2.3-alpha", "1.2.3-alpha.1", "1.2.3-0.3.7", "1.2.3-x.7.z.92", "1.2.3+build.5", "1.2.3-alpha+001").foreach { version =>
      load(s"[package]\nname = \"hello\"\nversion = \"$version\"\n").toOption.value.version shouldBe version
    }

  test("rejects invalid SemVer 2.0 versions"):
    Seq("1", "1.2", "1.2.3-", "1.2.3-01", "1.2.3-001", "1.2.3+", "01.2.3", "1.02.3", "1.2.03", "v1.2.3").foreach { version =>
      error(s"[package]\nname = \"hello\"\nversion = \"$version\"\n") shouldBe
        s"onion.toml:3:1: Invalid package version: $version"
    }

  test("rejects unknown root keys, root tables, and package keys"):
    error("edition = \"2026\"\n[package]\nname = \"hello\"\nversion = \"1.2.3\"\n") shouldBe
      "onion.toml:1:1: Unknown root key: edition"
    error("[workspace]\nmembers = []\n[package]\nname = \"hello\"\nversion = \"1.2.3\"\n") shouldBe
      "onion.toml:1:1: Unknown root table: workspace"
    error("[package]\nname = \"hello\"\nversion = \"1.2.3\"\ndescription = \"hi\"\n") shouldBe
      "onion.toml:4:1: Unknown package key: description"

  test("renders duplicate keys and malformed TOML with line and column"):
    error("[package]\nname = \"hello\"\nname = \"other\"\nversion = \"1.2.3\"\n") should include ("onion.toml:3:1:")
    error("[package]\nname = \"hello\"\nversion = \"1.2.3\n") should include ("onion.toml:3:17:")

  test("requires an actual package table header"):
    error("package = { name = \"hello\", version = \"1.2.3\" }\n") shouldBe
      "onion.toml:1:1: package must be declared with [package]"
    error("package.name = \"hello\"\npackage.version = \"1.2.3\"\n") shouldBe
      "onion.toml:1:1: package must be declared with [package]"

  test("allows whitespace and comments around a package table header"):
    load("  [package] # metadata\nname = \"hello\"\nversion = \"1.2.3\"\n").toOption.value.name shouldBe "hello"

  test("reports a clean error instead of crashing on a deeply nested value"):
    // Deep enough to overflow even the project's enlarged -Xss16m test-JVM
    // stack (unlike a bare `java` process, `sbt test` runs in-process and
    // inherits that larger stack), so this stays a regression test across
    // JVM/stack-size differences rather than only reproducing under a
    // smaller default stack.
    val nesting = "[" * 200000 + "1" + "]" * 200000
    val contents = s"[package]\nname = \"hello\"\nversion = \"1.2.3\"\nx = $nesting\n"

    error(contents) shouldBe "Could not parse project manifest: too deeply nested"
