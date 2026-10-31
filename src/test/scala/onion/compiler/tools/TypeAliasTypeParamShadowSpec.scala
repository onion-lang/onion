package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test for issue #2510: a module `type` alias named like a declaration's own
 * type parameter (`type T = Int` next to `class Box[T]`) won, so inside `Box` the name `T`
 * meant `Int` and `new Box[String]("x")` was E0021 against a constructor `Box(Int)` nobody
 * wrote. A type parameter now shadows a same-named alias inside its declaration, as in Java
 * and Scala; outside it, the alias still applies.
 */
class TypeAliasTypeParamShadowSpec extends AbstractShellSpec {

  private def runMain(body: String): Shell.Result =
    shell.run(body.stripMargin, "None", Array())

  describe("a type parameter named like a module alias") {
    it("shadows the alias inside a generic class") {
      val result = runMain(
        """type T = Int
          |class Box[T] {
          |  val v: T
          |public:
          |  def this(x: T) { v = x }
          |  def get: T = v
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return new Box[String]("x").get()
          |  }
          |}
          |""")
      assert(Shell.Success("x") == result)
    }

    it("shadows the alias inside a generic record") {
      val result = runMain(
        """type T = Int
          |record Box[T](v: T)
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return new Box[String]("y").v()
          |  }
          |}
          |""")
      assert(Shell.Success("y") == result)
    }

    it("shadows the alias inside a generic method") {
      val result = runMain(
        """type T = Int
          |class Main {
          |public:
          |  static def id[T](x: T): T = x
          |  static def main(args: String[]): String {
          |    return id("z")
          |  }
          |}
          |""")
      assert(Shell.Success("z") == result)
    }

    it("shadows a parameterized alias of the same name") {
      val result = runMain(
        """type K[A] = List[A]
          |class Holder[K] {
          |  val k: K
          |public:
          |  def this(x: K) { k = x }
          |  def get: K = k
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return new Holder[String]("w").get()
          |  }
          |}
          |""")
      assert(Shell.Success("w") == result)
    }

    it("still resolves the alias outside the declaration") {
      val result = runMain(
        """type T = Int
          |class Box[T] {
          |  val v: T
          |public:
          |  def this(x: T) { v = x }
          |  def get: T = v
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val n: T = 41
          |    return new Box[String]("x").get().length() + n
          |  }
          |}
          |""")
      assert(Shell.Success(42) == result)
    }
  }
}
