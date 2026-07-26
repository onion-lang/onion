package onion.compiler.tools

import onion.tools.Shell

/**
 * Inheritance is spelled with words, not symbols: `extends` for a superclass and
 * `conforms` for interfaces, where `:` and `<:` used to be.
 *
 * A symbol is unsearchable — you cannot grep for `<:`, ask about it out loud, or guess
 * what it means from the outside. `conforms` is a soft keyword so that trading the
 * symbol for a word does not also take the word away as an identifier.
 */
class InheritanceKeywordSpec extends AbstractShellSpec {

  describe("extends") {
    it("names a superclass") {
      val r = shell.run(
        """
          |class Animal {
          |public:
          |  def this {}
          |  def sound(): String = "..."
          |}
          |class Dog extends Animal {
          |public:
          |  def this {}
          |  override def sound(): String = "woof"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return new Dog().sound() }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("woof") == r)
    }

    it("carries superclass constructor arguments") {
      val r = shell.run(
        """
          |class Animal(val kind: String)
          |class Dog(kind: String, val breed: String) extends Animal(kind)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val d = new Dog("dog", "shiba")
          |    return d.kind + "/" + d.breed
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("dog/shiba") == r)
    }
  }

  describe("conforms") {
    it("names the interfaces a class implements") {
      val r = shell.run(
        """
          |interface Greeter { def greet(): String }
          |interface Named { def name(): String }
          |class Person(val who: String) conforms Greeter, Named {
          |public:
          |  def greet(): String = "hi"
          |  def name(): String = who
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Person("ko")
          |    return p.greet() + "/" + p.name()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("hi/ko") == r)
    }

    it("combines with extends") {
      val r = shell.run(
        """
          |interface Greeter { def greet(): String }
          |class Animal(val kind: String)
          |class Dog(kind: String) extends Animal(kind) conforms Greeter {
          |public:
          |  def greet(): String = "woof " + kind
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return new Dog("dog").greet() }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("woof dog") == r)
    }

    it("works on a record and on an interface") {
      val r = shell.run(
        """
          |interface Shown { def show(): String }
          |interface Loud conforms Shown { def yell(): String }
          |record Pt(x: Int, y: Int) conforms Loud {
          |public:
          |  def show(): String = x() + "," + y()
          |  def yell(): String = show().toUpperCase()
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p: Shown = new Pt(1, 2)
          |    return p.show()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("1,2") == r)
    }
  }

  describe("`conforms` stays an ordinary identifier") {
    it("is usable as a method name, a local and a field") {
      val r = shell.run(
        """
          |class Test {
          |  var conforms: Int
          |public:
          |  def this { conforms = 1 }
          |  def conforms(n: Int): Int = n + conforms
          |  static def main(args: String[]): Int {
          |    val conforms = new Test()
          |    return conforms.conforms(41)
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r)
    }
  }

  describe("the old symbolic spellings") {
    it("rejects `:` for a superclass") {
      val r = shell.run(
        """
          |class Animal { public: def this {} }
          |class Dog : Animal { public: def this {} }
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(r.isInstanceOf[Shell.Failure], s"expected a parse failure, got $r")
    }

    it("still lists `extends` among the expected tokens for a stray `:`") {
      // The migration hint itself is localized, so assert on the token the parser
      // offers rather than on message text.
      val out = captureErr {
        shell.run(
          """
            |class Animal { public: def this {} }
            |class Dog : Animal { public: def this {} }
            |""".stripMargin, "None", Array())
      }
      assert(out.contains("extends"), out)
    }

    it("rejects `<:` for interfaces") {
      val r = shell.run(
        """
          |interface Greeter { def greet(): String }
          |class Person <: Greeter {
          |public:
          |  def this {}
          |  def greet(): String = "hi"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(r.isInstanceOf[Shell.Failure], s"expected a parse failure, got $r")
    }
  }

  private def captureErr(body: => Any): String = {
    val buf = new java.io.ByteArrayOutputStream()
    val out = new java.io.PrintStream(buf, true, "UTF-8")
    Console.withErr(out) { Console.withOut(out) { body } }
    out.flush()
    new String(buf.toByteArray, "UTF-8")
  }
}
