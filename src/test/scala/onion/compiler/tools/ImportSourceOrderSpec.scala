package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * Issue #2776: the order of source files on the command line does not change what an import
 * finds. `onionc app.on lib.on` used to fail with E0003 on `import { shapes.Plain }`, and
 * `shapes.*` lost to a default-imported JDK class of the same name.
 */
class ImportSourceOrderSpec extends AnyFunSpec {
  private val lib =
    """module shapes;
      |class Plain {
      |public:
      |  static def make(): Int = 7
      |}
      |class Box[T] {
      |}
      |class Timer {
      |}""".stripMargin

  private def errorCodes(sources: (String, String)*): Either[Seq[String], Unit] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    val inputs = sources.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Success(_)      => Right(())
      case CompilationOutcome.Failure(errors) => Left(errors.map(_.toString))
    }
  }

  private def app(importLine: String, body: String): String =
    s"""module app;
       |import { $importLine }
       |class Use {
       |public:
       |  static def f(s: $body): void { }
       |}""".stripMargin

  describe("a single-class import of a class declared in another file") {
    val a = app("shapes.Plain", "Plain")
    it("resolves when the declaring file comes first") {
      assert(errorCodes("lib.on" -> lib, "app.on" -> a) == Right(()))
    }
    it("resolves when the declaring file comes later") {
      assert(errorCodes("app.on" -> a, "lib.on" -> lib) == Right(()))
    }
    it("is still E0003 when no file declares the class") {
      val Left(errors) = errorCodes("app.on" -> app("shapes.Nope", "Plain"), "lib.on" -> lib)
      assert(errors.exists(_.contains("E0003")))
    }
  }

  describe("a wildcard import of a module that declares a name the JDK also has") {
    it("prefers the module's generic class when its file comes first") {
      assert(errorCodes("lib.on" -> lib, "app.on" -> app("shapes.*", "Box[Int]")) == Right(()))
    }
    it("prefers the module's class when its file comes later") {
      // `Timer` is also java.util.Timer and javax.swing.Timer
      assert(errorCodes("app.on" -> app("shapes.*", "Timer"), "lib.on" -> lib) == Right(()))
    }
  }
}
