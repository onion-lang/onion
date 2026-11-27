package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/** A `null` element in a list or map literal never fills a primitive element type (#2895):
  * `val xs: List[Int] = [5, null]` is E0000 at the `null`, not a crash at the first read. */
class ListLiteralNullPrimitiveSpec extends AbstractShellSpec {
  private def outcome(src: String): CompilationOutcome = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on")))
  }

  private def errorCodes(src: String): Seq[String] = outcome(src) match {
    case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
    case _ => Seq.empty
  }

  private def errors(src: String): Seq[onion.compiler.CompileError] = outcome(src) match {
    case CompilationOutcome.Failure(errors) => errors
    case _ => Seq.empty
  }

  describe("a null element in a list literal whose element type is a primitive") {
    it("is E0000 for List[Int]") {
      assert(errorCodes("val xs: List[Int] = [5, null]\nprintln(xs)\n") == Seq("E0000"))
    }
    it("is E0000 when null is the only element") {
      assert(errorCodes("val xs: List[Long] = [null]\nprintln(xs)\n") == Seq("E0000"))
    }
    it("is E0000 next to a generic call") {
      val src = "def ident[T](x: T): T = x\nval xs: List[Int] = [ident(5), null]\nprintln(xs)\n"
      assert(errorCodes(src) == Seq("E0000"))
    }
    it("is E0000 for a nested list") {
      assert(errorCodes("val xs: List[List[Int]] = [[1], [2, null]]\nprintln(xs)\n") == Seq("E0000"))
    }
    it("points at the null and hints at the nullable type argument, with no cast advice") {
      val es = errors("val xs: List[Double] = [1.5,\n  null]\n")
      assert(es.size == 1)
      assert(es.head.location != null && es.head.location.line == 2, es.head.location)
      assert(es.head.message.contains("`Double?`"), es.head.message)
      assert(es.head.message.contains("`List[Double?]`"), es.head.message)
      assert(!es.head.message.contains("cast"), es.head.message)
    }
    it("is a compile error when run") {
      assert(Shell.Failure(-1) == shell.run("val xs: List[Int] = [5, null]\nprintln(xs)\n", "None", Array()))
    }
  }

  describe("a null in a map literal whose key or value type is a primitive") {
    it("is E0000 for a Map[String, Int] value") {
      assert(errorCodes("val m: Map[String, Int] = [\"a\": 1, \"b\": null]\nprintln(m)\n") == Seq("E0000"))
    }
    it("is E0000 for a Map[Int, String] key") {
      assert(errorCodes("val m: Map[Int, String] = [1: \"a\", null: \"b\"]\nprintln(m)\n") == Seq("E0000"))
    }
    it("compiles for Map[String, Int?]") {
      assert(errorCodes("val m: Map[String, Int?] = [\"a\": 1, \"b\": null]\nprintln(m)\n") == Seq.empty)
    }
  }

  describe("a null element that a nullable or reference element type admits") {
    it("compiles for List[Int?]") {
      assert(errorCodes("val xs: List[Int?] = [5, null]\nprintln(xs)\n") == Seq.empty)
    }
    it("compiles for List[String?]") {
      assert(errorCodes("val xs: List[String?] = [\"a\", null]\nprintln(xs)\n") == Seq.empty)
    }
    it("compiles with no expected type") {
      assert(errorCodes("val xs = [5, null]\nprintln(xs)\n") == Seq.empty)
    }
  }

  describe("a literal with a null element and no expected type is a list of nullable (#2998)") {
    it("is not accepted by a List[Int] parameter") {
      val src = "def total(zs: List[Int]): Int = zs[1] + 1\nprintln(total([1, null]))\n"
      assert(errorCodes(src) == Seq("E0005"))
    }
    it("is not assignable to a List[Int] through a val") {
      assert(errorCodes("val ys = [5, null]\nval xs: List[Int] = ys\nprintln(xs)\n") == Seq("E0000"))
    }
    it("is not assignable to a List[String] for strings") {
      assert(errorCodes("val ys = [\"a\", null]\nval xs: List[String] = ys\nprintln(xs)\n") == Seq("E0000"))
    }
    it("is accepted by a List[Int?] parameter") {
      val src = "def n(zs: List[Int?]): Int = zs.size\nprintln(n([1, null]))\n"
      assert(errorCodes(src) == Seq.empty)
    }
    it("keeps a null-free literal non-null") {
      val src = "def total(zs: List[Int]): Int = zs[0]\nprintln(total([1, 2]))\n"
      assert(errorCodes(src) == Seq.empty)
    }
  }

  describe("a map literal with a null value and no expected type is a map of nullable (#3201)") {
    it("is not accepted by a Map[String, Int] parameter") {
      val src = "def t(m: Map[String, Int]): Int = m[\"b\"] + 1\nprintln(t([\"a\": 1, \"b\": null]))\n"
      assert(errorCodes(src) == Seq("E0005"))
    }
    it("is not assignable to a Map[String, Int] through a val") {
      assert(errorCodes("val ys = [\"a\": 1, \"b\": null]\nval m: Map[String, Int] = ys\nprintln(m)\n") == Seq("E0000"))
    }
    it("is accepted by a Map[String, Int?] parameter") {
      val src = "def n(m: Map[String, Int?]): Int = m.size\nprintln(n([\"a\": 1, \"b\": null]))\n"
      assert(errorCodes(src) == Seq.empty)
    }
    it("keeps a null-free map non-null") {
      val src = "def t(m: Map[String, Int]): Int = m[\"a\"]\nprintln(t([\"a\": 1]))\n"
      assert(errorCodes(src) == Seq.empty)
    }
  }
}
