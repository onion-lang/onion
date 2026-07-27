package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * E0015 (CLASS_NOT_ACCESSIBLE) is reported when a *field* is selected on a
 * value whose static type is an `internal` class declared in a different
 * module (the check lives in the field/getter member-selection path, not the
 * method-call path), but had no regression coverage at all before this spec.
 * Accessibility is module-scoped (`hasSamePackage` compares the dotted prefix
 * before the last dot of the class name), so triggering it needs two
 * compilation units with distinct `module` declarations.
 */
class ClassAccessibilitySpec extends AbstractShellSpec {
  private def errors(sources: (String, String)*): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = sources.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("selecting a member on an internal class from another module") {
    it("reports E0015 when the class is internal to a different module") {
      val results = errors(
        "a.on" ->
          """module pkg.a
            |internal class Hidden {
            |public:
            |  var n: Int
            |  def this { n = 1 }
            |}
            |""".stripMargin,
        "b.on" ->
          """module pkg.b
            |import { pkg.a.Hidden }
            |class UseIt {
            |public:
            |  static def main(args: String[]): Int {
            |    val h: Hidden = null
            |    return h.n
            |  }
            |}
            |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0015")), s"expected E0015, got: $results")
    }

    it("still allows access to an internal class from within its own module") {
      val results = errors(
        "a.on" ->
          """module pkg.a
            |internal class Hidden {
            |public:
            |  var n: Int
            |  def this { n = 1 }
            |}
            |class UseIt {
            |public:
            |  static def main(args: String[]): Int {
            |    val h = new Hidden()
            |    return h.n
            |  }
            |}
            |""".stripMargin
      )
      assert(results.isEmpty, s"expected no errors, got: $results")
    }
  }
}
