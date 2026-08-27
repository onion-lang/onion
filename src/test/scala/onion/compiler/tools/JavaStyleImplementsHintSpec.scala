package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A Java-style `class A implements I { ... }` declaration. `implements` is not
 * a keyword in Onion (interfaces are named with `conforms`) -- it lexes as a
 * plain identifier, so `class Person` alone (with no `extends`/`conforms`/`{`)
 * is already a complete, body-less class declaration, and `implements Greeter`
 * is read as the start of a *new* top-level statement: a bare reference to the
 * identifier `implements`, followed by `Greeter`, where only a statement
 * terminator is valid. The parser trips several tokens past the actual
 * mistake and the expected-token dump never mentions `conforms`.
 */
class JavaStyleImplementsHintSpec extends AbstractShellSpec {
  private def messages(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message)
      case _ => Seq.empty
    }
  }

  describe("Java-style `implements` declaration") {
    it("hints at `conforms` for `class Person implements Greeter { ... }`") {
      val msgs = messages(
        """
          |interface Greeter { def greet(): String }
          |class Person implements Greeter {
          |public:
          |  def this {}
          |  def greet(): String = "hi"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("class Person conforms Greeter"), s"expected a hint mentioning `class Person conforms Greeter`, got: $msgs")
    }

    it("hints at `conforms` when combined with `extends`") {
      val msgs = messages(
        """
          |interface Greeter { def greet(): String }
          |class Animal { public: def this {} }
          |class Dog extends Animal implements Greeter {
          |public:
          |  def this {}
          |  def greet(): String = "woof"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("conforms Greeter"), s"expected a hint mentioning `conforms Greeter`, got: $msgs")
    }

    it("hints with multiple interfaces, `implements A, B`") {
      val msgs = messages(
        """
          |interface Greeter { def greet(): String }
          |interface Named { def name(): String }
          |class Person implements Greeter, Named {
          |public:
          |  def this {}
          |  def greet(): String = "hi"
          |  def name(): String = "p"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.contains("conforms Greeter, Named"), s"expected a hint mentioning `conforms Greeter, Named`, got: $msgs")
    }

    it("does not fire for the correct `conforms` form") {
      val msgs = messages(
        """
          |interface Greeter { def greet(): String }
          |class Person conforms Greeter {
          |public:
          |  def this {}
          |  def greet(): String = "hi"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin
      ).mkString("\n")
      assert(msgs.isEmpty, s"expected no errors for the correct `conforms` form, got: $msgs")
    }
  }
}
