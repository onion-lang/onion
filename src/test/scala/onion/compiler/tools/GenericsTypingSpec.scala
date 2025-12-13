package onion.compiler.tools

import onion.tools.Shell
import java.io.OutputStream
import java.io.PrintStream

class GenericsTypingSpec extends AbstractShellSpec {
  describe("Generics typing (erasure-based)") {
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

