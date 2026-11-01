package onion.compiler.tools

import onion.tools.Shell

/** #2457: `extension List[Int]` and `extension List[String]` may declare the same method name. */
class ExtensionReceiverTypeArgumentSpec extends AbstractShellSpec {

  private def run(src: String): Shell.Result = shell.run(src, "ExtRecvTypeArg.on", Array())

  describe("extension blocks on different instantiations of one class") {
    it("a shared method name picks the block matching the receiver's type argument") {
      val src =
        """extension List[Int] {
          |  def describe(): String = "ints"
          |}
          |extension List[String] {
          |  def describe(): String = "strings"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [1, 2]
          |    val ys: List[String] = ["a"]
          |    return (if xs.describe() == "ints" && ys.describe() == "strings" { 0 } else { 1 })
          |  }
          |}
          |""".stripMargin
      assert(run(src) == Shell.Success(0))
    }

    it("a receiver matching neither block is still an error") {
      val src =
        """extension List[Int] {
          |  def describe(): String = "ints"
          |}
          |extension List[String] {
          |  def describe(): String = "strings"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val zs: List[Long] = [1L]
          |    zs.describe()
          |    return 0
          |  }
          |}
          |""".stripMargin
      assert(run(src) == Shell.Failure(-1))
    }
  }
}
