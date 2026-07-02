package onion.compiler.tools

import onion.tools.Shell

/**
 * A lambda cast to a functional interface with `as` is SAM-converted, e.g.
 * `(() -> ...) as Runnable`.
 */
class CastSamConversionSpec extends AbstractShellSpec {
  describe("SAM conversion in an as-cast") {
    it("casts a zero-arg lambda to Runnable") {
      val r = shell.run(
        """
          |def main(args: String[]): String {
          |  val r = (() -> { IO::println("ran") }) as Runnable
          |  r.run()
          |  return "ok"
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("ok") == r)
    }
    it("casts a two-arg lambda to Comparator and uses it") {
      val r = shell.run(
        """
          |import { java.util.ArrayList; java.util.Collections; java.util.Comparator; }
          |def main(args: String[]): Int {
          |  val xs: ArrayList[Int] = new ArrayList[Int]
          |  xs.add(3); xs.add(1); xs.add(2)
          |  val c = ((a: Int, b: Int) -> a - b) as Comparator[Int]
          |  Collections::sort(xs, c)
          |  return xs.get(0)
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
  }
}
