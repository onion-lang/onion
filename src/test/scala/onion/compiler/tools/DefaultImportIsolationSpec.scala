package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import java.io.StringReader
import org.scalatest.funsuite.AnyFunSuite

class DefaultImportIsolationSpec extends AnyFunSuite {
  private val imported = """import { java.time.LocalDate }
    |def year(d: LocalDate): Int = d.getYear()
    |""".stripMargin
  private val unimported = "def year(d: LocalDate): Int = d.getYear()"
  private def source(text: String, name: String) =
    new StreamInputSource(() => new StringReader(text), name)
  private def compiler = new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10))

  test("a custom import in one source unit is not added to another unit's defaults") {
    val result = compiler.compileDetailed(Seq(
      source(imported, "WithImport.on"), source(unimported, "WithoutImport.on")))
    assert(result.hasErrors)
    assert(result.allErrors.exists(e => e.sourceFile == "WithoutImport.on" && e.message.contains("LocalDate")))
  }

  test("a custom import does not survive into the next compilation on the same compiler") {
    val c = compiler
    assert(!c.compileDetailed(Seq(source(imported, "WithImport.on"))).hasErrors)
    val next = c.compileDetailed(Seq(source(unimported, "WithoutImport.on")))
    assert(next.hasErrors)
    assert(next.allErrors.exists(e => e.sourceFile == "WithoutImport.on" && e.message.contains("LocalDate")))
  }

  test("default aliases and collections remain available across repeated compilations") {
    val c = compiler
    val code = """def intValue(n: JInteger): Int = n.intValue()
      |def count(xs: ArrayList[String]): Int = xs.size()
      |""".stripMargin
    for (name <- Seq("First.on", "Second.on")) {
      val result = c.compileDetailed(Seq(source(code, name)))
      assert(!result.hasErrors, result.allErrors.mkString("; "))
      assert(result.classes.nonEmpty)
    }
  }
}
