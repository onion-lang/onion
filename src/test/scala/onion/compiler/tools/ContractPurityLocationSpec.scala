package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompileError, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * #2605 item 4: E0106 (a contract that is not pure) points at the call that has the effect,
 * not at the `def` or `class` that owns the clause. Assertions use the code and the
 * line:column, which are the same in every locale.
 */
class ContractPurityLocationSpec extends AnyFunSpec {

  private def e0106(source: String): Seq[(Int, Int)] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, runContracts = false, contracts = ContractMode.Check)
    val errs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(source), "C.on"))) match {
      case CompilationOutcome.Failure(es) => es
      case _                              => Nil
    }
    errs.filter(_.errorCode.contains("E0106")).map(e => (e.location.line, e.location.column))
  }

  describe("E0106's location") {
    it("is the impure call in an `ensures`, not the `def`") {
      val src =
        """class K {
          |public:
          |  static def f(xs: List[Int]): Int ensures xs.add(1) { return 1 }
          |}""".stripMargin
      val at = e0106(src)
      assert(at.size == 1, at.toString)
      assert(at.head._1 == 3 && at.head._2 >= 44, at.toString)
    }

    it("is the impure call in a `requires`") {
      val src =
        """class K {
          |public:
          |  static def g(xs: List[Int]): Int requires xs.add(1) { return 1 }
          |}""".stripMargin
      val at = e0106(src)
      assert(at.size == 1, at.toString)
      assert(at.head._1 == 3 && at.head._2 >= 45, at.toString)
    }

    it("is the impure call, not the start of a longer clause") {
      val src =
        """class K {
          |public:
          |  static def g(n: Int, xs: List[Int]): Int
          |    requires n > 0 && xs.add(n) { return 1 }
          |}""".stripMargin
      val at = e0106(src)
      assert(at.size == 1, at.toString)
      assert(at.head._1 == 4 && at.head._2 >= 23, at.toString)
    }

    it("is the clause's line for a top-level function") {
      val src =
        """def f(xs: List[Int]): Int
          |  ensures xs.add(1) { return 1 }
          |println(f([]))""".stripMargin
      val at = e0106(src)
      assert(at.size == 1, at.toString)
      assert(at.head._1 == 2, at.toString)
    }

    it("is the call in a class `invariant`, not the operator before it") {
      val src =
        """class C {
          |  var n: Int
          |  invariant n > 0 && Files::exists("y")
          |public:
          |  def this { n = 1 }
          |}""".stripMargin
      val at = e0106(src)
      assert(at.size == 1, at.toString)
      assert(at.head._1 == 3 && at.head._2 >= 22, at.toString)
    }

    it("is the call in a `history`, not the operator before it") {
      val src =
        """class C {
          |  var n: Int
          |  history n >= old(n) && Files::exists("x")
          |public:
          |  def this { n = 1 }
          |}""".stripMargin
      val at = e0106(src)
      assert(at.size == 1, at.toString)
      assert(at.head._1 == 3 && at.head._2 >= 26, at.toString)
    }

    // #2847: the call is found by what it resolves to, not by its name alone.
    it("is the impure call, not an earlier pure call of the same name") {
      val src =
        """record P(v: Int) {
          |public:
          |  def add(o: Int): Int = v + o
          |}
          |def f(p: P, xs: List[Int]): Int requires p.add(1) > 0 && xs.add(1) { return 1 }
          |println(f(new P(1), []))""".stripMargin
      val at = e0106(src)
      assert(at == Seq((5, 61)), at.toString)
    }

    it("is the impure call when the pure one of the same name is nested inside it") {
      val src =
        """record P(v: Int) {
          |public:
          |  def add(o: Int): Int = v + o
          |}
          |def f(p: P, xs: List[Int]): Int requires xs.add(p.add(1)) { return 1 }
          |println(f(new P(1), []))""".stripMargin
      val at = e0106(src)
      assert(at == Seq((5, 45)), at.toString)
    }

    it("is still the first call of a name when both are impure") {
      val src =
        """def f(xs: List[Int], ys: List[Int]): Int requires xs.add(1) && ys.add(2) { return 1 }
          |println(f([], []))""".stripMargin
      val at = e0106(src)
      assert(at == Seq((1, 54)), at.toString)
    }
  }
}
