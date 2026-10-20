package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression tests for user-facing diagnostic hints.
 */
class DiagnosticHintsSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String): Seq[String] = {
    val result = new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )
    result.allErrors.map(_.message)
  }

  describe("INCOMPATIBLE_TYPE nullable hint") {
    it("suggests null-handling operators when a nullable value is assigned to a non-null type") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val name: String? = null
          |    val sure: String = name
          |    return 0
          |  }
          |}
        """.stripMargin
      )
      assert(errors.exists(_.contains("null")), s"expected a null-safety hint, got: $errors")
    }

    it("does not add a hint for an ordinary type mismatch") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = "hello"
          |    return 0
          |  }
          |}
        """.stripMargin
      )
      assert(errors.forall(!_.contains("null")), s"did not expect a null hint, got: $errors")
    }
  }

  describe("VARIABLE_NOT_FOUND did-you-mean hint") {
    it("suggests a similar local variable name for a typo") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val userName = "ok"
          |    return usrName.length()
          |  }
          |}
        """.stripMargin
      )
      assert(errors.exists(_.contains("userName")), s"expected did-you-mean hint, got: $errors")
    }
  }

  describe("List/array cast hint (#2258)") {
    it("hints at toArray for a List[T] as T[] cast") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val list: List[String] = ["a", "b"]
          |    val arr = (list as String[])
          |    return arr.length
          |  }
          |}
        """.stripMargin
      )
      assert(errors.exists(_.contains("toArray(new String[0])")), s"expected a toArray hint, got: $errors")
    }

    it("hints at Colls::toList for a T[] as List[T] cast") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val arr: String[] = new String[2]
          |    val list = (arr as List[String])
          |    return list.size
          |  }
          |}
        """.stripMargin
      )
      assert(errors.exists(_.contains("Colls::toList(xs)")), s"expected a Colls::toList hint, got: $errors")
    }

    it("does not add the List/array hint for an ordinary incompatible cast") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: String = "42"
          |    return s as Int
          |  }
          |}
        """.stripMargin
      )
      assert(errors.forall(e => !e.contains("toArray") && !e.contains("Colls::toList")), s"did not expect a List/array hint, got: $errors")
    }
  }
}
