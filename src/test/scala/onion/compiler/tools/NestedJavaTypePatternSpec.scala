package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.parser.{JJOnionParser, OnionParser}
import onion.tools.Shell

import java.io.{ByteArrayOutputStream, StringReader}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import javax.tools.ToolProvider

/**
 * F21: a nested Java type is named with dots (`Result.Ok`, `onion.Result.Ok`,
 * `a.B.C.D`) in a type pattern and in an import, the way Java source names it. The
 * standard library's sealed `Result`/`Outcome`/`Option` interfaces are then matched case
 * by case, with the binding at the scrutinee's type arguments and E0042 exhaustiveness
 * read from the Java class's `permits`.
 *
 * Assertions are on error codes and program output only (never localized text), so the
 * suite passes under both `-Duser.language=en` and `ja`.
 */
class NestedJavaTypePatternSpec extends AbstractShellSpec {

  private def errorCodes(program: String, classpath: Seq[String] = Seq(".")): Seq[String] = {
    val config = new CompilerConfig(classpath, null, "UTF-8", "", 10)
    val inputs = Seq(new StreamInputSource(() => new StringReader(program), "NestedJavaTypes.on"))
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs.flatMap(_.errorCode).toSeq
      case _ => Seq.empty
    }
  }

  private def messages(program: String): Seq[String] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    val inputs = Seq(new StreamInputSource(() => new StringReader(program), "NestedJavaTypes.on"))
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.message).toSeq
      case _ => Seq.empty
    }
  }

  /** Both parsers accept the program and build the same AST; the fast path does not give up on it. */
  private def parity(src: String): Unit = {
    val fast =
      try OnionParser.parse(src)
      catch { case _: OnionParser.Fail => fail(s"the fast-path parser gave up on:\n$src") }
    val jj = new JJOnionParser(new StringReader(src)).unit()
    assert(fast == jj, s"fast-path AST diverged from the JavaCC AST for:\n$src")
  }

  describe("parsing a dotted nested type name") {
    it("reads `case x is Outer.Inner:` the same in both parsers") {
      parity(
        """def f(r: Result[Int, String]): String = select r {
          |  case o is Result.Ok: "ok"
          |  case e is onion.Result.Err: "err"
          |}
          |""".stripMargin)
    }
    it("reads a dotted type before `when`, `&&`, `||`, `->`, `}`, `...` and `?[`") {
      parity(
        """def g(o: Option[String], r: Object): Boolean {
          |  val b = select o {
          |    case s is Option.Some when s.value().isEmpty(): true
          |    else: false
          |  }
          |  val c = r is Map.Entry && b || r is java.util.Map.Entry
          |  val h: Map.Entry[String, Int] -> String = (e) -> e.getKey()
          |  val k = (o as Option.Some)
          |  return c
          |}
          |def v(xs: Map.Entry...): Int = xs.length
          |def w(xs: Map.Entry?[]): Int = xs.length
          |def u(o: Object): Boolean {
          |  if o is Result.Ok { return true }
          |  return o is Result.Err
          |}
          |""".stripMargin)
    }
    it("reads nested-class imports, with and without an alias") {
      parity(
        """import {
          |  onion.Result.Ok
          |  onion.Result.Err as RErr
          |}
          |println("x")
          |""".stripMargin)
    }
  }

  describe("a type pattern naming a nested Java type") {
    it("matches onion.Result case by case, bound at the scrutinee's type arguments") {
      val r = shell.run(
        """def describe(r: Result[Int, String]): String = select r {
          |  case o is Result.Ok:  "ok " + (o.value() + 1)
          |  case e is Result.Err: "err " + e.error().length()
          |}
          |def main(args: String[]): String = describe(Result::ok(41)) + "/" + describe(Result::err("boom"))
          |""".stripMargin, "None", Array())
      assert(r == Shell.Success("ok 42/err 4"))
    }
    it("binds Result.Ok's value at the Ok type argument, so a mismatched use is a type error") {
      val ok = errorCodes(
        """def f(r: Result[Int, String]): Int = select r {
          |  case o is Result.Ok:  { val n: Int = o.value(); n }
          |  case e is Result.Err: { val s: String = e.error(); s.length() }
          |}
          |""".stripMargin)
      assert(ok.isEmpty, s"expected no errors, got: $ok")
      val bad = errorCodes(
        """def f(r: Result[Int, String]): Int = select r {
          |  case o is Result.Ok:  { val s: String = o.value(); 1 }
          |  case e is Result.Err: 0
          |}
          |""".stripMargin)
      assert(bad.contains("E0000"), s"expected E0000 (incompatible type), got: $bad")
    }
    it("accepts the fully qualified spelling onion.Result.Ok") {
      val r = shell.run(
        """def f(r: Result[String, String]): String = select r {
          |  case o is onion.Result.Ok:  o.value()
          |  case e is onion.Result.Err: e.error()
          |}
          |def main(args: String[]): String = f(Result::ok("fq"))
          |""".stripMargin, "None", Array())
      assert(r == Shell.Success("fq"))
    }
    it("matches onion.Outcome.Ok / Outcome.Bad") {
      val r = shell.run(
        """def f(x: Outcome[Int]): String = select x {
          |  case ok is Outcome.Ok:   "ok " + (ok.value() * 2)
          |  case bad is Outcome.Bad: "bad " + bad.defects().size
          |}
          |def main(args: String[]): String = f(Outcome::ok(21))
          |""".stripMargin, "None", Array())
      assert(r == Shell.Success("ok 42"))
    }
    it("matches onion.Option.Some / Option.None, with a guard") {
      val r = shell.run(
        """def f(x: Option[String]): String = select x {
          |  case s is Option.Some when s.value().isEmpty(): "empty"
          |  case s is Option.Some: "some " + s.value().length()
          |  case n is Option.None: "none"
          |}
          |def main(args: String[]): String = f(Option::some("abc")) + "/" + f(Option::some("")) + "/" + f(Option::none())
          |""".stripMargin, "None", Array())
      assert(r == Shell.Success("some 3/empty/none"))
    }
    it("matches the Result an Http request's send() returns") {
      val codes = errorCodes(
        """def fetch(url: String): String = select Http::request("GET", url).send() {
          |  case ok is Result.Ok:  "HTTP " + ok.value().status
          |  case no is Result.Err: "no response: " + no.error().kind()
          |}
          |""".stripMargin)
      assert(codes.isEmpty, s"expected no errors, got: $codes")
    }
  }

  describe("exhaustiveness over a sealed Java interface") {
    it("reports E0042 when Result.Err is missing") {
      val codes = errorCodes(
        """def f(r: Result[Int, String]): String = select r {
          |  case o is Result.Ok: "ok"
          |}
          |""".stripMargin)
      assert(codes.contains("E0042"), s"expected E0042, got: $codes")
      assert(!codes.contains("E0020"), s"a spurious E0020 next to E0042: $codes")
    }
    it("reports E0042 when Option.None is missing, and a guarded case does not count") {
      val codes = errorCodes(
        """def f(x: Option[String]): String = select x {
          |  case s is Option.Some: "some"
          |}
          |def g(x: Option[String]): String = select x {
          |  case s is Option.Some: "some"
          |  case n is Option.None when true: "none"
          |}
          |""".stripMargin)
      assert(codes.count(_ == "E0042") == 2, s"expected two E0042, got: $codes")
    }
    it("names the missing nested case in the message") {
      val msgs = messages(
        """def f(r: Outcome[Int]): String = select r {
          |  case o is Outcome.Ok: "ok"
          |}
          |""".stripMargin)
      assert(msgs.exists(_.contains("Outcome.Bad")), s"expected the missing Outcome.Bad to be named: $msgs")
    }
    it("leaves a non-sealed Java interface alone: no E0042, no exhaustiveness") {
      val codes = errorCodes(
        """def f(x: java.util.List[String]): String = select x {
          |  case a is java.util.ArrayList: "array"
          |  else: "other"
          |}
          |""".stripMargin)
      assert(codes.isEmpty, s"expected no errors, got: $codes")
    }
  }

  describe("importing a nested Java class by its dotted name") {
    it("resolves `import { onion.Result.Ok }` and an aliased `onion.Result.Err as RErr`") {
      val r = shell.run(
        """import {
          |  onion.Result.Ok
          |  onion.Result.Err as RErr
          |}
          |def f(r: Result[Int, String]): String = select r {
          |  case o is Ok:   "ok " + o.value()
          |  case e is RErr: "err " + e.error()
          |}
          |def main(args: String[]): String = f(Result::ok(1)) + "/" + f(Result::err("x"))
          |""".stripMargin, "None", Array())
      assert(r == Shell.Success("ok 1/err x"))
    }
    it("lets an imported nested class name a variable's type and a static call receiver") {
      val r = shell.run(
        """import { java.util.AbstractMap.SimpleEntry }
          |def main(args: String[]): String {
          |  val e: SimpleEntry[String, Int] = new SimpleEntry[String, Int]("k", 3)
          |  return e.getKey() + e.getValue()
          |}
          |""".stripMargin, "None", Array())
      assert(r == Shell.Success("k3"))
    }
  }

  describe("an unknown nested name") {
    it("is E0003 in a type pattern, suggesting the outer class's members") {
      val program =
        """def f(r: Result[Int, String]): String = select r {
          |  case o is Result.Okk: "ok"
          |  else: "other"
          |}
          |""".stripMargin
      assert(errorCodes(program).contains("E0003"))
      assert(messages(program).exists(_.contains("Result.Ok")), "expected a Result.Ok suggestion")
    }
    it("is E0003 in an import, suggesting the outer class's members") {
      val program =
        """import { onion.Result.Errr }
          |println("x")
          |""".stripMargin
      assert(errorCodes(program).contains("E0003"))
      assert(messages(program).exists(_.contains("onion.Result.Err")), "expected an onion.Result.Err suggestion")
    }
    it("is E0003 under an outer name that is not a class at all") {
      assert(errorCodes(
        """def f(o: Object): Boolean = o is NoSuchOuter.Inner
          |""".stripMargin).contains("E0003"))
    }
  }

  describe("a user-written nested Java hierarchy on the classpath") {
    lazy val fixture: Path = compileJava(Seq(
      "fx/Geometry.java" ->
        """package fx;
          |public final class Geometry {
          |  private Geometry() {}
          |  public sealed interface Shape<T> permits Shape.Circle, Shape.Square, Shape.Poly {
          |    record Circle<T>(T radius) implements Shape<T> {}
          |    record Square<T>(T side) implements Shape<T> {}
          |    sealed interface Poly<T> extends Shape<T> permits Poly.Tri, Poly.Quad {
          |      record Tri<T>(T base) implements Poly<T> {}
          |      record Quad<T>(T base) implements Poly<T> {}
          |    }
          |  }
          |  public static <T> Shape<T> circle(T r) { return new Shape.Circle<>(r); }
          |  public static <T> Shape<T> square(T s) { return new Shape.Square<>(s); }
          |  public static <T> Shape<T> tri(T b) { return new Shape.Poly.Tri<>(b); }
          |}
          |""".stripMargin
    ))

    it("matches deeply nested records, binding their generic accessors") {
      val r = Shell(Seq(fixture.toString)).run(
        """import { fx.Geometry }
          |def size(s: Geometry.Shape[Int]): Int = select s {
          |  case c is Geometry.Shape.Circle:      c.radius() * 3
          |  case q is fx.Geometry.Shape.Square:   q.side() * q.side()
          |  case t is Geometry.Shape.Poly.Tri:    t.base() + 100
          |  case u is Geometry.Shape.Poly.Quad:   u.base() + 200
          |}
          |def main(args: String[]): String = size(Geometry::circle(2)) + "/" + size(Geometry::square(3)) + "/" + size(Geometry::tri(1))
          |""".stripMargin, "None", Array())
      assert(r == Shell.Success("6/9/101"))
    }
    it("covers a nested sealed subtype by its own type or by all of its cases") {
      val byType = errorCodes(
        """import { fx.Geometry }
          |def f(s: Geometry.Shape[Int]): String = select s {
          |  case c is Geometry.Shape.Circle: "c"
          |  case q is Geometry.Shape.Square: "s"
          |  case p is Geometry.Shape.Poly:   "p"
          |}
          |""".stripMargin, Seq(fixture.toString))
      assert(byType.isEmpty, s"expected no errors, got: $byType")
      val missing = errorCodes(
        """import { fx.Geometry }
          |def f(s: Geometry.Shape[Int]): String = select s {
          |  case c is Geometry.Shape.Circle:   "c"
          |  case q is Geometry.Shape.Square:   "s"
          |  case t is Geometry.Shape.Poly.Tri: "t"
          |}
          |""".stripMargin, Seq(fixture.toString))
      assert(missing.contains("E0042"), s"expected E0042 for the missing Poly.Quad, got: $missing")
    }
    it("names the uncovered leaf of a nested sealed subtype") {
      val config = new CompilerConfig(Seq(fixture.toString), null, "UTF-8", "", 10)
      val program =
        """import { fx.Geometry }
          |def f(s: Geometry.Shape[Int]): String = select s {
          |  case c is Geometry.Shape.Circle:   "c"
          |  case q is Geometry.Shape.Square:   "s"
          |  case t is Geometry.Shape.Poly.Tri: "t"
          |}
          |""".stripMargin
      val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(program), "Leaf.on"))) match {
        case CompilationOutcome.Failure(errs) => errs.map(_.message).toSeq
        case _ => Seq.empty
      }
      assert(msgs.exists(_.contains("Quad")), s"expected Quad to be named: $msgs")
    }

    // Package `amb.box` holds a class `Item`; class `amb.box` holds member classes `Item`
    // and `Only`. Java reads `amb.box.Item` as the package's class (the longest package
    // prefix wins) and `amb.box.Only` as the member class, and so does Onion.
    lazy val ambiguous: Seq[String] = Seq(
      compileJava(Seq("amb/box/Item.java" ->
        """package amb.box;
          |public final class Item {
          |  public static Object make() { return new Item(); }
          |  public String tag() { return "package class"; }
          |}
          |""".stripMargin)).toString,
      compileJava(Seq("amb/box.java" ->
        """package amb;
          |public final class box {
          |  public static final class Item {
          |    public static Object make() { return new Item(); }
          |    public String tag() { return "member Item"; }
          |  }
          |  public static final class Only {
          |    public static Object make() { return new Only(); }
          |    public String tag() { return "member Only"; }
          |  }
          |}
          |""".stripMargin)).toString
    )

    it("resolves an ambiguous a.b.C to the longest package prefix, then to a member class") {
      val r = Shell(ambiguous).run(
        """import { amb.box.Only as O }
          |def tagOf(o: Object): String = select o {
          |  case i is amb.box.Item: i.tag()
          |  case n is amb.box.Only: n.tag()
          |  else: "?"
          |}
          |def main(args: String[]): String = tagOf(amb.box.Item::make()) + "/" + tagOf(amb.box.Only::make()) + "/" + tagOf(O::make())
          |""".stripMargin, "None", Array())
      assert(r == Shell.Success("package class/member Only/member Only"))
    }
  }

  private def compileJava(sources: Seq[(String, String)]): Path = {
    val root = Files.createTempDirectory("nested-java-types")
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
    val args = Seq("-d", classes.toString, "-cp", classes.toString) ++ files
    val exit = compiler.run(null, null, errors, args*)
    assert(exit == 0, errors.toString(UTF_8))
    classes
  }
}
