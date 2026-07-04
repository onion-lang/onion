package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for issue #282: a cast from one interface type to a sibling
 * interface must be accepted at compile time (per JLS 5.5.1) and checked at
 * runtime, while a provably-impossible cast (interface -> unrelated final class,
 * or between unrelated final classes) must still be rejected.
 */
class SiblingInterfaceCastSpec extends AbstractShellSpec {

  describe("Sibling interface cast (#282)") {

    it("allows casting between sibling interfaces and runs at runtime") {
      val result = shell.run(
        """
          |interface Named { def name(): String }
          |interface Aged { def age(): Int }
          |class Person <: Named, Aged {
          |  var name_: String
          |  var age_: Int
          |public:
          |  def this(n: String, a: Int) { name_ = n; age_ = a }
          |  def name(): String { return name_ }
          |  def age(): Int { return age_ }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val p: Named = new Person("K", 42)
          |    val q: Aged = (p as Aged)
          |    return q.age()
          |  }
          |}
          |""".stripMargin,
        "SiblingInterfaceCast.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("still rejects casting an interface to an unrelated final class") {
      val result = shell.run(
        """
          |interface Named { def name(): String }
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val p: Named = null
          |    val s: String = (p as String)
          |    return 0
          |  }
          |}
          |""".stripMargin,
        "InterfaceAsFinalClass.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("still rejects casting between two unrelated final classes") {
      val result = shell.run(
        """
          |import { java.lang.Integer as JInt }
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: String = "hi"
          |    val i: JInt = (s as JInt)
          |    return 0
          |  }
          |}
          |""".stripMargin,
        "UnrelatedFinalClasses.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }
  }
}
