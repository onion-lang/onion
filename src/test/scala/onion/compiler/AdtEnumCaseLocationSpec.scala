package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/** An error in an ADT enum `case` is reported at that case, not at the `enum` keyword (#2322). */
class AdtEnumCaseLocationSpec extends AnyFunSpec with Diagrams {
  it("reports each unimplemented-method error (E0037) at its own case line") {
    val src =
      """enum Shape {
        |  case Circle(r: Double)
        |  case Square(s: Double)
        |public:
        |  def area(): Double
        |}
        |println("x")
        |""".stripMargin
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val errors = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(es) => es
      case _ => Nil
    }
    val lines = errors.filter(_.errorCode.contains("E0037")).map(_.location.line)
    assert(lines == List(2, 3), s"got $lines from ${errors.map(_.message)}")
  }
}
