package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/** A generic call whose arguments fix T, into a nullable target of another type, gets one E0000 (#2694). */
class GenericCallNullableTargetMismatchSpec extends AnyFunSpec with Diagrams {
  private def errorsOf(body: String): Seq[CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src = s"def ident[T](x: T): T = x\n$body\n"
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.toSeq
      case _ => Seq.empty
    }
  }

  for ((target, arg) <- Seq("Long?" -> "5", "Int?" -> "5L", "Int?" -> "\"s\"", "String?" -> "5")) {
    it(s"val x: $target = ident($arg) reports one error") {
      val errs = errorsOf(s"val x: $target = ident($arg)\nprintln(x)")
      assert(errs.size == 1)
      assert(errs.head.errorCode.contains("E0000"))
      assert(!errs.head.message.contains("!!"))
    }
  }
}
