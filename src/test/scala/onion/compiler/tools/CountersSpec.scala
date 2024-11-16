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
          |   static def counter(begin :Int, up :Int) :Counter = 
          |     #Counter.count {
          |       return begin =
          |         begin + up
          |     }
          |
          |   static def main(args: String[]): Int {
          |     c = counter(1, 10)
          |     c.count
          |     c.count
          |     return c.count
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