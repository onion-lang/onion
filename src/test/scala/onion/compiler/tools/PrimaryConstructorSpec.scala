package onion.compiler.tools

import onion.tools.Shell

/**
 * Primary constructors: class Point(val x: Int, val y: Int) declares
 * public fields with an auto-generated assigning constructor. var makes
 * the field mutable, plain params are constructor-only, defaults/named
 * arguments work, and ": Super(args)" delegates to the superclass.
 */
class PrimaryConstructorSpec extends AbstractShellSpec {

  describe("Primary constructors") {
    it("declares val params as public final fields") {
      val result = shell.run(
        """
          |class Point(val x: Int, val y: Int) {
          |public:
          |  def dist(): Int { return this.x * this.x + this.y * this.y }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(3, 4)
          |    return "" + p.x + "," + p.y + " d=" + p.dist()
          |  }
          |}
          |""".stripMargin,
        "PrimaryVal.on",
        Array()
      )
      assert(Shell.Success("3,4 d=25") == result)
    }

    it("var params are mutable; defaults and named arguments apply") {
      val result = shell.run(
        """
          |class Conf(val host: String = "localhost", var port: Int = 8080) {
          |public:
          |  def show(): String = this.host + ":" + this.port
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = new Conf()
          |    val b = new Conf(port = 9090)
          |    b.port = 7070
          |    return a.show() + "/" + b.show()
          |  }
          |}
          |""".stripMargin,
        "PrimaryDefaults.on",
        Array()
      )
      assert(Shell.Success("localhost:8080/localhost:7070") == result)
    }

    it("plain params pass to the superclass; body-less classes work") {
      val result = shell.run(
        """
          |class Animal(val name: String) {
          |public:
          |  def who(): String { return this.name }
          |}
          |class Dog(name: String, val breed: String) : Animal(name)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val d = new Dog("pochi", "shiba")
          |    return d.who() + " is a " + d.breed
          |  }
          |}
          |""".stripMargin,
        "PrimarySuper.on",
        Array()
      )
      assert(Shell.Success("pochi is a shiba") == result)
    }

    it("val fields reject reassignment") {
      val result = shell.run(
        """
          |class Point(val x: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1)
          |    p.x = 2
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "PrimaryValImmutable.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("classic def this constructors keep working") {
      val result = shell.run(
        """
          |class Legacy {
          |  val v: Int
          |public:
          |  def this(v: Int) { this.v = v }
          |  def get(): Int { return this.v }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + new Legacy(9).get()
          |  }
          |}
          |""".stripMargin,
        "ClassicCtor.on",
        Array()
      )
      assert(Shell.Success("9") == result)
    }
  }
}
