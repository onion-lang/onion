package onion.compiler.tools

import onion.tools.Shell

/**
 * SAM conversion applies to constructor arguments, including a zero-parameter
 * lambda, and disambiguates overloaded constructors by the functional-interface
 * parameter (e.g. Thread(Runnable) vs Thread(String)).
 */
class ConstructorSamSpec extends AbstractShellSpec {
  describe("constructor SAM conversion") {
    it("converts a lambda passed to new Thread(Runnable)") {
      val r = shell.run(
        """
          |def main(args: String[]): String {
          |  val t: Thread = new Thread(() -> { IO::println("ran") })
          |  t.start()
          |  t.join()
          |  return "ok"
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("ok") == r)
    }
    it("still accepts a lambda for an Onion function-typed constructor parameter") {
      val r = shell.run(
        """
          |class Box[T] { val v: T
          |public: def this(x: T) { v = x }
          |  def get(): T = v }
          |def main(args: String[]): Int {
          |  val b = new Box[Function1[Int, Int]]((x: Int) -> x + 1)
          |  return b.get().call(4)
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(5) == r)
    }
  }
}
