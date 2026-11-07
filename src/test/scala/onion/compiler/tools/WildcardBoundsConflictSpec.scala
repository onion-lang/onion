package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A generic call whose `? super U` and `? extends U` arguments leave no `U` that fits both is
 * an error, as in Java. `Collections::copy(x: List[T], o: List[Object])` inside `G[T]` used to
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
      assert(errorCodes(src).nonEmpty)
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
      assert(errorCodes(src).nonEmpty)
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
  }
}
