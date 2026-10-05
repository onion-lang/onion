package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * An abstract method declared before any `public:`/`protected:` section (a member with
 * no access section is private) or under `private:` used to compile with no diagnostic;
 * the class file then carried ACC_PRIVATE|ACC_ABSTRACT and the JVM refused it with a raw
 * ClassFormatError the moment the script's classes were loaded (issue #2012).
 */
class PrivateAbstractMethodSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("an abstract method that would be private") {
    it("reports E0096 when it has no access section") {
      val c = codes(
        """
          |abstract class ShapeOnly {
          |  abstract def area(): Double
          |}
          |IO::println("unused")
          |""".stripMargin)
      assert(c.contains(Some("E0096")), s"expected E0096, got: $c")
    }

    it("reports E0096 under `private:`") {
      val c = codes(
        """
          |abstract class ShapeOnly {
          |private:
          |  abstract def area(): Double
          |}
          |""".stripMargin)
      assert(c.contains(Some("E0096")), s"expected E0096, got: $c")
    }

    it("still accepts and runs an abstract method under `public:`") {
      val c = codes(
        """
          |abstract class ShapeOnly {
          |public:
          |  abstract def area(): Double
          |}
          |class Sq extends ShapeOnly {
          |public:
          |  override def area(): Double { return 4.0 }
          |}
          |IO::println((new Sq()).area())
          |""".stripMargin)
      assert(c.isEmpty, s"expected no errors, got: $c")
    }
  }
}
