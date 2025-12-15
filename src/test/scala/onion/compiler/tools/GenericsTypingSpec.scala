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
          |  var value: T
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
          |    val box: Box[String] = new Box[String]
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
          |  var value: T
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
          |    val box: Box[String] = new Box[String]
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
          |    val good: BoundBox[String] = new BoundBox[String]
          |    val bad: BoundBox[Object] = new BoundBox[Object]
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
          |  var value: T
          |  def this { }
          |}
          |
          |class UseBoxFieldOk {
          |public:
          |  static def main(args: String[]): String {
          |    val b: Box[String] = new Box[String]
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
            |  var value: T
            |  def this { }
            |}
            |
            |class UseBoxFieldBad {
            |public:
            |  static def main(args: String[]): Int {
            |    val b: Box[String] = new Box[String]
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

    it("resolves generic interface methods on concrete class receivers") {
      val result = shell.run(
        """
          |interface Picker[T] {
          |  def pick(x: T): String
          |}
          |
          |class PickerImpl <: Picker[String] {
          |public:
          |  def pick(x: Object): String {
          |    return "ok"
          |  }
          |
          |  static def main(args: String[]): String {
          |    val p: PickerImpl = new PickerImpl
          |    return p.pick("x")
          |  }
          |}
          |""".stripMargin,
        "PickerImpl.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("rejects incompatible overrides for generic supertypes") {
      val badBase = silenceErr {
        shell.run(
          """
            |class Base[T] {
            |public:
            |  def get(): T { return null }
            |}
            |
            |class Sub : Base[String] {
            |public:
            |  def get(): Object { return new Object }
            |  static def main(args: String[]): Int { return 0 }
            |}
            |""".stripMargin,
          "BadOverrideBase.on",
          Array()
        )
      }
      assert(Shell.Failure(-1) == badBase)

      val badInterface = silenceErr {
        shell.run(
          """
            |interface Id[T] {
            |  def id(x: T): T
            |}
            |
            |class BadImpl <: Id[String] {
            |public:
            |  def id(x: String): Object { return x }
            |  static def main(args: String[]): Int { return 0 }
            |}
            |""".stripMargin,
          "BadOverrideInterface.on",
          Array()
        )
      }
      assert(Shell.Failure(-1) == badInterface)

      val okInterface = shell.run(
        """
          |interface Id2[T] {
          |  def id(x: T): T
          |}
          |
          |class OkImpl <: Id2[String] {
          |public:
          |  def id(x: String): String {
          |    return x
          |  }
          |  static def main(args: String[]): String {
          |    val i: Id2[String] = new OkImpl
          |    return i.id("ok")
          |  }
          |}
          |""".stripMargin,
        "OkOverrideInterface.on",
        Array()
      )
      assert(Shell.Success("ok") == okInterface)
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
