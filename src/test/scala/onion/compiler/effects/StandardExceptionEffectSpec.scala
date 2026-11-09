package onion.compiler.effects

import java.io.StringReader
import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/** Building a standard `java.lang` exception is pure, like `new RuntimeException(..)` (#2611). */
class StandardExceptionEffectSpec extends AnyFunSpec {
  private val names = Seq(
    "IllegalArgumentException", "IllegalStateException", "UnsupportedOperationException",
    "ArithmeticException", "IndexOutOfBoundsException", "NullPointerException",
    "NumberFormatException", "ClassCastException", "Error")

  private def effectOf(name: String): Set[Effect] = {
    val src = s"class C {\npublic:\n  static def f(): void { val e = new $name(\"x\") }\n}"
    val result = new OnionCompiler(new CompilerConfig(Seq("."), null, "UTF-8", "", 10, runContracts = false))
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "Exc.on")))
    assert(!result.hasErrors, s"$name: ${result.allErrors.map(_.message).mkString("; ")}")
    val typed = result.debugArtifacts.typedClasses.getOrElse(Seq.empty)
    EffectInference.infer(typed).find(_.methodName == "f").get.effects
  }

  describe("a standard exception constructor") {
    names.foreach { n =>
      it(s"new $n(..) is pure") { assert(effectOf(n) == Set.empty[Effect]) }
    }
  }
}
