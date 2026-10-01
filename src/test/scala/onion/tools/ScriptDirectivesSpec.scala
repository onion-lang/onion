package onion.tools

import onion.tools.project.Dependency
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `//> using` directive parsing: what is accepted, where, and that nothing in `//>` form is
 * silently ignored. Messages are English-only tool messages (like `onion.toml`'s), so the
 * substrings asserted here do not depend on the JVM locale.
 */
class ScriptDirectivesSpec extends AnyFunSuite with Matchers with EitherValues:

  private def parse(text: String) = ScriptDirectives.parse(text)

  test("no directives is an empty set"):
    parse("IO::println(\"hi\")\n").value shouldBe ScriptDirectives.Directives.empty

  test("reads quoted and bare dependencies, repositories, and trailing comments"):
    val directives = parse(
      """#!/usr/bin/env onion
        |// A script that needs POI.
        |/* license
        |   header */
        |
        |//> using dep "org.apache.poi:poi-ooxml:5.5.1"
        |//> using dep com.example:bare:1.0.0   // unquoted works too
        |//> using repository "https://example.com/maven"   // optional
        |//>using deps "a.b:c:1" "d.e:f:2"
        |//> using repository file:///tmp/repo
        |
        |tool go(): Int { return 0 }
        |""".stripMargin).value

    directives.dependencies shouldBe Seq(
      Dependency("org.apache.poi", "poi-ooxml", "5.5.1"),
      Dependency("com.example", "bare", "1.0.0"),
      Dependency("a.b", "c", "1"),
      Dependency("d.e", "f", "2")
    )
    directives.repositories shouldBe Seq("https://example.com/maven", "file:///tmp/repo")

  test("the same dependency twice at the same version is one dependency"):
    parse(
      """//> using dep "a:b:1"
        |//> using dep "a:b:1"
        |""".stripMargin).value.dependencies shouldBe Seq(Dependency("a", "b", "1"))

  test("a directive after code is an error that says to move it up"):
    val error = parse(
      """//> using dep "a:b:1"
        |val x: Int = 1
        |  //> using dep "c:d:2"
        |""".stripMargin).left.value
    error.line shouldBe 3
    error.column shouldBe 3
    error.message should include("must come before any code")

  test("code sharing a line with the end of a block comment ends the leading block"):
    val error = parse(
      """/* header */ val x: Int = 1
        |//> using dep "a:b:1"
        |""".stripMargin).left.value
    error.line shouldBe 2
    error.message should include("must come before any code")

  test("a //> line in the leading block must be a using directive"):
    val error = parse("//> usin dep \"a:b:1\"\n").left.value
    error.line shouldBe 1
    error.message should include("Unknown directive `usin`")
    parse("//>\n").left.value.message should include("Malformed directive")
    parse("//> using\n").left.value.message should include("Malformed directive")

  test("an unsupported key is named, with what is supported"):
    val error = parse("//> using scala \"3.3.7\"\n").left.value
    error.column shouldBe 11
    error.message should include("`using scala`")
    error.message should include("`using dep`")

  test("a key needs a value, and a quoted value must be terminated"):
    parse("//> using dep\n").left.value.message should include("needs at least one value")
    parse("//> using dep \"a:b:1\n").left.value.message should include("Unterminated string")

  test("a coordinate must be group:artifact:version"):
    val error = parse("//> using dep \"org.example:lib\"\n").left.value
    error.line shouldBe 1
    error.column shouldBe 15
    error.message should include("expected \"group:artifact:version\"")
    parse("//> using dep \"a:b:c:d\"\n").left.value.message should include("Invalid dependency coordinate")
    parse("//> using dep \"org::lib:1\"\n").left.value.message should include("`::`")

  test("versions must be exact: no ranges, no latest, no +"):
    Seq("[1.0,2.0)", "(,1.0]", "latest.release", "LATEST", "RELEASE", "1.+").foreach { version =>
      withClue(version) {
        parse(s"//> using dep \"a:b:$version\"\n").left.value.message should include("must be exact")
      }
    }

  test("a repository must be an absolute http, https or file URL"):
    parse("//> using repository \"ftp://example.com/maven\"\n").left.value.message should
      include("Invalid repository URL")
    parse("//> using repository \"relative/path\"\n").left.value.message should
      include("Invalid repository URL")

  test("two versions of one module is an error, not a silent pick"):
    val error = parse(
      """//> using dep "a:b:1"
        |//> using dep "a:b:2"
        |""".stripMargin).left.value
    error.line shouldBe 2
    error.message should include("a:b is declared twice")

  test("renders like a compiler diagnostic, naming the script"):
    ScriptDirectives.DirectiveError(3, 5, "boom").render("s.on") shouldBe "s.on:3:5: error: boom"
