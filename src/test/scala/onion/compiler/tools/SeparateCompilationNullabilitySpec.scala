package onion.compiler.tools

import java.io.File
import java.nio.file.Files

import onion.compiler.{CompilationOutcome, CompiledClass, CompilerConfig, OnionCompiler, StringInputSource}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** A `T?` parameter keeps its `?` when the declaring class is read back from a `.class` file. */
class SeparateCompilationNullabilitySpec extends AnyFunSpec with Matchers {

  private def compile(source: String, name: String, classPath: Seq[String], out: String): CompilationOutcome =
    new OnionCompiler(CompilerConfig(classPath, null, "UTF-8", out, 10))
      .compile(Seq(StringInputSource(source, name)))

  private def libraryDir(libSrc: String): File =
    val dir = Files.createTempDirectory("onion-sepnull").toFile
    compile(libSrc, "Lib.on", Seq.empty, dir.getAbsolutePath) match
      case CompilationOutcome.Success(classes) =>
        for c: CompiledClass <- classes do
          val f = new File(dir, c.className.replace('.', '/') + ".class")
          Option(f.getParentFile).foreach(_.mkdirs())
          Files.write(f.toPath, c.content)
      case CompilationOutcome.Failure(errors) => fail(errors.mkString("; "))
    dir

  private def errorCodes(client: String, lib: File): Seq[String] =
    compile(client, "Client.on", Seq(lib.getAbsolutePath), lib.getAbsolutePath) match
      case CompilationOutcome.Success(_)      => Nil
      case CompilationOutcome.Failure(errors) => errors.map(_.toString)

  private val lib =
    """class Gh {
      |public:
      |  static def search(token: String?): String = token ?: "none"
      |  static def strict(token: String): String = token
      |  def this(base: String?) { }
      |}
      |""".stripMargin

  describe("Separate compilation of nullable parameters") {
    it("accepts a String? argument for a String? parameter") {
      val dir = libraryDir(lib)
      val client =
        """class Client {
          |public:
          |  static def run(): String {
          |    val t: String? = null
          |    return Gh::search(t)
          |  }
          |  static def make(): Gh {
          |    val t: String? = null
          |    return new Gh(t)
          |  }
          |}
          |""".stripMargin
      errorCodes(client, dir) shouldBe Nil
    }

    it("still rejects a String? argument for a non-null String parameter (E0005)") {
      val dir = libraryDir(lib)
      val client =
        """class Client {
          |public:
          |  static def run(): String {
          |    val t: String? = null
          |    return Gh::strict(t)
          |  }
          |}
          |""".stripMargin
      errorCodes(client, dir).mkString should include("E0005")
    }
  }
}
