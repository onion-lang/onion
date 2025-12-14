package onion.compiler.tools

import onion.tools.Shell
import java.io.OutputStream
import java.io.PrintStream

class GenericsTypingSpec extends AbstractShellSpec {
  describe("Generics typing (erasure-based)") {
    it("defaults missing upper bounds to Object") {
      val result = shell.run(
        """
          |class Box[T] {
          |  value: T
          |public:
          |  def this {
          |  }
          |
          |  def set(v: T) {
          |    this.value = v
          |  }
          |
          |  def get(): T {
          |    return this.value
          |  }
          |}
          |
          |class UseBox {
          |public:
          |  static def main(args: String[]): String {
          |    box = new Box[String]
          |    box.set("hello")
          |    return box.get()
          |  }
          |}
          |""".stripMargin,
        "UseBoxDefaultBound.on",
        Array()
      )
      assert(Shell.Success("hello") == result)
    }

    it("type-checks generic class applications") {
      val result = shell.run(
        """
          |class Box[T extends Object] {
          |  value: T
          |public:
          |  def this {
          |  }
          |
          |  def set(v: T) {
          |    this.value = v
          |  }
          |
          |  def get(): T {
          |    return this.value
          |  }
          |}
          |
          |class UseBox {
          |public:
          |  static def main(args: String[]): String {
          |    box = new Box[String]
          |    box.set("hello")
          |    return box.get()
          |  }
          |}
          |""".stripMargin,
        "UseBox.on",
        Array()
      )
      assert(Shell.Success("hello") == result)
    }

    it("infers generic method type arguments") {
      val result = shell.run(
        """
          |class Util {
          |public:
          |  static def id[A extends Object](x: A): A {
          |    return x
          |  }
          |
          |  static def main(args: String[]): String {
          |    return Util::id("ok")
          |  }
          |}
          |""".stripMargin,
        "Util.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("accepts explicit generic method type arguments") {
      val result = shell.run(
        """
          |class UtilExplicit {
          |public:
          |  static def id[A extends Object](x: A): A {
          |    return x
          |  }
          |
          |  static def main(args: String[]): String {
          |    return UtilExplicit::id[String]("ok")
          |  }
          |}
          |""".stripMargin,
        "UtilExplicit.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("rejects invalid explicit generic method type arguments") {
      val result = silenceErr {
        shell.run(
          """
            |class UtilExplicitFail {
            |public:
            |  static def id[A extends Object](x: A): A {
            |    return x
            |  }
            |
            |  static def main(args: String[]): String {
            |    return UtilExplicitFail::id[String](new Object)
            |  }
            |}
            |""".stripMargin,
          "UtilExplicitFail.on",
          Array()
        )
      }
      assert(Shell.Failure(-1) == result)
    }

    it("rejects conflicting generic method inference") {
      val result = silenceErr {
        shell.run(
          """
            |class Util2 {
            |public:
            |  static def pair[A extends Object](x: A, y: A): A {
            |    return x
            |  }
            |
            |  static def main(args: String[]): String {
            |    Util2::pair("x", "y")
            |    Util2::pair("x", new Object)
            |    return "done"
            |  }
            |}
            |""".stripMargin,
          "Util2.on",
          Array()
        )
      }
      assert(Shell.Failure(-1) == result)
    }

    it("checks upper bounds on type applications") {
      val result = silenceErr {
        shell.run(
          """
            |class BoundBox[T extends Serializable] {
            |public:
            |  def this { }
            |}
            |
            |class UseBoundBox {
            |public:
            |  static def main(args: String[]): String {
            |    good = new BoundBox[String]
            |    bad = new BoundBox[Object]
            |    return "done"
            |  }
            |}
            |""".stripMargin,
          "UseBoundBox.on",
          Array()
        )
      }
      assert(Shell.Failure(-1) == result)
    }

    it("rejects erased signature collisions") {
      val result = silenceErr {
        shell.run(
          """
            |class Collision[T] {
            |public:
            |  def f(x: T): Object {
            |    return x
            |  }
            |
            |  def f(x: Object): Object {
            |    return x
            |  }
            |
            |  static def main(args: String[]): Int {
            |    return 0
            |  }
            |}
            |""".stripMargin,
          "Collision.on",
          Array()
        )
      }
      assert(Shell.Failure(-1) == result)
    }

    it("checks field assignment types on applied class types") {
      val ok = shell.run(
        """
          |class Box[T] {
          |public:
          |  value: T
          |  def this { }
          |}
          |
          |class UseBoxFieldOk {
          |public:
          |  static def main(args: String[]): String {
          |    b: Box[String] = new Box[String]
          |    b.value = "ok"
          |    return b.value
          |  }
          |}
          |""".stripMargin,
        "UseBoxFieldOk.on",
        Array()
      )
      assert(Shell.Success("ok") == ok)

      val bad = silenceErr {
        shell.run(
          """
            |class Box[T] {
            |public:
            |  value: T
            |  def this { }
            |}
            |
            |class UseBoxFieldBad {
            |public:
            |  static def main(args: String[]): Int {
            |    b: Box[String] = new Box[String]
            |    b.value = new Object
            |    return 0
            |  }
            |}
            |""".stripMargin,
          "UseBoxFieldBad.on",
          Array()
        )
      }
      assert(Shell.Failure(-1) == bad)
    }
  }

  private def silenceErr[A](block: => A): A = {
    val original = System.err
    val silent = new PrintStream(OutputStream.nullOutputStream())
    try {
      System.setErr(silent)
      block
    } finally {
      System.setErr(original)
      silent.close()
    }
  }
}
