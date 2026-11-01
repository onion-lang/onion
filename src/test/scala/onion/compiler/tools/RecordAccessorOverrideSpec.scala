package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompileError, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell
import java.io.StringReader

/** A record accessor must be return-compatible with the inherited method it overrides (#2372). */
class RecordAccessorOverrideSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src.stripMargin), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  describe("A primitive return type must match the inherited one exactly (E0104)") {
    it("rejects Int over Integer in an enum case, at the component") {
      val errs = errors(
        """
          |enum Shape {
          |  case Circle(area: Int)
          |public:
          |  def area(): Integer = 0
          |}
          |val s: Shape = new Circle(3)
          |println(s.area())
          |""")
      assert(errs.flatMap(_.errorCode) == Seq("E0104"))
      assert(errs.head.location.line == 3)
      assert(errs.head.message.contains("area"))
    }

    it("rejects Int over an interface's Integer, which threw AbstractMethodError") {
      val errs = errors(
        """
          |interface HasN { def n(): Integer }
          |record R(n: Int) conforms HasN
          |val h: HasN = new R(4)
          |println(h.n())
          |""")
      assert(errs.flatMap(_.errorCode) == Seq("E0104"))
      assert(errs.head.location.line == 3)
    }

    it("rejects Int over Double on a record component and on a class method") {
      val record = errors(
        """
          |interface Shape { def area(): Double }
          |record Circle(r: Int, area: Int) conforms Shape
          |""")
      assert(record.flatMap(_.errorCode) == Seq("E0104"))
      assert(record.head.location.line == 3)
      val klass = errors(
        """
          |class Base {
          |public:
          |  def this {}
          |  def size(): Long = 1L
          |}
          |class D extends Base {
          |public:
          |  def this {}
          |  def size(): Int = 2
          |}
          |""")
      assert(klass.flatMap(_.errorCode) == Seq("E0104"))
      assert(klass.head.location.line == 10)
    }

    it("still accepts a primitive accessor for a generic contract, which is bridged") {
      val result = shell.run(
        """
          |interface G[T] { def get(): T }
          |record R(get: Int) conforms G[Integer]
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val g: G[Integer] = new R(5)
          |    return "" + g.get()
          |  }
          |}
          |""".stripMargin,
        "GenericAccessor.on",
        Array()
      )
      assert(Shell.Success("5") == result)
    }
  }

  describe("Record accessor vs inherited method") {

    it("rejects a component whose accessor returns a different type than the interface method") {
      val result = shell.run(
        """
          |interface Shape { def area(): Double }
          |record Circle(area: Int) conforms Shape
          |IO::println(new Circle(3).area())
          |""".stripMargin,
        "RecordAccessorClash.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("rejects the same clash in an ADT enum case") {
      val result = shell.run(
        """
          |enum Shape {
          |  case Circle(area: Int)
          |public:
          |  def area(): Double = 0.0
          |}
          |val s: Shape = new Circle(3)
          |IO::println(s.area())
          |""".stripMargin,
        "EnumCaseAccessorClash.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("accepts a component whose accessor matches the interface method") {
      val result = shell.run(
        """
          |interface Shape { def area(): Int }
          |record Circle(area: Int) conforms Shape
          |IO::println(new Circle(3).area())
          |""".stripMargin,
        "RecordAccessorOk.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }
  }
}
