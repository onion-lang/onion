package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompiledClass, CompilerConfig, OnionCompiler, StreamInputSource, StringInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.{File, StringReader}
import java.nio.file.Files

/**
 * Tests for issue #2116: W0012 (null to non-nullable) should not fire when the
 * callee is a Java-declared method, whose parameter nullness is unknown to the
 * compiler (same "platform type" leniency Java type variables already get),
 * not an Onion-declared method whose signature the user actually owns.
 */
class NullToNonNullableJavaCalleeSpec extends AnyFunSpec {

  private def compileWarnings(source: String, classPath: Seq[String] = Seq(".")) = {
    val config = CompilerConfig(classPath, null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  private def w12(result: onion.compiler.pipeline.CompilationResult) =
    result.diagnostics.warnings.filter(_.category.code == "W0012")

  /** Compiles `libSrc` on its own and writes the classes to a fresh directory. */
  private def libraryDir(libSrc: String): File = {
    val dir = Files.createTempDirectory("onion-w0012-lib").toFile
    new OnionCompiler(CompilerConfig(Seq.empty, null, "UTF-8", dir.getAbsolutePath, 10))
      .compile(Seq(StringInputSource(libSrc, "Lib.on"))) match {
      case CompilationOutcome.Success(classes) =>
        for (c: CompiledClass <- classes) {
          val f = new File(dir, c.className.replace('.', '/') + ".class")
          Option(f.getParentFile).foreach(_.mkdirs())
          Files.write(f.toPath, c.content)
        }
      case CompilationOutcome.Failure(errors) => fail(errors.mkString("; "))
    }
    dir
  }

  describe("W0012 and Java-declared callees") {
    it("does not warn when null is passed to a Java static method's Object/varargs parameter") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return String::format("%s", null)
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w12 = result.diagnostics.warnings.filter(_.category.code == "W0012")
      assert(w12.isEmpty, s"expected no W0012, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn when null is passed into a Java static generic vararg method") {
      val result = compileWarnings(
        """
          |import { java.util.Arrays }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = Arrays::asList(1, null)
          |    return "" + xs.size()
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w12 = result.diagnostics.warnings.filter(_.category.code == "W0012")
      assert(w12.isEmpty, s"expected no W0012, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("still warns when null is passed to an Onion-declared method's non-nullable parameter") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def shout(s: String): String { return s + "!" }
          |  static def main(args: String[]): String {
          |    return shout(null)
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(result.diagnostics.warnings.exists(_.category.code == "W0012"))
    }
    it("still warns when the callee is an Onion class compiled separately (tests/ against src/)") {
      val dir = libraryDir(
        """class Lib {
          |public:
          |  static def shout(s: String): String { return s + "!" }
          |}
          |""".stripMargin)
      val result = compileWarnings(
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Lib::shout(null)
          |  }
          |}
          |""".stripMargin, Seq(dir.getAbsolutePath))
      assert(!result.hasErrors)
      assert(w12(result).nonEmpty, s"expected W0012, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("still warns when a Java parameter is the receiver's own type argument (List[String].add)") {
      val result = compileWarnings(
        """import { java.util.ArrayList; java.util.HashMap }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs: List[String] = new ArrayList[String]()
          |    xs.add(null)
          |    val m: Map[String, String] = new HashMap[String, String]()
          |    m.put("k", null)
          |    return "" + xs.size()
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(w12(result).size == 2, s"expected two W0012, got: ${result.diagnostics.warnings.map(_.message)}")
    }
  }
}
