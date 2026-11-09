package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A generic method applies only if some `U` satisfies all its limits (#2431): a call whose
 * `? super U` and `? extends U` arguments leave no `U` that fits both does not select it, so
 * another overload is chosen or the call is E0005, as in Java. `Collections::copy(x: List[T], o: List[Object])` inside `G[T]` used to
 * compile, then throw a ClassCastException when `x[0]` was read back (#2431).
 */
class WildcardBoundsConflictSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("Collections::copy over a type-variable list") {
    it("rejects a List[Object] source for a List[T] destination (the issue's repro)") {
      val src =
        """import { java.util.* }
          |class G[T] {
          |public:
          |  def f(o: List[Object], x: List[T]): void { Collections::copy(x, o) }
          |}
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0005"))
    }

    it("still accepts a source that is a T") {
      val src =
        """import { java.util.* }
          |class G[T] {
          |public:
          |  def f(o: List[T], x: List[T]): void { Collections::copy(x, o) }
          |}
          |""".stripMargin
      assert(errorCodes(src).isEmpty)
    }

    it("still accepts a List[Object] destination for a List[T] source") {
      val src =
        """import { java.util.* }
          |class G[T] {
          |public:
          |  def f(o: List[T], x: List[Object]): void { Collections::copy(x, o) }
          |}
          |""".stripMargin
      assert(errorCodes(src).isEmpty)
    }

    it("rejects a List[Object] source for a List[T?] destination") {
      val src =
        """import { java.util.* }
          |class G[T] {
          |public:
          |  def c(o: List[Object], x: List[T?]): void { Collections::copy(x, o) }
          |}
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0005"))
    }

    it("still accepts a List[T] or List[T?] source for a List[T?] destination") {
      val src =
        """import { java.util.* }
          |class G[T] {
          |public:
          |  def a(o: List[T], x: List[T?]): void { Collections::copy(x, o) }
          |  def b(o: List[T?], x: List[T?]): void { Collections::copy(x, o) }
          |}
          |""".stripMargin
      assert(errorCodes(src).isEmpty)
    }
    it("rejects a List[ArrayList[Object]] source for a List[List[T]] destination (nested)") {
      val src =
        """import { java.util.* }
          |class G[T] {
          |public:
          |  def f(o: List[ArrayList[Object]], x: List[List[T]]): void { Collections::copy(x, o) }
          |}
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0005"))
    }

    it("falls back to another overload when the generic one has no U (third block, repro 2)") {
      val result = shell.run(
        """import { java.util.* }
          |class H {
          |public:
          |  static def mv[U](dst: List[? super U], src: List[? extends U]): String = "generic"
          |  static def mv(dst: Object, src: Object): String = "obj"
          |}
          |class G[T] {
          |public:
          |  def b(x: List[T], o: List[Object]): String = H::mv(x, o)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = new G[String]().b(new ArrayList[String](), new ArrayList[Object]())
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("obj") == result)
    }

    it("reads a wildcard limit as its bound: max over a List[String?] with a lambda (third block, repro 1)") {
      val result = shell.run(
        """import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs: List[String?] = ["b", "a", null]
          |    val m: String? = Collections::max(xs, (a, b) -> (a ?: "").compareTo(b ?: ""))
          |    return m ?: "none"
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("b") == result)
    }

    it("still accepts a List[List[T]] source for a List[List[T]] destination (nested)") {
      val src =
        """import { java.util.* }
          |class G[T] {
          |public:
          |  def f(o: List[List[T]], x: List[List[T]]): void { Collections::copy(x, o) }
          |}
          |""".stripMargin
      assert(errorCodes(src).isEmpty)
    }
  }
}
