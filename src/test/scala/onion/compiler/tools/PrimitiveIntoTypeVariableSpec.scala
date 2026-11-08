package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A primitive is not a `T`. Boxing an `Int` gives an `Integer`, and an `Integer`
 * fits a `T` slot only when `T` is known to be `Integer`; inside the generic
 * code it is not. `def zero(): T = 0` and a default `n: T = 3` used to compile,
 * then threw a ClassCastException in a caller that used `T = String`
 * (#2534, #2652). A `String` there was already E0000 (#2328); Java rejects both.
 */
class PrimitiveIntoTypeVariableSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("a primitive put into a slot typed by a type parameter") {
    it("rejects a default for a class type parameter (#2652's repro)") {
      val src =
        """class A[T] { public: def this {} def id(n: T = 3): T = n }
          |val s: String? = new A[String]().id()
          |println(s?.length())
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000"))
    }
    it("rejects a default for a method type parameter") {
      assert(errorCodes("def g[U](u: U = 4): U = u\nprintln(g[String]())\n") == Seq("E0000"))
    }
    it("rejects a return, a field store and a local of type T (#2534's repro)") {
      val src =
        """class Box[T] {
          |  var t: T
          |public:
          |  def this(x: T) { t = x }
          |  def zero(): T = 0
          |  def reset(): void { t = 5 }
          |  def local(): T { val x: T = 3; return x }
          |  def get(): T = t
          |}
          |def f[T](x: T): T = 7
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000", "E0000", "E0000", "E0000"))
    }
    it("rejects a primitive for a bounded or nullable type parameter") {
      assert(errorCodes("class A[T extends Number] { public: def this {} def id(n: T = 3): T = n }\n") == Seq("E0000"))
      assert(errorCodes("class A[T] { public: def this {} def id(): T? = true }\n") == Seq("E0000"))
    }
  }

  describe("a primitive where the slot's type is known") {
    it("still boxes into a concrete type argument, Object and a nullable boxed type") {
      val src =
        """class Box[T] {
          |public:
          |  var t: T
          |  def this(x: T) { t = x }
          |  def get(): T = t
          |}
          |def id[T](x: T): T = x
          |def main(args: String[]): String {
          |  val b = new Box[JInteger](3)
          |  b.t = 4
          |  val o: Object = 5
          |  val n: JInteger? = 6
          |  val xs: List[Int] = [1]
          |  xs.add(7)
          |  return "" + b.get() + o + n + id(8) + xs
          |}
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success("4568[1, 7]"))
    }
  }
}
