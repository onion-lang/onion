package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A lambda passed to an overloaded extension method is typed against the overload that
 * can take a lambda, whatever the declaration order, as for class methods (#2447).
 */
class ExtensionOverloadLambdaSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def runMain(decls: String, body: String): Shell.Result =
    shell.run(
      s"""
         |$decls
         |class Main {
         |public:
         |  static def main(args: String[]): String {
         |    return $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("a lambda argument to an overloaded extension") {
    it("picks the function overload when it is declared first") {
      val result = runMain(
        """extension String {
          |  def g3(f: (Int) -> String): String = "fn:" + f(1)
          |  def g3(s: Int): String = "int:" + s
          |}""".stripMargin,
        """"a".g3((n: Int) -> "n" + n) + "," + "a".g3(5)"""
      )
      assert(Shell.Success("fn:n1,int:5") == result)
    }

    it("picks the function overload when it is declared last") {
      val result = runMain(
        """extension String {
          |  def g3(s: Int): String = "int:" + s
          |  def g3(f: (Int) -> String): String = "fn:" + f(1)
          |}""".stripMargin,
        """"a".g3((n: Int) -> "n" + n) + "," + "a".g3(5)"""
      )
      assert(Shell.Success("fn:n1,int:5") == result)
    }

    it("tells a function slot from a String slot after a shared first parameter") {
      val result = runMain(
        """extension String {
          |  def g4(xs: List[Int], f: (Int) -> String): String = "fn:" + f(xs[0])
          |  def g4(xs: List[Int], s: String): String = "str:" + s
          |}""".stripMargin,
        """"a".g4([7], (n: Int) -> "n" + n) + "," + "a".g4([7], "q")"""
      )
      assert(Shell.Success("fn:n7,str:q") == result)
    }

    it("takes a trailing lambda next to a String overload") {
      val result = runMain(
        """extension String {
          |  def on(f: () -> String): String = "fn:" + f()
          |  def on(s: String): String = "str:" + s
          |}""".stripMargin,
        """"a".on { "lam" } + "," + "a".on("v")"""
      )
      assert(Shell.Success("fn:lam,str:v") == result)
    }

    // The panel's cases on #2452: each must print what the class version prints.
    val neighbours = Seq(
      "Int" -> "\"int\"", "String" -> "\"str\"", "Object" -> "\"obj\"",
      "List[Int]" -> "\"list\"", "Runnable" -> "\"run\"", "Comparable[String]" -> "\"cmp\""
    )
    for ((tpe, value) <- neighbours; fnFirst <- Seq(true, false)) {
      it(s"picks (Int) -> String over a $tpe overload, function declared ${if (fnFirst) "first" else "last"}") {
        val fn = "  def t(f: (Int) -> String): String = \"fn:\" + f(1)"
        val other = s"  def t(o: $tpe): String = $value"
        val decls = Seq("extension String {", if (fnFirst) fn else other, if (fnFirst) other else fn, "}").mkString("\n")
        assert(Shell.Success("fn:n1") == runMain(decls, """"a".t((n: Int) -> "n" + n)"""))
      }
    }

    it("picks the 2-parameter function overload for a 2-parameter lambda") {
      val result = runMain(
        """extension String {
          |  def h(f: (Int) -> String): String = "fn1:" + f(1)
          |  def h(f: (Int, Int) -> String): String = "fn2:" + f(1, 2)
          |}""".stripMargin,
        """"a".h((a: Int, b: Int) -> "ab" + a + b) + "," + "a".h((n: Int) -> "n" + n)"""
      )
      assert(Shell.Success("fn2:ab12,fn1:n1") == result)
    }

    it("takes a trailing lambda over an Object overload declared first") {
      val result = runMain(
        """extension String {
          |  def on(o: Object): String = "obj"
          |  def on(f: () -> String): String = "fn:" + f()
          |}""".stripMargin,
        """"x".on { "lam" } + "," + "x".on("s")"""
      )
      assert(Shell.Success("fn:lam,obj") == result)
    }

    it("infers an untyped trailing lambda's parameter next to an Object or Runnable overload") {
      val result = runMain(
        """extension String {
          |  def a(o: Object): String = "obj"
          |  def a(f: (Int) -> Int): String = "fn:" + f(2)
          |  def b(r: Runnable): String = "run"
          |  def b(f: (Int) -> Int): String = "fn:" + f(4)
          |}""".stripMargin,
        """"x".a { n -> n * 10 } + "," + "x".b { n -> n * 10 }"""
      )
      assert(Shell.Success("fn:20,fn:40") == result)
    }

    // The panel's cases on 6b143c6: the receiver is ranked too, and a function type
    // beats a SAM interface, in either declaration order.
    for (stringFirst <- Seq(true, false)) {
      val order = if (stringFirst) "String block first" else "Object block first"
      it(s"picks the String receiver's extension over Object's for a lambda ($order)") {
        val str = "extension String { def tap2(f: (Object) -> void): String = \"string\" }"
        val obj = "extension Object { def tap2(f: (Object) -> void): String = \"object\" }"
        val decls = if (stringFirst) str + "\n" + obj else obj + "\n" + str
        assert(Shell.Success("string") == runMain(decls, """"abc".tap2 { x -> IO::print("") }"""))
      }

      it(s"picks the List receiver's extension over Collection's for a lambda ($order)") {
        val list = "extension List { def each2(f: (Object) -> void): String = \"list\" }"
        val coll = "extension Collection { def each2(f: (Object) -> void): String = \"collection\" }"
        val decls = if (stringFirst) list + "\n" + coll else coll + "\n" + list
        assert(Shell.Success("list") == runMain(decls, """["a"].each2 { x -> IO::print("") }"""))
      }

      it(s"picks () -> void over Runnable for a block lambda (${if (stringFirst) "function" else "Runnable"} first)") {
        val fn = "  def k(f: () -> void): String = \"f\""
        val run = "  def k(r: Runnable): String = \"r\""
        val decls = Seq("extension String {", if (stringFirst) fn else run, if (stringFirst) run else fn, "}").mkString("\n")
        assert(Shell.Success("f") == runMain(decls, """"a".k { IO::print("") }"""))
      }
    }

    // The panel's cases on b7deb07, ranked by the rule written on #2447.
    for (intFirst <- Seq(true, false)) {
      val order = if (intFirst) "Int first" else "Int last"
      it(s"picks f(n: Int, g) over f(n: Object, g) for an Int argument and a lambda ($order)") {
        val i = "  def f(n: Int, g: (Int) -> Int): String = \"int:\" + g(1)"
        val o = "  def f(n: Object, g: (Int) -> Int): String = \"obj:\" + g(1)"
        val decls = Seq("extension String {", if (intFirst) i else o, if (intFirst) o else i, "}").mkString("\n")
        assert(Shell.Success("int:1") == runMain(decls, """"a".f(5, (x: Int) -> x)"""))
      }

      it(s"picks fold2(z: Int, g) over fold2[A](z: A, g) ($order)") {
        val i = "  def fold2(z: Int, g: (Int) -> Int): String = \"int:\" + g(z)"
        val a = "  def fold2[A](z: A, g: (A) -> A): String = \"gen:\" + g(z)"
        val decls = Seq("extension String {", if (intFirst) i else a, if (intFirst) a else i, "}").mkString("\n")
        assert(Shell.Success("int:1004") == runMain(decls, """"a".fold2(1000, (x: Int) -> x + 4)"""))
      }

      it(s"lets the receiver decide between (String) -> void and (Object) -> void (${if (intFirst) "String" else "Object"} block first)") {
        val str = "extension String { def m(f: (String) -> void): String = \"string\" }"
        val obj = "extension Object { def m(f: (Object) -> void): String = \"object\" }"
        val decls = if (intFirst) str + "\n" + obj else obj + "\n" + str
        assert(Shell.Success("string,string") ==
          runMain(decls, """"abc".m { x -> IO::print("") } + "," + "abc".m((s: String) -> IO::print(""))"""))
      }
    }

    it("is one E0006 when the parameters and the receivers disagree") {
      val codes = errorCodes(
        """extension Object { def z(f: (Object) -> void): String = "objfn" }
          |extension String { def z(o: Object): String = "strobj" }
          |IO::println("abc".z((x: Object) -> IO::print("")))
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0006")))
    }

    it("reports a lambda that fits nothing against the overload of its arity") {
      val codes = errorCodes(
        """extension String {
          |  def h(f: (Int) -> String): String = "fn1:" + f(1)
          |  def h(f: (Int, Int) -> String): String = "fn2:" + f(1, 2)
          |}
          |IO::println("a".h((a: Int, b: Int) -> a))
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0000")))
    }

    it("still rejects a lambda where no overload takes one, with one error") {
      val codes = errorCodes(
        """extension String {
          |  def g5(s: Int): String = "int:" + s
          |  def g5(s: String): String = "str:" + s
          |}
          |IO::println("a".g5((n: Int) -> "n" + n))
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0000")))
    }
  }
}
