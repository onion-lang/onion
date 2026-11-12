package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * W0011 (#2665): a cast whose target type arguments the JVM cannot check warns at the cast.
 * Before, `o as List[T?]` compiled silently and the program failed later, where an element
 * was read.
 */
class UncheckedCastWarningSpec extends AnyFunSpec {

  private def compile(source: String) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  private def w0011(source: String) = {
    val result = compile(source)
    assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    result.diagnostics.warnings.filter(_.category.code == "W0011")
  }

  private def body(stmts: String): String =
    s"""import { java.util.* }
       |def main(args: String[]): void {
       |  val o: Object = new ArrayList[Object]()
       |  val lo: List[Object] = new ArrayList[Object]()
       |  val ls: List[String] = new ArrayList[String]()
       |  val al: ArrayList[String] = new ArrayList[String]()
       |  $stmts
       |  println(args.length)
       |}
       |""".stripMargin

  describe("W0011 unchecked cast") {
    it("warns at the cast in the issue's repro, naming both types") {
      val ws = w0011(
        """import { java.util.* }
          |class G[T] {
          |public:
          |  def c(o: List[Object], x: List[T?]): void { Collections::copy(x, o as List[T?]) }
          |}
          |def main(args: String[]): void {
          |  val x: List[String?] = new ArrayList[String?](["a"])
          |  new G[String]().c(new ArrayList[Object](), x)
          |  println(args.length)
          |}
          |""".stripMargin)
      assert(ws.size == 1, ws.map(_.message))
      assert(ws.head.location.line == 4, ws.map(_.formatted))
      assert(ws.head.message.contains("List[java.lang.Object]") && ws.head.message.contains("List[T?]"), ws.head.message)
    }

    it("warns for a cast from Object to an applied type, and to a type variable") {
      assert(w0011(body("val a = o as List[String]\n  println(a.size)")).size == 1)
      assert(w0011(body("val b = lo as List[String]\n  println(b.size)")).size == 1)
      assert(w0011(body("val c = o as Map[String, Int?]\n  println(c.size)")).size == 1)
      val tv =
        """class Box[T] {
          |public:
          |  def get(o: Object): T = o as T
          |}
          |println(new Box[String]().get("x"))
          |""".stripMargin
      assert(w0011(tv).size == 1)
      // ... but a value already of type T is not.
      val same =
        """class Box[T] {
          |public:
          |  def id(t: T): T = t as T
          |}
          |println(new Box[String]().id("x"))
          |""".stripMargin
      assert(w0011(same).isEmpty)
    }

    it("does not warn when the run-time check is the whole check") {
      // Object arguments, raw classes, plain classes and primitives: the class test is all.
      assert(w0011(body("val a = o as List[Object]\n  val b = o as List[Object?]\n  val c = o as String\n  val d = o as ArrayList\n  println(a.size + b.size + c.length + d.size)")).isEmpty)
      // An upcast or the same type takes nothing on trust.
      assert(w0011(body("val a = al as List[String]\n  val b = ls as List[String]\n  val c = (ls as Object)\n  println(a.size + b.size + c.hashCode)")).isEmpty)
      // A downcast whose arguments follow from the source's.
      assert(w0011(body("val a = ls as ArrayList[String]\n  println(a.size)")).isEmpty)
      // An empty literal holds no element to take on trust.
      assert(w0011(body("val a = [] as List[String]\n  val b = [:] as Map[String, Int]\n  println(a.size + b.size)")).isEmpty)
      // A non-empty one does: `[o] as List[String]` would put an Object in a List[String].
      assert(w0011(body("val a = [o] as List[String]\n  println(a.size)")).size == 1)
      assert(w0011(body("val n = (5 as Object)\n  val i = n as Int\n  println(i)")).isEmpty)
    }

    it("does not warn for casts the compiler writes itself (a json shape's build lambda)") {
      val shape =
        """record S(tags: List[String], name: String) { shape doc = json }
          |println(S::doc().parse("{\"tags\": [\"a\"], \"name\": \"n\"}").get().name())
          |""".stripMargin
      assert(w0011(shape).isEmpty)
    }

    it("names the file of the cast, in a one-file and a two-file build") {
      // #2848 panel: inside a method body the warning had no file name (`:4:70:`), and with
      // two files it named whichever file was typed first.
      val one = w0011(
        """class Box[T] {
          |public:
          |  def get(o: Object): T = o as T
          |}
          |def f(o: Object): List[String] = o as List[String]
          |println(new Box[String]().get("x"))
          |""".stripMargin)
      assert(one.size == 2, one.map(_.formatted))
      assert(one.forall(_.sourceFile == "W.on"), one.map(_.formatted))
      val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
      val a = "class A {\npublic:\n  static def run(): void { println(\"a\") }\n}\n"
      val b = "class B[T] {\npublic:\n  def get(o: Object): T = o as T\n}\n"
      for (order <- Seq(Seq("B.on" -> b, "A.on" -> a), Seq("A.on" -> a, "B.on" -> b))) {
        val result = new OnionCompiler(config).compileDetailed(order.map { case (name, src) =>
          new StreamInputSource(() => new StringReader(src), name)
        })
        assert(!result.hasErrors, result.allErrors.map(_.message))
        val ws = result.diagnostics.warnings.filter(_.category.code == "W0011")
        assert(ws.size == 1 && ws.head.sourceFile == "B.on", (order.map(_._1), ws.map(_.formatted)))
      }
    }

    it("is silenced by --Wno unchecked-cast") {
      val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On,
        suppressedWarnings = Set(onion.compiler.WarningCategory.UncheckedCast))
      val result = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(
        () => new StringReader(body("val a = o as List[String]\n  println(a.size)")), "W.on")))
      assert(!result.hasErrors)
      assert(result.diagnostics.warnings.forall(_.category.code != "W0011"))
    }
  }
}
