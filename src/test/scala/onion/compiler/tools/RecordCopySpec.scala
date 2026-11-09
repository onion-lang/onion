package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * Tests for issue #130: record copy with named arguments (partial copy),
 * zero-arg clone, and the original positional form.
 */
class RecordCopySpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("record copy") {
    it("supports partial copy with named arguments") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1, 2)
          |    val q = p.copy(y = 9)
          |    return q.x() + "," + q.y()
          |  }
          |}
          |""".stripMargin,
        "PartialCopy.on",
        Array()
      )
      assert(Shell.Success("1,9") == result)
    }

    it("supports zero-arg clone and full named copy") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1, 2)
          |    return p.copy().y() + "," + p.copy(x = 10, y = 20).x() + "," + p.copy(5, 6).x()
          |  }
          |}
          |""".stripMargin,
        "CloneAndFullCopy.on",
        Array()
      )
      assert(Shell.Success("2,10,5") == result)
    }

    it("reports E0005 for an unknown named component instead of crashing") {
      val results = errors(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    val p = new Point(1, 2)
          |    val q = p.copy(z = 9)
          |  }
          |}
          |""".stripMargin
      )
      val codes = results.map(_._1)
      assert(codes.contains(Some("E0005")))
    }

    it("falls back to the regular call path when positional and named args are mixed") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1, 2)
          |    val q = p.copy(5, y = 9)
          |    return q.x() + "," + q.y()
          |  }
          |}
          |""".stripMargin,
        "MixedArgsCopy.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("accepts a non-null value for a nullable component (#1993)") {
      val result = shell.run(
        """
          |record Cfg(host: String, port: Int?, tag: String?)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = new Cfg("h", null, null)
          |    val a = v.copy(port = 8080)
          |    val b = a.copy(tag = "t")
          |    val c = b.copy(port = null)
          |    return a.port() + "," + b.tag() + "," + c.port() + "," + c.host()
          |  }
          |}
          |""".stripMargin,
        "NullableCopy.on",
        Array()
      )
      assert(Shell.Success("8080,t,null,h") == result)
    }

    it("rejects a named argument whose type does not fit the component (#2705)") {
      // Each one compiled and then threw ClassCastException when the copy ran.
      val cases = Seq(
        "record N(a: Int, b: Long?)" -> "new N(1, 2L).copy(b = \"s\")",
        "record N(a: Int, b: Long?)" -> "new N(1, 2L).copy(b = 1.5)",
        "record N(a: Int, b: String)" -> "new N(1, \"x\").copy(b = 3)",
        "record N(a: Int, b: String?)" -> "new N(1, \"x\").copy(b = 3)"
      )
      for ((decl, call) <- cases) {
        val codes = errors(s"$decl\nval n = $call\nIO::println(n.b())\n").map(_._1)
        // One error at the argument: no second E0043 "unknown parameter name" from
        // the regular named-argument path, which knows nothing of components.
        assert(codes == Seq(Some("E0000")), s"$call gave $codes")
      }
    }

    it("reports an unknown component once (#2705)") {
      val codes = errors("record N(a: Int, b: String)\nval n = new N(1, \"x\").copy(z = 3)\n").map(_._1)
      assert(codes == Seq(Some("E0005")))
    }

    it("still widens, boxes and accepts null for fitting arguments (#2705)") {
      val result = shell.run(
        """
          |record N(a: Int, b: Long?, c: String?, d: Long, e: Object?)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val n = new N(1, 2L, null, 3L, null)
          |    val s: String? = "z"
          |    val b: Long? = n.copy(b = 5).b()
          |    return b + "," + n.copy(b = null).b() + "," + n.copy(c = s).c() + "," +
          |      n.copy(d = 7).d() + "," + n.copy(a = 9).a() + "," + n.copy(e = 5).e() + "," +
          |      n?.copy(c = "q")?.c()
          |  }
          |}
          |""".stripMargin,
        "FittingCopy.on",
        Array()
      )
      assert(Shell.Success("5,null,z,7,9,5,q") == result)
    }

    it("calls a record's own copy overload when the named arguments do not fit the components (#2705)") {
      // A record that declares `copy` gets no generated one; a call that is not
      // component-shaped is an ordinary call to the declared overloads.
      val result = shell.run(
        """
          |record U(name: Int) {
          |public:
          |  def copy(name: String): String = "user:" + name
          |  def copy(tag: Int): String = "tag:" + tag
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val u = new U(1)
          |    return u.copy(name = "s") + "," + u.copy(tag = 3) + "," + u?.copy(name = "t")
          |  }
          |}
          |""".stripMargin,
        "UserCopy.on",
        Array()
      )
      assert(Shell.Success("user:s,tag:3,user:t") == result)
    }
  }
}
