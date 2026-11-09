package onion.compiler.tools

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}
import java.net.URLClassLoader
import java.nio.file.{Files, Path}
import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * Issue #2769: a type-class instance is found by its trait, not by the trait's simple name.
 * A trait declared in a `module` gets the instance declared beside it, and another trait that
 * only shares its simple name can no longer capture the dictionary slot (which compiled, then
 * threw IncompatibleClassChangeError).
 */
class TypeClassInstanceAcrossModulesSpec extends AnyFunSpec {
  private val lib =
    """module numlib;
      |trait Numeric[T] {
      |  def zero(): T
      |  def plus(a: T, b: T): T
      |}
      |instance Numeric[Int] {
      |  def zero(): Int = 0
      |  def plus(a: Int, b: Int): Int = a + b
      |}
      |class Agg {
      |public:
      |  static def total[T: Numeric](xs: List[T]): T {
      |    var acc: T = Numeric[T]::zero()
      |    foreach x: T in xs { acc = Numeric[T]::plus(acc, x) }
      |    return acc
      |  }
      |  static def inside(): Int = total([10, 20])
      |}""".stripMargin

  /** Compile the sources together, run `Main.main`, and return what it printed, or the errors. */
  private def compileAndRun(sources: (String, String)*): Either[String, String] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    val inputs = sources.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Success(classes) =>
        val dir: Path = Files.createTempDirectory("onion-tc-2769")
        classes.foreach { c =>
          val f = dir.resolve(c.className.replace('.', '/') + ".class")
          Files.createDirectories(f.getParent)
          Files.write(f, c.content)
        }
        val loader = new URLClassLoader(Array(dir.toUri.toURL), getClass.getClassLoader)
        val buffer = new ByteArrayOutputStream()
        val old = System.out
        System.setOut(new PrintStream(buffer, true, "UTF-8"))
        try loader.loadClass("Main").getMethod("main", classOf[Array[String]]).invoke(null, Array[String]())
        finally System.setOut(old)
        Right(buffer.toString("UTF-8").trim)
      case CompilationOutcome.Failure(errors) => Left(errors.map(_.toString).mkString("\n"))
    }
  }

  private def main(body: String, extra: String = ""): String =
    s"""import { numlib.Agg }
       |$extra
       |class Main {
       |public:
       |  static def main(args: String[]): void {
       |    $body
       |  }
       |}""".stripMargin

  it("finds the instance declared beside a module's trait") {
    assert(compileAndRun("lib.on" -> lib, "main.on" -> main("val xs: List[Int] = [1, 2, 4]\n    println(Agg::total(xs))")) == Right("7"))
  }

  it("finds it from a call inside the module") {
    assert(compileAndRun("lib.on" -> lib, "main.on" -> main("println(Agg::inside())")) == Right("30"))
  }

  it("does not pass another trait's instance that only shares the simple name") {
    val own =
      """trait Numeric[T] {
        |  def zero(): T
        |  def plus(a: T, b: T): T
        |}
        |instance Numeric[Integer] {
        |  def zero(): Integer = 0
        |  def plus(a: Integer, b: Integer): Integer = a + b
        |}""".stripMargin
    assert(compileAndRun("lib.on" -> lib, "main.on" -> main("val xs: List[Int] = [1, 2, 4]\n    println(Agg::total(xs))", own)) == Right("7"))
  }

  it("still reports a missing instance as an error, not a crash") {
    val r = compileAndRun("lib.on" -> lib, "main.on" -> main("val xs: List[Long] = [1L, 2L]\n    println(Agg::total(xs))"))
    assert(r.isLeft && r.left.toOption.get.contains("E0003"), r)
  }
}
