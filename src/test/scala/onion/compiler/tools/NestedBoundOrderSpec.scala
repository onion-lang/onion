package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * Issue #2967: a type argument inside another type parameter's bound
 * (`class Bad1[T extends Num[String]]`) is checked against `Num`'s real bound
 * whatever the declaration order. `Num` declared later used to be checked against
 * its placeholder `Object` bound, so `Num[String]` passed; and an argument whose
 * superclass is a user class (`Box[MyNum]`, `MyNum extends Number`) was rejected,
 * because its supertype was not known yet.
 */
class NestedBoundOrderSpec extends AnyFunSpec {
  private def compile(sources: (String, String)*): Either[Seq[String], Unit] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    val inputs = sources.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Success(_)      => Right(())
      case CompilationOutcome.Failure(errors) => Left(errors.map(_.toString))
    }
  }

  private def assertE0000(r: Either[Seq[String], Unit]): Unit = {
    assert(r.isLeft, r)
    assert(r.swap.toOption.get.exists(_.contains("E0000")), r)
  }

  private val num = "class Num[T extends Number] { }"
  private val bad = "class Bad1[T extends Num[String]] { }"

  private val myNum =
    """class MyNum extends Number {
      |public:
      |  def this { }
      |  def intValue: Int = 0
      |  def longValue: Long = 0L
      |  def floatValue: Float = 0.0f
      |  def doubleValue: Double = 0.0
      |}""".stripMargin

  describe("a bound inside a type-parameter bound") {
    it("is violated when the inner generic class is declared later in the file") {
      assertE0000(compile("s.on" -> s"$bad\n$num"))
    }
    it("is violated when the inner generic class is declared earlier in the file") {
      assertE0000(compile("s.on" -> s"$num\n$bad"))
    }
    it("is violated in either file order") {
      assertE0000(compile("a.on" -> bad, "b.on" -> num))
      assertE0000(compile("b.on" -> num, "a.on" -> bad))
    }
    it("is violated in an interface or a record declared before the inner class") {
      assertE0000(compile("s.on" -> s"interface I[T extends Num[String]] { }\n$num"))
      assertE0000(compile("s.on" -> s"record R[T extends Num[String]](t: T)\n$num"))
    }
    it("is met by a class whose superclass meets it, in any order") {
      val use = "class A[T extends Box[MyNum]] { }"
      val box = "class Box[T extends Number] { }"
      assert(compile("s.on" -> s"$box\n$use\n$myNum") == Right(()))
      assert(compile("s.on" -> s"$use\n$box\n$myNum") == Right(()))
      assert(compile("s.on" -> s"$myNum\n$use\n$box") == Right(()))
    }
    it("is met by a plain argument when the inner class is declared later") {
      assert(compile("s.on" -> s"class Ok[T extends Num[Integer]] { }\n$num") == Right(()))
    }
    it("still accepts an F-bound on a later class") {
      val src =
        """class Holder[A extends Animal[A]] { }
          |class Animal[T extends Animal[T]] { }
          |class Cat extends Animal[Cat] { }""".stripMargin
      assert(compile("s.on" -> src) == Right(()))
    }
  }
}
