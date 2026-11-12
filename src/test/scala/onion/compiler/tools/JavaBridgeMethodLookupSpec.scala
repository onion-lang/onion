package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.{ByteArrayOutputStream, StringReader}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import javax.tools.ToolProvider

// #2708: a Java class's synthetic bridge (`Integer.compareTo(Object)`, which
// javac emits for `Comparable[Integer]`) is not a method the source can call.
// Java resolves `i.compareTo("x")` against `compareTo(Integer)` only and rejects
// it; Onion used to pick the bridge, compile, and throw ClassCastException.
// A visibility bridge (`StringBuilder.length()`, inherited from a non-public
// superclass) has no real sibling and stays callable. A visibility bridge that
// does have a same-name overload (`Pub.foo(String)` over a package-private
// `Base`, next to `Pub.foo(Integer)`) stays callable too (#2837's panel block):
// dropping it sent the call to `Base` and threw IllegalAccessError.
class JavaBridgeMethodLookupSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def run(body: String): Shell.Result =
    shell.run(
      s"""import { java.lang.Integer as JInteger }
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |${body.stripMargin}
         |  }
         |}
         |""".stripMargin, "JavaBridgeMethodLookup.on", Array())

  describe("A Java bridge method is not an overload (#2708)") {
    it("rejects JInteger.compareTo with a String argument (E0005)") {
      assert(errorCodes(
        """import { java.lang.Integer as JInteger }
          |val i: JInteger = 5
          |println(i.compareTo("x"))
          |""".stripMargin).contains("E0005"))
    }

    it("rejects StringBuilder.compareTo with a String argument (E0005)") {
      assert(errorCodes(
        """val sb = new StringBuilder("a")
          |println(sb.compareTo("b"))
          |""".stripMargin).contains("E0005"))
    }

    it("still calls JInteger.compareTo with a JInteger or an Int") {
      assert(run(
        """val i: JInteger = 5
          |val j: JInteger = 7
          |return "" + i.compareTo(j) + "," + i.compareTo(3)
          |""") == Shell.Success("-1,1"))
    }

    it("still calls compareTo through Comparable[String]") {
      assert(run(
        """val c: Comparable[String] = "q"
          |return "" + c.compareTo("r") + "," + "b".compareTo("a")
          |""") == Shell.Success("-1,1"))
    }

    it("keeps a visibility bridge callable (StringBuilder.length, charAt)") {
      assert(run(
        """val sb = new StringBuilder("abc")
          |sb.append("de").append(1)
          |return "" + sb.length() + sb.charAt(1) + sb.toString()
          |""") == Shell.Success("6babcde1"))
    }
  }

  describe("A visibility bridge with a same-name overload stays callable (#2837)") {
    // javac re-declares each public method of the package-private `Base` in `Pub` as a bridge
    // that calls `Base`'s with invokespecial; `Pub`'s own overloads sit next to those bridges.
    lazy val fixture: Path = compileJava(Seq(
      "p/Base.java" ->
        """package p;
          |class Base {
          |  public String foo(String s) { return "base:" + s; }
          |  public String put(Object o) { return "obj"; }
          |}
          |""".stripMargin,
      "p/Pub.java" ->
        """package p;
          |public class Pub extends Base {
          |  public String foo(Integer i) { return "pub:" + i; }
          |  public String put(String s) { return "str"; }
          |}
          |""".stripMargin
    ))

    def runWith(program: String): Shell.Result =
      Shell(Seq(fixture.toString)).run(program, "VisibilityBridge.on", Array())

    it("calls the inherited foo(String) next to the overload foo(Integer)") {
      assert(runWith(
        """import { p.Pub }
          |def main(args: String[]): String {
          |  val p = new Pub()
          |  return p.foo("s") + "," + p.foo(3)
          |}
          |""".stripMargin) == Shell.Success("base:s,pub:3"))
    }

    it("calls the inherited put(Object) with an Object next to the overload put(String)") {
      assert(runWith(
        """import { p.Pub }
          |def main(args: String[]): String {
          |  val p = new Pub()
          |  val o: Object = 1
          |  return p.put(o) + "," + p.put("x")
          |}
          |""".stripMargin) == Shell.Success("obj,str"))
    }
  }

  private def compileJava(sources: Seq[(String, String)]): Path = {
    val root = Files.createTempDirectory("java-bridge-methods")
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
