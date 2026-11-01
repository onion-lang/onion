package onion.compiler

import onion.compiler.parser.{JJOnionParser, OnionParser}

import java.io.StringReader
import org.scalatest.funspec.AnyFunSpec

/**
 * Contract clauses on a method (`requires`, `ensures`; RFC #2557) must parse to the same AST
 * in the handwritten fast-path parser and in the JavaCC parser.
 *
 * `FastPathParserParitySpec` walks `run/` only, and it skips a file the fast path rejects,
 * so new syntax that appears in no sample is not covered there at all. These programs are
 * parsed by both, and the fast path must accept them.
 */
class ContractSyntaxParitySpec extends AnyFunSpec {

  private def fast(code: String): AST.CompilationUnit =
    try OnionParser.parse(code)
    catch { case f: OnionParser.Fail => fail(s"the fast path rejected this program: $f\n$code") }

  private def jj(code: String): AST.CompilationUnit =
    new JJOnionParser(new StringReader(code)).unit()

  private def methods(node: Any): List[AST.MethodDeclaration] = node match {
    case m: AST.MethodDeclaration => m :: methods(m.block)
    case p: Product               => p.productIterator.toList.flatMap(methods)
    case it: Iterable[?]          => it.toList.flatMap(methods)
    case _                        => Nil
  }

  /** Class, record and interface declarations, with their class-level clauses. */
  private case class Clauses(name: String, invariants: Int, histories: Int)

  private def clausesOf(node: Any): List[Clauses] = node match {
    case c: AST.ClassDeclaration     => Clauses(c.name, c.invariants.size, c.histories.size) :: Nil
    case r: AST.RecordDeclaration    => Clauses(r.name, r.invariants.size, r.histories.size) :: Nil
    case i: AST.InterfaceDeclaration => Clauses(i.name, i.invariants.size, i.histories.size) :: Nil
    case p: Product                  => p.productIterator.toList.flatMap(clausesOf)
    case it: Iterable[?]             => it.toList.flatMap(clausesOf)
    case _                           => Nil
  }

  private def bothClasses(code: String): List[Clauses] = {
    val f = fast(code)
    assert(f == jj(code), "the fast-path AST differs from the JavaCC AST")
    clausesOf(f)
  }

  private def both(code: String): List[AST.MethodDeclaration] = {
    val f = fast(code)
    assert(f == jj(code), "the fast-path AST differs from the JavaCC AST")
    methods(f)
  }

  describe("clauses after a signature") {
    it("reads requires and ensures on one line") {
      val ms = both(
        """class A {
          |public:
          |  def f(x: Int): Int requires x > 0 ensures result > x { return x + 1 }
          |}""".stripMargin)
      val f = ms.find(_.name == "f").get
      assert(f.requires.size == 1)
      assert(f.ensures.size == 1)
    }

    it("reads one clause per line, with the body brace on its own line") {
      val ms = both(
        """class A {
          |public:
          |  def f(x: Int): Int
          |    requires x > 0
          |    requires x < 100
          |    ensures result > x
          |  {
          |    return x + 1
          |  }
          |}""".stripMargin)
      val f = ms.find(_.name == "f").get
      assert(f.requires.size == 2)
      assert(f.ensures.size == 1)
      assert(f.block != null)
    }

    it("reads clauses before an expression body") {
      val ms = both(
        """class A {
          |public:
          |  def f(x: Int): Int
          |    requires x > 0
          |    = x + 1
          |}""".stripMargin)
      val f = ms.find(_.name == "f").get
      assert(f.requires.size == 1)
      assert(f.block != null)
    }

    it("reads clauses on an interface method that has no body") {
      val ms = both(
        """interface I {
          |  def f(x: Int): Int requires x > 0 ensures result >= 0
          |  def g(): Int
          |}""".stripMargin)
      val f = ms.find(_.name == "f").get
      val g = ms.find(_.name == "g").get
      assert(f.requires.size == 1 && f.ensures.size == 1)
      assert(f.block == null)
      assert(g.requires.isEmpty && g.ensures.isEmpty)
    }

    it("does not read a trailing lambda out of the last clause expression") {
      val ms = both(
        """class A {
          |  var n: Int
          |public:
          |  def get(): Int ensures result == n { return n }
          |}""".stripMargin)
      val get = ms.find(_.name == "get").get
      assert(get.ensures.size == 1)
      assert(get.block != null)
    }

    it("keeps the order of the clauses") {
      val ms = both(
        """class A {
          |public:
          |  def f(x: Int, y: Int): Int requires x > 0 requires y > 0 ensures result > 0 ensures result > x { return x + y }
          |}""".stripMargin)
      val f = ms.find(_.name == "f").get
      assert(f.requires.size == 2 && f.ensures.size == 2)
    }
  }

  describe("invariant and history in a type body") {
    it("reads them in the default section and in an access section") {
      val cs = bothClasses(
        """class Counter {
          |  var n: Int
          |  invariant n >= 0
          |public:
          |  history n >= old(n)
          |  def get(): Int { return n }
          |}""".stripMargin)
      assert(cs == List(Clauses("Counter", 1, 1)))
    }

    it("reads several of each, in order") {
      val cs = bothClasses(
        """class A {
          |  var n: Int
          |  var m: Int
          |  invariant n >= 0
          |  invariant m >= 0
          |  history n >= old(n)
          |  history m >= old(m)
          |}""".stripMargin)
      assert(cs == List(Clauses("A", 2, 2)))
    }

    it("reads them in a record body") {
      val cs = bothClasses(
        """record Range(lo: Int, hi: Int) {
          |  invariant lo <= hi
          |}""".stripMargin)
      assert(cs == List(Clauses("Range", 1, 0)))
    }

    it("reads a history in an interface") {
      val cs = bothClasses(
        """interface Point {
          |  def x(): Int
          |  history x() == old(x())
          |}""".stripMargin)
      assert(cs == List(Clauses("Point", 0, 1)))
    }

    it("lets a field and a method be called invariant and history") {
      val cs = bothClasses(
        """class A {
          |  var invariant: Int
          |public:
          |  def history(): Int { return invariant }
          |}""".stripMargin)
      assert(cs == List(Clauses("A", 0, 0)))
    }
  }

  describe("the words stay ordinary names") {
    it("lets a method, a parameter and a call use requires and ensures") {
      val ms = both(
        """class A {
          |public:
          |  def requires(ensures: Int): Int { return ensures }
          |  def h(): Int { return requires(1) }
          |}""".stripMargin)
      assert(ms.forall(m => m.requires.isEmpty && m.ensures.isEmpty))
    }
  }
}
