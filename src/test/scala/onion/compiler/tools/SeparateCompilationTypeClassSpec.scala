package onion.compiler.tools

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files

import onion.compiler.{CompilationOutcome, CompiledClass, CompilerConfig, OnionCompiler, StringInputSource}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/**
 * A `[T: Trait]` method read back from a `.class` file keeps its constraint, so a separate
 * compilation can call it without naming the dictionary (#2768).
 */
class SeparateCompilationTypeClassSpec extends AnyFunSpec with Matchers {

  private def compile(source: String, name: String, classPath: Seq[String], out: String): CompilationOutcome =
    new OnionCompiler(CompilerConfig(classPath, null, "UTF-8", out, 10))
      .compile(Seq(StringInputSource(source, name)))

  private def write(dir: File, classes: Seq[CompiledClass]): Unit =
    for c <- classes do
      val f = new File(dir, c.className.replace('.', '/') + ".class")
      Option(f.getParentFile).foreach(_.mkdirs())
      Files.write(f.toPath, c.content)

  private def libraryDir(libSrc: String): File =
    val dir = Files.createTempDirectory("onion-septc").toFile
    compile(libSrc, "NumLib.on", Seq.empty, dir.getAbsolutePath) match
      case CompilationOutcome.Success(classes) => write(dir, classes)
      case CompilationOutcome.Failure(errors)  => fail(errors.mkString("; "))
    dir

  /** Compiles `client` against `lib`, runs `Client.run()` and returns its result. */
  private def run(client: String, lib: File): AnyRef =
    val out = Files.createTempDirectory("onion-septc-client").toFile
    compile(client, "Client.on", Seq(lib.getAbsolutePath), out.getAbsolutePath) match
      case CompilationOutcome.Success(classes) =>
        write(out, classes)
        val loader = new URLClassLoader(Array(out.toURI.toURL, lib.toURI.toURL), getClass.getClassLoader)
        loader.loadClass("Client").getMethod("run").invoke(null)
      case CompilationOutcome.Failure(errors) => fail(errors.mkString("; "))

  private def errors(client: String, lib: File): String =
    val out = Files.createTempDirectory("onion-septc-client").toFile
    compile(client, "Client.on", Seq(lib.getAbsolutePath), out.getAbsolutePath) match
      case CompilationOutcome.Success(_)      => ""
      case CompilationOutcome.Failure(errors) => errors.mkString("; ")

  private val lib =
    """module numlib;
      |trait Numeric[T] {
      |  def zero(): T
      |  def plus(a: T, b: T): T
      |}
      |trait Show[T] {
      |  def show(a: T): String
      |}
      |instance Numeric[Int] {
      |  def zero(): Int = 0
      |  def plus(a: Int, b: Int): Int = a + b
      |}
      |instance Show[Int] {
      |  def show(a: Int): String = "#" + a
      |}
      |class Agg {
      |public:
      |  static def total[T: Numeric](xs: List[T]): T {
      |    var acc: T = Numeric[T]::zero()
      |    foreach x: T in xs { acc = Numeric[T]::plus(acc, x) }
      |    return acc
      |  }
      |  static def label[T: Numeric + Show](xs: List[T], prefix: String = "sum"): String =
      |    prefix + "=" + Show[T]::show(total(xs))
      |}
      |""".stripMargin

  describe("A type-class method from a class file") {
    it("is called with its dictionary filled from the caller's instance") {
      val client =
        """import { numlib.Agg }
          |class Client {
          |public:
          |  static def run(): Int {
          |    val xs: List[Int] = [1, 2, 4]
          |    return Agg::total(xs)
          |  }
          |}
          |""".stripMargin
      run(client, libraryDir(lib)) shouldBe Integer.valueOf(7)
    }

    it("keeps two constraints on one type parameter, in order, after an ordinary parameter") {
      val client =
        """import { numlib.Agg }
          |class Client {
          |public:
          |  static def run(): String {
          |    val xs: List[Int] = [1, 2, 4]
          |    return Agg::label(xs, "total")
          |  }
          |}
          |""".stripMargin
      run(client, libraryDir(lib)) shouldBe "total=#7"
    }

    it("forwards a caller's own dictionary to it") {
      val client =
        """import { numlib.Agg; numlib.Numeric }
          |class Client {
          |public:
          |  static def twice[T: Numeric](xs: List[T]): T {
          |    val t: T = Agg::total(xs)
          |    return Numeric[T]::plus(t, t)
          |  }
          |  static def run(): Int {
          |    val xs: List[Int] = [1, 2, 4]
          |    return twice(xs)
          |  }
          |}
          |""".stripMargin
      run(client, libraryDir(lib)) shouldBe Integer.valueOf(14)
    }

    it("fills the dictionary on a statically imported call, ground or forwarded") {
      val client =
        """import { numlib.Numeric; numlib.Agg::* }
          |class Client {
          |public:
          |  static def twice[T: Numeric](xs: List[T]): T {
          |    val t: T = total(xs)
          |    return Numeric[T]::plus(t, t)
          |  }
          |  static def run(): String {
          |    val xs: List[Int] = [1, 2, 4]
          |    return "" + total(xs) + " " + twice(xs)
          |  }
          |}
          |""".stripMargin
      run(client, libraryDir(lib)) shouldBe "7 14"
    }

    it("reports a type with no instance by the instance it lacks, not E0005") {
      val client =
        """import { numlib.Agg }
          |class Client {
          |public:
          |  static def run(): String {
          |    val xs: List[String] = ["a"]
          |    return Agg::total(xs)
          |  }
          |}
          |""".stripMargin
      val msg = errors(client, libraryDir(lib))
      msg should include("Numeric[String]")
      msg should not include("E0005")
    }
  }

  describe("A statically imported type-class method in the same compilation") {
    it("gets its dictionary instead of a null that fails at run time") {
      val client =
        """import { numlib.Agg::* }
          |class Client {
          |public:
          |  static def run(): Int {
          |    val xs: List[Int] = [1, 2, 4]
          |    return total(xs)
          |  }
          |}
          |""".stripMargin
      val out = Files.createTempDirectory("onion-septc-batch").toFile
      new OnionCompiler(CompilerConfig(Seq.empty, null, "UTF-8", out.getAbsolutePath, 10))
        .compile(Seq(StringInputSource(lib, "NumLib.on"), StringInputSource(client, "Client.on"))) match
        case CompilationOutcome.Success(classes) =>
          write(out, classes)
          val loader = new URLClassLoader(Array(out.toURI.toURL), getClass.getClassLoader)
          loader.loadClass("Client").getMethod("run").invoke(null) shouldBe Integer.valueOf(7)
        case CompilationOutcome.Failure(errors) => fail(errors.mkString("; "))
    }
  }
}
