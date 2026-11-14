package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/** A generic call whose type argument is fixed through a wildcard parameter
 *  (`Collection[? extends T]`) is not re-fixed by a wider assignment target:
 *  `val o: Object = Collections::max(xs, c)` keeps T = String, as javac infers (#2857). */
class WildcardArgWiderTargetSpec extends AbstractShellSpec {
  private def compileErrors(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(e => s"${e.code} ${e.message}").mkString("\n")
      case _ => ""
    }
  }

  private val prelude =
    """import { java.util.* }
      |def main(args: String[]): String {
      |  val xs: List[String] = new ArrayList[String]()
      |  xs.add("a"); xs.add("b")
      |  val c: Comparator[String] = (a, b) -> a.compareTo(b)
      |""".stripMargin

  describe("a wildcard-fixed type argument assigned to a wider target (#2857)") {
    it("accepts Object as the target") {
      val r = shell.run(prelude +
        """  val o: Object = Collections::max(xs, c)
          |  return "" + o
          |}
          |""".stripMargin, "WiderObject.on", Array())
      assert(Shell.Success("b") == r)
    }
    it("accepts an interface the result implements") {
      val r = shell.run(prelude +
        """  val cs: CharSequence = Collections::max(xs, c)
          |  val k: Comparable[String] = Collections::min(xs, c)
          |  return "" + cs + k
          |}
          |""".stripMargin, "WiderIface.on", Array())
      assert(Shell.Success("ba") == r)
    }
    it("accepts a nullable wider target") {
      val r = shell.run(prelude +
        """  val o: Object? = Collections::max(xs, c)
          |  return "" + o
          |}
          |""".stripMargin, "WiderNullable.on", Array())
      assert(Shell.Success("b") == r)
    }
    it("still rejects a target the result does not fit") {
      for (target <- Seq("JInteger", "Comparable[JInteger]", "List[String]")) {
        val src = prelude.replace("import { java.util.* }", "import { java.util.*; java.lang.Integer as JInteger }") +
          s"""  val o: $target = Collections::max(xs, c)
             |  return "" + o
             |}
             |""".stripMargin
        val msgs = compileErrors(src)
        assert(msgs.contains("E0000") || msgs.contains("E0001"), s"expected an error for <$target>, got: $msgs")
      }
    }
  }
}
