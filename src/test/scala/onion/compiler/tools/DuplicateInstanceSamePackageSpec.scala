package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/** Issue #2777: a duplicate `instance` in two files of one package is E0097, as across modules. */
class DuplicateInstanceSamePackageSpec extends AnyFunSpec {
  private def errors(sources: (String, String)*): String = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    val inputs = sources.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Success(_)      => ""
      case CompilationOutcome.Failure(errs)   => errs.map(_.toString).mkString("\n")
    }
  }

  private val s1 =
    """trait Show[T] { def show(x: T): String }
      |instance Show[Int] { def show(x: Int): String = "a" + x }""".stripMargin
  private val s2 = """instance Show[Int] { def show(x: Int): String = "b" + x }"""

  it("two default-package files declaring the same instance report E0097, not E0008") {
    val out = errors("s1.on" -> s1, "s2.on" -> s2)
    assert(out.contains("E0097"), out)
    assert(!out.contains("E0008"), out)
    assert(!out.contains("$$"), out)
  }

  it("a genuinely duplicated class is still E0008") {
    val out = errors("a.on" -> "class A {}", "b.on" -> "class A {}")
    assert(out.contains("E0008"), out)
  }

  it("distinct instances in two files still compile") {
    assert(errors("s1.on" -> s1, "s3.on" -> """instance Show[String] { def show(x: String): String = x }""") == "")
  }

  it("a generic type argument is printed as written, not mangled (E0097)") {
    val t = "import { java.util.* }\ntrait Show[T] { def show(x: T): String }\n"
    val body = """instance Show[List[String]] { def show(x: List[String]): String = "x" }"""
    val out = errors("t1.on" -> (t + body), "t2.on" -> ("import { java.util.* }\n" + body))
    assert(out.contains("E0097"), out)
    assert(out.contains("Show[List[String]]"), out)
    assert(!out.contains("$$"), out)
  }

  it("a two-parameter trait instance is printed as written (E0097)") {
    val t = "trait Conv[A, B] { def conv(a: A): B }\n"
    val body = """instance Conv[Int, String] { def conv(a: Int): String = "" + a }"""
    val out = errors("c1.on" -> (t + body), "c2.on" -> body)
    assert(out.contains("E0097"), out)
    assert(out.contains("Conv[int,String]"), out)
    assert(!out.contains("$"), out)
  }

  it("two classes whose names contain `$$` are E0008, not an instance") {
    val out = errors("a.on" -> "class `Foo$$Bar` {}", "b.on" -> "class `Foo$$Bar` {}")
    assert(out.contains("E0008"), out)
    assert(!out.contains("E0097"), out)
  }

  it("a class named like an instance's lowered name is E0008 in either order") {
    val cls = "class `Show$$Integer` {}"
    val inst = s1
    for (order <- Seq(Seq("a.on" -> cls, "b.on" -> inst), Seq("a.on" -> inst, "b.on" -> cls))) {
      val out = errors(order: _*)
      assert(out.contains("E0008"), out)
      assert(!out.contains("E0097"), out)
    }
  }
}
