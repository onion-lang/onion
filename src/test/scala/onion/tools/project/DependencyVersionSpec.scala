package onion.tools.project

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

import onion.tools.ScriptDirectives
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * One rule for exact dependency versions, applied identically by `onion.toml`'s
 * `[dependencies]` and by a script's `//> using dep`. Each case goes through both front
 * doors, not just the shared predicate, so a front end that stopped calling it would fail
 * here. Messages are English-only tool messages; nothing here depends on the JVM locale.
 */
class DependencyVersionSpec extends AnyFunSuite with Matchers:

  private val rejected = Seq(
    // Maven ranges
    "[1.0,2.0)", "(,1.0]", "[1.0,)", "[1.5]", "(1.0,2.0)",
    // Ivy's reversed-bracket spelling and a bare comma list
    "]1.0,2.0[", "1.0,2.0",
    // dynamic revisions
    "1.+", "1.2.+", "+",
    // moving aliases, in any case
    "LATEST", "latest", "RELEASE", "release", "latest.release", "latest.integration", "latest.milestone",
    // not a version at all
    "1.0 beta", " 1.0"
  )

  private val accepted = Seq("1.0.0", "42.7.3", "1.0.0+build.5", "2.0.0-RC1", "1.0-SNAPSHOT", "1", "5.5.1")

  private def manifestAccepts(version: String): Boolean =
    val directory = Files.createTempDirectory("onion-dependency-version")
    val path = directory.resolve("onion.toml")
    Files.writeString(path,
      s"[package]\nname = \"h\"\nversion = \"1.0.0\"\n[dependencies]\n\"a:b\" = \"$version\"\n", UTF_8)
    ProjectManifest.load(path) match
      case Right(_) => true
      case Left(error) =>
        // A rejection must be the version rule, naming the dependency, at its line.
        error.message should startWith("onion.toml:5:1: dependencies.\"a:b\" must be an exact version")
        error.message should include(version)
        false

  private def directiveAccepts(version: String): Boolean =
    ScriptDirectives.parse(s"//> using dep \"a:b:$version\"\n") match
      case Right(_) => true
      case Left(error) =>
        error.message should include("must be exact")
        error.message should include(s"a:b:$version")
        false

  test("the manifest and //> using dep reject the same ranges, dynamic revisions and aliases"):
    rejected.foreach { version =>
      withClue(s"[$version] ") {
        DependencyVersion.isExact(version) shouldBe false
        manifestAccepts(version) shouldBe false
        directiveAccepts(version) shouldBe false
      }
    }

  test("the manifest and //> using dep accept the same exact versions"):
    accepted.foreach { version =>
      withClue(s"[$version] ") {
        DependencyVersion.isExact(version) shouldBe true
        manifestAccepts(version) shouldBe true
        directiveAccepts(version) shouldBe true
      }
    }
