package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A bare name that is actually a field (Onion requires explicit `this.` /
 * `Class::`) should be diagnosed with the qualified form to use, instead of
 * the generic "local variable not found".
 */
class FieldQualificationHintSpec extends AnyFunSpec {

  private def errorsOf(source: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    val result = new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Hint.on")))
    result.diagnostics.allErrors.toSeq.map(_.message)
  }

  describe("field qualification hint") {
    it("suggests this.<name> for a bare instance field") {
      val messages = errorsOf(
        """
          |class Circle {
          |  val r: Double
          |public:
          |  def this(radius: Double) { this.r = radius }
          |  def area(): Double = r * r
          |}
          |""".stripMargin)
      assert(messages.exists(_.contains("this.r")), s"messages: ${messages.mkString(" | ")}")
    }

    it("suggests Class::<name> for a bare static field") {
      val messages = errorsOf(
        """
          |class C {
          |  static val cache: Int = 42
          |public:
          |  static def get(): Int = cache
          |}
          |""".stripMargin)
      assert(messages.exists(_.contains("C::cache")), s"messages: ${messages.mkString(" | ")}")
    }
  }
}
