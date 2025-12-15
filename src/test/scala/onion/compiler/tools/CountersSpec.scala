package onion.compiler.tools

import onion.tools.Shell

class CountersSpec extends AbstractShellSpec {
  describe("Counters class") {
    it("creates a Counter using closure") {
      val result = shell.run(
        """
          | interface Counter {
          |   def count :Int
          | }
          |
          | class Counters {
          | public:
          |   static def counter(begin :Int, up :Int) :() -> JInteger =
          |     () -> {
          |       begin = begin + up
          |       return new JInteger(begin);
          |     }
          |
          |   static def main(args: String[]): Int {
          |     val c: () -> JInteger = counter(1, 10)
          |     c.call().intValue()
          |     c.call().intValue()
          |     return c.call().intValue()
          |   }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(31) == result)
    }
  }
}
