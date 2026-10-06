package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `SemanticErrorReporter.reportMethodNotFound` suggests the paren-less field form
 * (`suggestion.fieldNotMethod`) whenever a failed call's name matches a same-named field,
 * with no accessibility check (gap-probe finding, 2026-10-06). For `Concurrent::lock().lock()`
 * this steers the user straight into a second, equally unhelpful error: `Concurrent.Lock.lock`
 * is `private final ReentrantLock lock`, so the "fix" the hint names (`lock.lock`) immediately
 * fails with FIELD_NOT_ACCESSIBLE. The real API is `lock.acquire()`/`lock.release()`. The hint
 * must not point at a field the caller cannot reach.
 */
class InaccessibleFieldNotMethodHintSpec extends AnyFunSpec with Diagrams {
  private val src =
    """
      |import { onion.Concurrent }
      |class Main {
      |public:
      |  static def main(args: String[]): Int {
      |    val lock = Concurrent::lock()
      |    lock.lock()
      |    return 0
      |  }
      |}
      |""".stripMargin

  private def compileErrors(): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  it("does not suggest the inaccessible private field as a paren-less fix") {
    val msgs = compileErrors()
    assert(
      !msgs.toLowerCase(java.util.Locale.ROOT).contains("without parentheses"),
      s"expected no misleading field-not-method hint for an inaccessible field, got: $msgs"
    )
  }
}
