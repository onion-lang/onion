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
}
