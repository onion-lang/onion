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

  // --------------------------------------------------------------- [dependencies]

  test("a manifest without a dependencies table declares no dependencies"):
    load("[package]\nname = \"hello\"\nversion = \"1.2.3\"\n").toOption.value.dependencies shouldBe empty

  test("reads dependencies as group:artifact = version"):
    val manifest = load(
      "[package]\nname = \"hello\"\nversion = \"1.2.3\"\n" +
      "\n[dependencies]\n" +
      "\"org.postgresql:postgresql\" = \"42.7.3\"\n" +
      "\"com.fasterxml.jackson.core:jackson-databind\" = \"2.17.0\"\n"
    ).toOption.value
    manifest.dependencies.map(_.render) shouldBe Seq(
      "com.fasterxml.jackson.core:jackson-databind:2.17.0",
      "org.postgresql:postgresql:42.7.3"
    )

  test("keeps dependencies in a stable order regardless of how they are written"):
    // The resolved set feeds the build fingerprint, so reordering the file must not
    // invalidate the cache.
    val one = load(
      "[package]\nname = \"h\"\nversion = \"1.0.0\"\n[dependencies]\n" +
      "\"b:b\" = \"1\"\n\"a:a\" = \"2\"\n").toOption.value
    val two = load(
      "[package]\nname = \"h\"\nversion = \"1.0.0\"\n[dependencies]\n" +
      "\"a:a\" = \"2\"\n\"b:b\" = \"1\"\n").toOption.value
    one.dependencies shouldBe two.dependencies

  test("rejects a coordinate that is not group:artifact, with a position"):
    error("[package]\nname = \"h\"\nversion = \"1.0.0\"\n[dependencies]\n\"postgresql\" = \"42.7.3\"\n") shouldBe
      "onion.toml:5:1: Invalid dependency coordinate: postgresql (expected \"group:artifact\")"
    error("[package]\nname = \"h\"\nversion = \"1.0.0\"\n[dependencies]\n\"a:b:c\" = \"1\"\n") shouldBe
      "onion.toml:5:1: Invalid dependency coordinate: a:b:c (expected \"group:artifact\")"
    error("[package]\nname = \"h\"\nversion = \"1.0.0\"\n[dependencies]\n\":b\" = \"1\"\n") shouldBe
      "onion.toml:5:1: Invalid dependency coordinate: :b (expected \"group:artifact\")"

  test("rejects a non-string or empty dependency version, with a position"):
    error("[package]\nname = \"h\"\nversion = \"1.0.0\"\n[dependencies]\n\"a:b\" = 1\n") shouldBe
      "onion.toml:5:1: dependencies.\"a:b\" must be a version string"
    error("[package]\nname = \"h\"\nversion = \"1.0.0\"\n[dependencies]\n\"a:b\" = \"\"\n") shouldBe
      "onion.toml:5:1: dependencies.\"a:b\" must be a version string"

  test("still rejects root tables other than package and dependencies"):
    error("[package]\nname = \"h\"\nversion = \"1.0.0\"\n[profile]\nx = 1\n") shouldBe
      "onion.toml:4:1: Unknown root table: profile"

  // -------------------------------------------------------------- [[repositories]]

  private val pkg = "[package]\nname = \"h\"\nversion = \"1.0.0\"\n"

  test("a manifest without repositories declares none"):
    load(pkg).toOption.value.repositories shouldBe empty

  test("reads repositories in declaration order, which is resolution precedence"):
    load(pkg +
      "[[repositories]]\nurl = \"https://one.example.com/maven\"\n" +
      "[[repositories]]\nurl = \"https://two.example.com/maven\"\n"
    ).toOption.value.repositories shouldBe Seq(
      "https://one.example.com/maven",
      "https://two.example.com/maven"
    )

  test("accepts a file URL, which is what a local repository looks like"):
    load(pkg + "[[repositories]]\nurl = \"file:///tmp/repo\"\n").toOption.value.repositories shouldBe
      Seq("file:///tmp/repo")

  test("rejects a repository URL that is relative or uses an unsupported scheme"):
    error(pkg + "[[repositories]]\nurl = \"repo/maven\"\n") shouldBe
      "onion.toml:5:1: Invalid repository URL: repo/maven"
    error(pkg + "[[repositories]]\nurl = \"ftp://example.com/maven\"\n") shouldBe
      "onion.toml:5:1: Invalid repository URL: ftp://example.com/maven"

  test("rejects a repository entry that is not a table with a url"):
    error(pkg + "[[repositories]]\nname = \"internal\"\n") shouldBe
      "onion.toml:5:1: Unknown repository key: name"
    error(pkg + "[[repositories]]\nurl = 1\n") should endWith
      "repositories must be declared as [[repositories]] tables with a url string"

  test("rejects a plain repositories array, which TOML would scope into [package]"):
    // Written after [package] a bare `repositories = [...]` becomes `package.repositories`;
    // the [[repositories]] form is immune to where in the file it appears.
    error(pkg + "repositories = [\"https://example.com\"]\n") shouldBe
      "onion.toml:4:1: Unknown package key: repositories"
