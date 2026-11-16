package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * Issue #2853: a generic class declared in a file that comes later on the command line is
 * usable with type arguments in an earlier file. `onionc app.on lib.on` used to fail with
 * E0030 "does not take type arguments" on `Crate[Int]`; `onionc lib.on app.on` compiled.
 */
class GenericSourceOrderSpec extends AnyFunSpec {
  private def errorCodes(sources: (String, String)*): Either[Seq[String], Unit] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    val inputs = sources.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Success(_)      => Right(())
      case CompilationOutcome.Failure(errors) => Left(errors.map(_.toString))
    }
  }

  private val lib =
    """module shapes;
      |class Crate[T] {
      |}
      |interface Source[T] {
      |  def next(): T
      |}
      |record Pair[A, B](a: A, b: B)
      |class Num[T extends Number] {
      |}""".stripMargin

  private def app(paramType: String): String =
    s"""module app;
       |import { shapes.* }
       |class Use {
       |public:
       |  static def f(s: $paramType): void { }
       |}""".stripMargin

  private def bothOrders(paramType: String): Seq[Either[Seq[String], Unit]] = {
    val a = app(paramType)
    Seq(errorCodes("app.on" -> a, "lib.on" -> lib), errorCodes("lib.on" -> lib, "app.on" -> a))
  }

  describe("a generic type from another file, used with type arguments") {
    it("compiles in either file order for a class") {
      assert(bothOrders("Crate[Int]") == Seq(Right(()), Right(())))
    }
    it("compiles in either file order for an interface") {
      assert(bothOrders("Source[String]") == Seq(Right(()), Right(())))
    }
    it("compiles in either file order for a record") {
      assert(bothOrders("Pair[String, Int]") == Seq(Right(()), Right(())))
    }
    it("compiles in either file order when the argument meets the bound") {
      assert(bothOrders("Num[Integer]") == Seq(Right(()), Right(())))
    }
    it("compiles in either file order as a supertype argument") {
      val sub = "module app;\nimport { shapes.* }\nclass Sub extends Crate[String] {\n}"
      assert(errorCodes("app.on" -> sub, "lib.on" -> lib) == Right(()))
      assert(errorCodes("lib.on" -> lib, "app.on" -> sub) == Right(()))
    }
  }

  describe("a later file's generic type still checks its own rules") {
    it("reports a bound violation in either file order") {
      bothOrders("Num[String]").foreach { r =>
        assert(r.isLeft, r)
        assert(r.swap.toOption.get.exists(_.contains("E0000")), r)
      }
    }
    it("reports a wrong number of type arguments in either file order") {
      bothOrders("Crate[Int, Int]").foreach { r =>
        assert(r.isLeft, r)
        assert(r.swap.toOption.get.exists(_.contains("E0031")), r)
      }
    }
    it("still reports E0030 for a type argument on a non-generic class in a later file") {
      val plain = "module shapes;\nclass Plain {\n}"
      val Left(errors) = errorCodes("app.on" -> app("Plain[Int]"), "lib.on" -> plain)
      assert(errors.exists(_.contains("E0030")))
    }
  }
}
