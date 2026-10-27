package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome, WarningLevel}
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

    for (fnFirst <- Seq(true, false)) {
      it(s"reports a wrong-arity lambda against the function overload, not Int (function ${if (fnFirst) "first" else "last"})") {
        val fn = "  def g3(f: (Int) -> String): String = \"fn:\" + f(1)"
        val int = "  def g3(s: Int): String = \"int:\" + s"
        val decls = Seq("extension String {", if (fnFirst) fn else int, if (fnFirst) int else fn, "}").mkString("\n")
        assert(errorCodes(decls + "\nIO::println(\"a\".g3((a: Int, b: Int) -> \"x\"))\n") == Seq(Some("E0101")))
        assert(errorCodes(decls + "\nIO::println(\"a\".g3((a, b) -> \"x\"))\n") == Seq(Some("E0101")))
      }
    }

    // The panel's cases on 268e9cd: non-SAM interfaces are no lambda slot, and SAM vs SAM is
    // decided by the body's void/value shape (rules 1, 2 and 3b on #2447).
    for (first <- Seq(true, false)) {
      val order = if (first) "function first" else "function last"
      for ((tpe, call) <- Seq("List[Int]" -> "\"a\".k((a: Int, b: Int) -> \"x\")", "CharSequence" -> "\"a\".k { a, b -> \"x\" }")) {
        it(s"reports a wrong-arity lambda as E0101 next to a $tpe overload ($order)") {
          val fn = "  def k(f: (Int) -> String): String = \"fn\""
          val other = s"  def k(xs: $tpe): String = \"other\""
          val decls = Seq("extension String {", if (first) fn else other, if (first) other else fn, "}").mkString("\n")
          assert(errorCodes(decls + s"\nIO::println($call)\n") == Seq(Some("E0101")))
        }
      }

      it(s"sends a value block to Callable and a void block to Runnable (${if (first) "Runnable" else "Callable"} first)") {
        val run = "  def k(r: Runnable): String = \"run\""
        val call = "  def k(c: Callable[Integer]): String = \"call\""
        val decls = Seq("import { java.util.concurrent.Callable }", "extension String {",
          if (first) run else call, if (first) call else run, "}").mkString("\n")
        assert(Shell.Success("call,run") == runMain(decls, """"a".k { 42 } + "," + "a".k { IO::print("") }"""))
      }

      it(s"sends a value block to Supplier over Runnable (${if (first) "Runnable" else "Supplier"} first)") {
        val run = "  def k(r: Runnable): String = \"run\""
        val sup = "  def k(s: Supplier[String]): String = \"sup\""
        val decls = Seq("import { java.util.function.Supplier }", "extension String {",
          if (first) run else sup, if (first) sup else run, "}").mkString("\n")
        assert(Shell.Success("sup") == runMain(decls, """"a".k { "v" }"""))
      }
    }

    // The panel's cases on 631679d: non-generic over generic once the signatures tie, and
    // nested lambdas next to an Object overload stay linear.
    for (genericFirst <- Seq(true, false)) {
      it(s"picks each2(String, g) over each2[A](A, g) for a typed lambda (generic ${if (genericFirst) "first" else "last"})") {
        val gen = "  def each2[A](z: A, f: (A) -> void): String = \"gen\""
        val str = "  def each2(s: String, f: (String) -> void): String = \"str\""
        val decls = Seq("extension String {", if (genericFirst) gen else str, if (genericFirst) str else gen, "}").mkString("\n")
        assert(Shell.Success("str") == runMain(decls, """"a".each2("q", (s: String) -> IO::print(""))"""))
      }
    }

    it("types nested lambdas next to an Object overload in linear time") {
      val depth = 12
      val call = (1 to depth).foldRight("s" + depth) { (i, inner) => s"\"a\".w((s$i: String) -> $inner)" }
      val decls =
        """extension String {
          |  def w(o: Object): String = "o"
          |  def w(f: (String) -> String): String = f("x")
          |}""".stripMargin
      val start = System.nanoTime()
      val result = runMain(decls, call)
      val seconds = (System.nanoTime() - start) / 1e9
      assert(Shell.Success("x") == result)
      assert(seconds < 30, s"took $seconds s")
    }

    // The panel's cases on 56af12b: a call site's remembered choice is keyed by what the
    // call sees, so a captured variable typed differently by an enclosing overload counts.
    for (stringFirst <- Seq(true, false)) {
      val order = if (stringFirst) "String block first" else "Object block first"
      it(s"picks the inner String-receiver overload once the captured p is a String ($order)") {
        val str = """extension String {
                    |  def inner(f: (Int) -> String): String = "str:" + f(1)
                    |  def outer(f: (String) -> String): String = "os:" + f("s")
                    |}""".stripMargin
        val obj = """extension Object {
                    |  def inner(f: (Int) -> Object): String = "obj:" + f(1)
                    |  def outer(f: (Int) -> String): String = "oo:" + f(1)
                    |}""".stripMargin
        val decls = if (stringFirst) str + "\n" + obj else obj + "\n" + str
        assert(Shell.Success("os:str:s") == runMain(decls, """"a".outer { p -> val r = "x".inner { q -> p }; r }"""))
      }

      it(s"keeps an inner choice whether the captured p is typed or inferred ($order)") {
        val n = Seq("  def n(o: Object): String = \"nobj\"", "  def n(f: (Int) -> Int): String = \"nfn\"")
        val o = Seq("  def o(s: String, f: (String) -> String): String = \"oS:\" + f(\"s\")",
          "  def o(i: Int, f: (Int) -> String): String = \"oI:\" + f(1)")
        val members = if (stringFirst) n ++ o else (n ++ o).reverse
        val decls = ("extension String {" +: members :+ "}").mkString("\n")
        assert(Shell.Success("oI:nfn,oI:nfn") == runMain(decls,
          """"a".o(1) { p -> "a".n((x: Int) -> p) } + "," + "a".o(1, (p: Int) -> "a".n((x: Int) -> p))"""))
      }
    }

    it("reports a warning inside a lambda once, however often overloads try it") {
      val src =
        """extension String {
          |  def g(o: Object): String = "obj"
          |  def g(f: (String) -> String): String = "fn:" + f("x")
          |}
          |IO::println("a".g((s: String) -> s.g((u: String) -> {
          |  val t: String = null
          |  u + t
          |})))
          |""".stripMargin
      val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
      val result = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "w.on")))
      val w12 = result.diagnostics.warnings.filter(_.category.code == "W0012")
      assert(w12.length == 1, s"got: ${w12.map(w => s"${w.location} ${w.message}")}")
    }

    // The panel's cases on 10c7f36: an inner choice is reused only under the same expected
    // type, and a try against an overload that is not chosen leaves no warning behind.
    for (objectFirst <- Seq(true, false); (dType, dArg) <- Seq("Object" -> "\"s\"", "Int" -> "5")) {
      it(s"picks outer by whether the inner call fits its expected type (d: $dType, ${if (objectFirst) "Integer-returning first" else "String-returning first"})") {
        val oi = s"  def outer(d: $dType, f: (Int) -> Integer): String = \"oI:\" + f(1)"
        val os = "  def outer(d: String, f: (Int) -> String): String = \"oS:\" + f(1)"
        val decls = Seq(
          "extension Object {",
          "  def inner(f: (String) -> String): String = \"n\"",
          "}",
          "extension String {",
          "  def inner[A](f: (A) -> String): A = null",
          if (objectFirst) oi else os,
          if (objectFirst) os else oi,
          "}").mkString("\n")
        assert(Shell.Success("oI:null") == runMain(decls, s"\"a\".outer($dArg, { p -> \"x\".inner { q -> \"v\" } })"))
      }
    }

    it("reports only the chosen overload's warning") {
      val src =
        """import { java.util.function.Function as JFn }
          |extension String {
          |  def outer(f: JFn[String, String]): String = "os"
          |  def outer(f: (Int) -> Object): String = "oi"
          |}
          |IO::println("a".outer { p -> null })
          |""".stripMargin
      val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
      val result = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "w.on")))
      val w12 = result.diagnostics.warnings.filter(_.category.code == "W0012")
      assert(w12.length == 1 && w12.head.message.contains("java.lang.Object"), s"got: ${w12.map(_.message)}")
    }

    // The panel's cases on 4ed8553: nesting stays fast for three SAM overloads and on the
    // class path, and this path's E0006 says how to pick an overload.
    def timed(src: String): (Seq[Option[String]], Double) = {
      val start = System.nanoTime()
      val codes = errorCodes(src)
      (codes, (System.nanoTime() - start) / 1e9)
    }

    it("types nested lambdas against three SAM overloads in linear time") {
      val inner = (1 to 16).foldLeft("\"z\" + x1") { (body, i) => s"\"a\".g { x$i -> $body }" }
      val (codes, seconds) = timed(
        s"""import { java.util.function.Function as JFn; java.util.function.IntFunction }
           |extension String {
           |  def g(f: JFn[String, String]): String = "fs:" + f.apply("x")
           |  def g(f: IntFunction[String]): String = "fi:" + f.apply(1)
           |  def g(f: Runnable): String = "r"
           |}
           |IO::println($inner)
           |""".stripMargin)
      assert(codes.nonEmpty)
      assert(seconds < 30, s"took $seconds s")
    }

    it("types nested lambdas on a class method in linear time") {
      val inner = (1 to 18).foldLeft("\"z\"") { (body, i) => s"s.g((x$i: String) -> s.g { x$i -> $body })" }
      val (codes, seconds) = timed(
        s"""import { java.util.function.Function as JFn; java.util.function.IntFunction }
           |class S { public:
           |  def g(f: JFn[String, String]): String = "fs:" + f.apply("x")
           |  def g(f: IntFunction[Integer]): String = "fi:" + f.apply(1)
           |}
           |val s = new S()
           |IO::println($inner)
           |""".stripMargin)
      assert(codes.nonEmpty)
      assert(seconds < 30, s"took $seconds s")
    }

    it("gives this path's E0006 a hint at the fix") {
      val src =
        """extension String {
          |  def c(n: Long, g: (Long) -> Long): String = "long:" + g(n)
          |  def c(n: Object, g: (Long) -> Long): String = "obj"
          |}
          |IO::println("a".c(5, (x: Long) -> x * 2L))
          |""".stripMargin
      val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
      new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "h.on"))) match {
        case CompilationOutcome.Failure(errs) =>
          assert(errs.map(_.errorCode) == Seq(Some("E0006")))
          assert(errs.head.message.contains("5L"), errs.head.message)
        case other => fail(s"expected E0006, got $other")
      }
    }

    // The panel's case on b191766: an untyped lambda's own position does not rank, so the
    // other argument decides (rule 4 on #2447), as on the class path.
    for (longFirst <- Seq(true, false)) {
      it(s"lets the Int argument pick m(Int, f) for an untyped lambda (${if (longFirst) "Long" else "Int"} first)") {
        val l = "  def m(n: Long, f: (Long) -> Long): String = \"long:\" + f(n)"
        val i = "  def m(n: Int, f: (Int) -> Int): String = \"int:\" + f(n)"
        val decls = Seq("extension String {", if (longFirst) l else i, if (longFirst) i else l, "}").mkString("\n")
        assert(Shell.Success("int:1,int:1,long:1") ==
          runMain(decls, """"a".m(1, { x -> x }) + "," + "a".m(1, (x: Int) -> x) + "," + "a".m(1L, { x -> x })"""))
      }
    }

    it("types nested lambdas once per overload that can take them") {
      val depth = 10
      val call = (1 to depth).foldRight("s" + depth) { (i, inner) => s"\"a\".w((s$i: String) -> $inner)" }
      val decls =
        """extension String {
          |  def w(n: Int): String = "i"
          |  def w(b: Boolean): String = "b"
          |  def w(f: (String) -> String): String = f("x")
          |}""".stripMargin
      val start = System.nanoTime()
      val result = runMain(decls, call)
      val seconds = (System.nanoTime() - start) / 1e9
      assert(Shell.Success("x") == result)
      assert(seconds < 30, s"took $seconds s")
    }

    // A typed inner lambda that captures the outer parameter: its body's type depends on
    // the overload the outer lambda is tried against, so it must be typed again for each.
    val fnOuter = "  def outer(f: (Int) -> Int): String = \"oi:\" + f(1)"
    val jfnOuter = "  def outer(f: JFn[String, String]): String = \"os:\" + f.apply(\"s\")"
    for ((label, first, second) <- Seq(("function first", fnOuter, jfnOuter), ("SAM first", jfnOuter, fnOuter))) {
      it(s"types a capturing inner lambda against each outer overload ($label)") {
        val result = runMain(
          s"""import { java.util.function.Function as JFn }
             |extension String {
             |$first
             |$second
             |  def inner(f: (Int) -> String): String = "ext:" + f(1)
             |}
             |def inner(f: (Int) -> Object): String = "fn:" + f(1)
             |class K {
             |public:
             |  def inner(f: (Int) -> Object): String = "k:" + f(1)
             |}
             |""".stripMargin,
          """"a".outer { p -> inner((q: Int) -> p) } + "," +
            |      "a".outer { p -> "a".inner((q: Int) -> p) } + "," +
            |      "a".outer { p -> new K().inner((q: Int) -> p) }""".stripMargin
        )
        assert(Shell.Success("os:fn:s,os:ext:s,os:k:s") == result)
      }
    }

    it("passes an Int literal beside a lambda to the Int overload, not a narrower one") {
      for (order <- Seq(true, false)) {
        val short = """  def h(x: Short, f: (Int) -> Int): String = "short" + f(1)"""
        val int = """  def h(x: Int, f: (Int) -> Int): String = "int" + f(1)"""
        val byte = """  def b(x: Byte, f: (Int) -> Int): String = "byte" + f(1)"""
        val long = """  def b(x: Long, f: (Int) -> Int): String = "long" + f(1)"""
        val obj = """  def o(x: Object, f: (Int) -> Int): String = "obj" + f(1)"""
        val shortO = """  def o(x: Short, f: (Int) -> Int): String = "short" + f(1)"""
        val decls = (if (order) Seq(short, int, byte, long, shortO, obj) else Seq(int, short, long, byte, obj, shortO))
        val result = runMain(
          decls.mkString("extension String {\n", "\n", "\n}"),
          """"a".h(5, { n -> n }) + "," + "a".h(5, (n: Int) -> n) + "," + "a".b(100, (n: Int) -> n) + "," +
            |      "a".o(5, (n: Int) -> n) + "," + "a".h(5 as Short, (n: Int) -> n)""".stripMargin
        )
        assert(Shell.Success("int1,int1,long1,obj1,short1") == result, s"declaration order $order")
      }
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
