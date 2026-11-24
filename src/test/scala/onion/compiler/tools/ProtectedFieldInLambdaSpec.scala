package onion.compiler.tools

import onion.tools.Shell
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import javax.tools.ToolProvider

/**
 * A lambda in a subclass that read or wrote an inherited protected field of a Java class in
 * another package compiled, then threw IllegalAccessError: the closure class is not a
 * subclass of its host (#3218). The access now goes through a synthetic accessor on the host,
 * as calls to protected methods do since #2910.
 */
class ProtectedFieldInLambdaSpec extends AbstractShellSpec {
  lazy val fixture: Path = compileJava(Seq(
    "p/Base.java" ->
      """package p;
        |public class Base {
        |  protected static int K = 5;
        |  protected static long L = 10L;
        |  protected static String S = "s";
        |  protected int inst = 7;
        |  public static int get() { return K; }
        |}
        |""".stripMargin
  ))

  private def runWith(program: String): Shell.Result =
    Shell(Seq(fixture.toString)).run(program, "ProtectedFieldInLambda.on", Array())

  describe("a lambda reading or writing an inherited protected field of a Java class") {
    it("reads, increments and assigns a protected static field (the issue's repro)") {
      assert(runWith(
        """import { p.Base }
          |class B extends Base {
          |public:
          |  def this {}
          |  def inc(): Int { K++; return K }
          |  def lr(): Int { val f = () -> { return K }; return f() }
          |  def lq(): Int { val f = () -> { Base::K = 40; return 1 }; return f() }
          |}
          |def main(args: String[]): String {
          |  val b = new B()
          |  return "" + b.inc() + "," + b.lr() + "," + b.lq() + "," + Base::get()
          |}
          |""".stripMargin) == Shell.Success("6,6,1,40"))
    }

    it("handles ++, +=, a long field, a static method's lambda and a nested lambda") {
      assert(runWith(
        """import { p.Base }
          |class B extends Base {
          |public:
          |  def this {}
          |  def a(): Int { val f = () -> { K++; K += 2; return K }; return f() }
          |  def b(): Long { val f = () -> { L = L * 3L; return L }; return f() }
          |  def c(): String {
          |    val f: () -> String = () -> { val g: () -> String = () -> { S = S + "!"; return S }; return g() }
          |    return f()
          |  }
          |  static def s(): String { val f = () -> { return S + B::K }; return f() }
          |}
          |def main(args: String[]): String {
          |  val b = new B()
          |  return "" + b.a() + "," + b.b() + "," + b.c() + "," + B::s()
          |}
          |""".stripMargin) == Shell.Success("8,30,s!,s!8"))
    }

    it("reads and writes a protected instance field, through this. or another instance") {
      assert(runWith(
        """import { p.Base }
          |class D extends Base {
          |public:
          |  def this {}
          |  def i(): Int { val f = () -> { inst = inst + 1; inst += 2; inst++; return inst }; return f() }
          |  def j(): Int { val f = () -> { this.inst = 100; return this.inst }; return f() }
          |  def other(o: D): Int { val f = () -> { return o.inst }; return f() }
          |}
          |def main(args: String[]): String {
          |  val d = new D()
          |  return "" + d.i() + "," + d.j() + "," + d.other(new D())
          |}
          |""".stripMargin) == Shell.Success("11,100,7"))
    }

    it("writes a value whose own evaluation runs a try") {
      assert(runWith(
        """import { p.Base }
          |class E extends Base {
          |public:
          |  def this {}
          |  def t(): Int {
          |    val f = () -> {
          |      inst = try { Integer::parseInt("x") } catch e: NumberFormatException { 42 }
          |      K = try { 9 } finally { }
          |      return inst + K
          |    }
          |    return f()
          |  }
          |}
          |def main(args: String[]): Int { return new E().t() }
          |""".stripMargin) == Shell.Success(51))
    }
  }

  private def compileJava(sources: Seq[(String, String)]): Path = {
    val root = Files.createTempDirectory("protected-field-lambda")
    val src = Files.createDirectory(root.resolve("src"))
    val classes = Files.createDirectory(root.resolve("classes"))
    val files = sources.map { (rel, text) =>
      val f = src.resolve(rel)
      Files.createDirectories(f.getParent)
      Files.writeString(f, text, UTF_8)
      f.toString
    }
    val compiler = Option(ToolProvider.getSystemJavaCompiler).getOrElse(fail("this spec needs a JDK compiler"))
    val errors = new ByteArrayOutputStream()
    val exit = compiler.run(null, null, errors, (Seq("-d", classes.toString) ++ files)*)
    assert(exit == 0, errors.toString(UTF_8))
    classes
  }
}
