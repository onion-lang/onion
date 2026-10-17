package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/** W0020 (issue #2205): an extension method whose name/arity exactly match an existing
 *  member is silently shadowed by that member -- the extension is never called, with no
 *  diagnostic at either compile time or runtime. */
class ExtensionMethodShadowedWarningSpec extends AnyFunSpec {

  private def compile(body: String, level: WarningLevel = WarningLevel.On) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = level)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(body), "W.on")))
  }

  private def w20(result: onion.compiler.pipeline.CompilationResult) =
    result.diagnostics.warnings.filter(_.category.code == "W0020")

  describe("W0020 extension method shadowed") {
    it("warns when an extension method's name/arity match an existing stdlib member (List.size)") {
      val result = compile(
        """extension List { def size(): Int = 999 }
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    val xs: List[Int] = [1, 2, 3]
          |    IO::println(xs.size())
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      assert(w20(result).length == 1, s"got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("warns when an extension method matches a user-defined class's own method") {
      val result = compile(
        """class Box {
          |public:
          |  def this { }
          |  def describe(): String = "box"
          |}
          |extension Box { def describe(): String = "shadowed" }
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    IO::println(new Box().describe())
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      assert(w20(result).length == 1, s"got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn for a genuinely new extension method name") {
      val result = compile(
        """extension List { def doubledSize(): Int = this.size * 2 }
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    val xs: List[Int] = [1, 2, 3]
          |    IO::println(xs.doubledSize())
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      assert(w20(result).isEmpty, s"got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn when arity differs from the existing member") {
      val result = compile(
        """class Box {
          |public:
          |  def this { }
          |  def describe(): String = "box"
          |}
          |extension Box { def describe(suffix: String): String = "shadowed-" + suffix }
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    IO::println(new Box().describe("x"))
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      assert(w20(result).isEmpty, s"got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn for a same-name/arity extension that is a distinguishable overload (different param type)") {
      val result = compile(
        """class Box {
          |public:
          |  def this { }
          |  def describe(n: Int): String = "box-" + n
          |}
          |extension Box { def describe(s: String): String = "shadowed-" + s }
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    IO::println(new Box().describe("x"))
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
      assert(w20(result).isEmpty, s"got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("fails compilation under warnings-as-errors") {
      val result = compile(
        """class Box {
          |public:
          |  def this { }
          |  def describe(): String = "box"
          |}
          |extension Box { def describe(): String = "shadowed" }
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    IO::println(new Box().describe())
          |  }
          |}
          |""".stripMargin, WarningLevel.Error)
      assert(result.hasErrors)
    }
  }
}
