package onion.compiler.tools

import onion.tools.Shell

/** A record component default written as `do[M] { ... }` is desugared like any other default (#3224). */
class DoInRecordComponentDefaultSpec extends AbstractShellSpec {
  describe("a record component default") {
    it("may be a do[Option] block, so a call that omits it compiles and holds the value") {
      val result = shell.run(
        """
          |import { onion.Option }
          |record R(n: Int, o: Option[Int] = do[Option] { x <- Option::some(3); yield x })
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val r: R = new R(1)
          |    return "#{r.n()} #{r.o().get()}"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1 3") == result)
    }
  }
}
