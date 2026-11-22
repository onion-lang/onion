package onion.compiler.tools

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}
import java.net.URLClassLoader
import java.nio.file.Files
import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/** Issue #2990: an `instance` declared through an import alias is the instance of the real trait. */
class TypeClassImportAliasSpec extends AnyFunSpec {
  private val lib =
    """module lib
      |trait Sh[T] { def sh(x: T): String }
      |""".stripMargin

  private def run(sources: (String, String)*): Either[String, String] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    val inputs = sources.map { case (n, s) => new StreamInputSource(() => new StringReader(s), n) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Success(classes) =>
        val dir = Files.createTempDirectory("onion-tc-2990")
        classes.foreach { c =>
          val f = dir.resolve(c.className.replace('.', '/') + ".class")
          Files.createDirectories(f.getParent); Files.write(f, c.content)
        }
        val loader = new URLClassLoader(Array(dir.toUri.toURL), getClass.getClassLoader)
        val buf = new ByteArrayOutputStream(); val old = System.out
        System.setOut(new PrintStream(buf, true, "UTF-8"))
        try loader.loadClass("Main").getMethod("main", classOf[Array[String]]).invoke(null, Array[String]())
        finally System.setOut(old)
        Right(buf.toString("UTF-8").trim)
      case CompilationOutcome.Failure(errors) => Left(errors.map(_.toString).mkString("\n"))
    }
  }

  private def app(imp: String, name: String): String =
    s"""import { $imp }
       |instance $name[Integer] {
       |  def sh(x: Integer): String = "I" + x
       |}
       |def show[T: $name](x: T): String = $name[T]::sh(x)
       |class Main {
       |public:
       |  static def main(args: String[]): void { println(show(5)) }
       |}""".stripMargin

  it("finds an instance declared under the trait's real name") {
    assert(run("lib.on" -> lib, "app.on" -> app("lib.Sh", "Sh")) == Right("I5"))
  }

  it("finds an instance declared through an import alias") {
    assert(run("lib.on" -> lib, "app.on" -> app("lib.Sh as S", "S")) == Right("I5"))
  }
}
