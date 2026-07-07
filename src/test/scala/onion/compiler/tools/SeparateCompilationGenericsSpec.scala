package onion.compiler.tools

import java.io.File
import java.net.{URL, URLClassLoader}
import java.nio.file.Files

import onion.compiler.{CompilationOutcome, CompiledClass, CompilerConfig, OnionCompiler, StringInputSource}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/**
 * Proves that generic types survive SEPARATE COMPILATION: a generic class
 * compiled to a `.class` file carries a JVM generic Signature attribute, so a
 * second compilation unit that only sees the `.class` file (not the source) can
 * still resolve its type parameters (no E0030 "takes no type arguments").
 *
 * Before the GenericSignatureEncoder was wired into codegen this failed because
 * codegen erased generics and never wrote a Signature attribute.
 */
class SeparateCompilationGenericsSpec extends AnyFunSpec with Matchers {

  private def baseConfig(classPath: Seq[String], outputDirectory: String): CompilerConfig =
    CompilerConfig(
      classPath = classPath,
      superClass = null,
      encoding = "UTF-8",
      outputDirectory = outputDirectory,
      maxErrorReports = 10
    )

  /** Compile a single unit; return its classes or fail with the error codes. */
  private def compileUnit(
    source: String,
    name: String,
    classPath: Seq[String],
    outputDirectory: String
  ): Seq[CompiledClass] =
    val config = baseConfig(classPath, outputDirectory)
    new OnionCompiler(config).compile(Seq(StringInputSource(source, name))) match
      case CompilationOutcome.Success(classes) => classes
      case CompilationOutcome.Failure(errors) =>
        fail(s"compilation of $name failed: " + errors.mkString("; "))

  /** Write compiled classes to a directory as real `.class` files. */
  private def writeClasses(classes: Seq[CompiledClass], dir: File): Unit =
    for c <- classes do
      val file = new File(dir, c.className.replace('.', '/') + ".class")
      Option(file.getParentFile).foreach(_.mkdirs())
      Files.write(file.toPath, c.content)

  describe("Separate compilation of generic types") {

    it("compiles a client against a separately-compiled generic class and runs it") {
      val tmp = Files.createTempDirectory("onion-sepgen").toFile
      val libDir = new File(tmp, "lib"); libDir.mkdirs()
      val appDir = new File(tmp, "app"); appDir.mkdirs()

      // Phase 1: compile the generic library and persist it as .class files.
      val libSrc =
        """class Container[T] {
          |  val v: T
          |public:
          |  def this(value: T) {
          |    this.v = value
          |  }
          |  def get(): T {
          |    return v
          |  }
          |}
          |""".stripMargin
      val libClasses = compileUnit(libSrc, "Container.on", Seq.empty, libDir.getAbsolutePath)
      writeClasses(libClasses, libDir)
      new File(libDir, "Container.class").exists() shouldBe true

      // Phase 2: compile the client seeing ONLY the .class file on the classpath.
      // This is the regression proof: it must NOT report E0030.
      val appSrc =
        """class App {
          |public:
          |  static def main(args: String[]): void {
          |    val b: Container[String] = new Container[String]("hi")
          |    IO::println(b.get())
          |  }
          |}
          |""".stripMargin
      val appClasses =
        compileUnit(appSrc, "App.on", Seq(libDir.getAbsolutePath), appDir.getAbsolutePath)
      writeClasses(appClasses, appDir)

      // Load both units and run App.main; it should print "hi" without error.
      val urls: Array[URL] = Array(libDir.toURI.toURL, appDir.toURI.toURL)
      val loader = new URLClassLoader(urls, getClass.getClassLoader)
      val appClass = Class.forName("App", true, loader)
      val mainMethod = appClass.getMethod("main", classOf[Array[String]])

      val out = new java.io.ByteArrayOutputStream()
      val prev = System.out
      try
        System.setOut(new java.io.PrintStream(out, true, "UTF-8"))
        mainMethod.invoke(null, Array.empty[String])
      finally System.setOut(prev)

      out.toString("UTF-8").trim shouldBe "hi"
    }

    it("emits a class generic Signature that the reader accepts (no raw-generic error)") {
      val tmp = Files.createTempDirectory("onion-sepgen2").toFile
      val libDir = new File(tmp, "lib"); libDir.mkdirs()
      val appDir = new File(tmp, "app"); appDir.mkdirs()

      // Bounded type parameter [T extends Number] plus a List[String] field.
      val libSrc =
        """import { java.util.* }
          |class NumBox[T extends Number] {
          |  val v: T
          |  val names: List[String]
          |public:
          |  def this(value: T) {
          |    this.v = value
          |    this.names = new ArrayList[String]
          |  }
          |  def get(): T {
          |    return v
          |  }
          |}
          |""".stripMargin
      val libClasses = compileUnit(libSrc, "NumBox.on", Seq.empty, libDir.getAbsolutePath)
      writeClasses(libClasses, libDir)

      // Compiling a client with a bare/raw `NumBox` must trigger the raw-generic
      // ban (E0066) rather than E0030 — proof the reader now sees the type
      // parameters. A properly parameterized use must compile cleanly.
      val rawSrc =
        """class RawApp {
          |public:
          |  static def main(args: String[]): void {
          |    val b: NumBox = null
          |  }
          |}
          |""".stripMargin
      val rawConfig = baseConfig(Seq(libDir.getAbsolutePath), appDir.getAbsolutePath)
      val rawOutcome =
        new OnionCompiler(rawConfig).compile(Seq(StringInputSource(rawSrc, "RawApp.on")))
      rawOutcome match
        case CompilationOutcome.Failure(errors) =>
          val text = errors.mkString("; ")
          text should include("E0066")
          text should not include "E0030"
        case CompilationOutcome.Success(_) =>
          fail("expected raw-generic use of NumBox to be rejected")

      // A parameterized use compiles without errors.
      val okSrc =
        """import { java.lang.* }
          |class OkApp {
          |public:
          |  static def main(args: String[]): void {
          |    val b: NumBox[Integer] = new NumBox[Integer](JInteger::valueOf(3))
          |    IO::println(b.get())
          |  }
          |}
          |""".stripMargin
      val okClasses =
        compileUnit(okSrc, "OkApp.on", Seq(libDir.getAbsolutePath), appDir.getAbsolutePath)
      okClasses.map(_.className) should contain("OkApp")
    }
  }
}
