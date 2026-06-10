package onion.compiler.tools

import onion.tools.Shell

/** Round-4 audit fixes: Rand accepting List, enum constant args rejected. */
class StdlibErgonomicsSpec extends AbstractShellSpec {

  describe("Rand with list literals") {
    it("accepts a list literal in choice and shuffle") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "b", "c"]
          |    val c = Rand::choice(xs)
          |    val s = Rand::shuffle(xs)
          |    return "" + (c != null) + ":" + s.size()
          |  }
          |}
          |""".stripMargin,
        "RandList.on",
        Array()
      )
      assert(Shell.Success("true:3") == result)
    }
  }

  describe("Enum constant arguments") {
    it("rejects constructor arguments on enum constants with a clear error") {
      val result = shell.run(
        """
          |enum Planet { MERCURY(1.0), EARTH(2.0) }
          |class Test {
          |public:
          |  static def main(args: String[]): String { return Planet::EARTH.name() }
          |}
          |""".stripMargin,
        "EnumArgs.on",
        Array()
      )
      assert(result == Shell.Failure(-1))
    }
  }

  describe("Files glob and list") {
    it("globs files and pipelines the result") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    new java.io.File("/tmp/onion-glob-test").mkdirs()
          |    Files::writeText("/tmp/onion-glob-test/a.on", "x")
          |    Files::writeText("/tmp/onion-glob-test/b.on", "y")
          |    Files::writeText("/tmp/onion-glob-test/c.txt", "z")
          |    val found = Files::glob("/tmp/onion-glob-test", "*.on")
          |    Files::delete("/tmp/onion-glob-test/a.on")
          |    Files::delete("/tmp/onion-glob-test/b.on")
          |    Files::delete("/tmp/onion-glob-test/c.txt")
          |    return found.toString()
          |  }
          |}
          |""".stripMargin,
        "GlobBasic.on",
        Array()
      )
      assert(Shell.Success("[a.on, b.on]") == result)
    }
  }
}
