package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A lambda with the wrong number of parameters for its expected function type was
 * reported as E0005 on the function interface's own method, e.g. `m.filter { e -> ... }`
 * on a Map gave "a method applicable for Function2[String, Int, Boolean].call(Object) is
 * not found" -- the compiler's internal `call` method, not the mistake. It is now E0101,
 * once per lambda, naming both parameter counts and the expected function type.
 */
class LambdaArityDiagnosticSpec extends AbstractShellSpec {
  private def errors(src: String) = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  private def check(src: String, lambdaCount: Int, expectedCount: Int): Unit = {
    val errs = errors(src)
    assert(errs.flatMap(_.code) == Seq("E0101"), s"expected exactly one E0101, got: ${errs.map(e => e.code -> e.message)}")
    val msg = errs.head.message
    assert(!msg.contains(".call("), s"message leaks the internal call method: $msg")
    assert(msg.contains(lambdaCount.toString) && msg.contains(expectedCount.toString), s"message should give both counts: $msg")
  }

  describe("a lambda with the wrong parameter count") {
    it("a 1-parameter lambda for Map.filter's (key, value) function") {
      check(
        """val m = ["a": 1]
          |println(m.filter { e -> e.getValue() > 0 })
          |""".stripMargin, 1, 2)
    }
    it("a 2-parameter lambda for List.map's function") {
      check("println([1, 2].map { a, b -> a + b })\n", 2, 1)
    }
    it("a 1-parameter lambda for fold's accumulator function") {
      check("println([1, 2].fold(0) { a -> a })\n", 1, 2)
    }
    it("a lambda assigned to a declared function type") {
      check("val f: (Int, Int) -> Int = x -> x\n", 1, 2)
    }
    it("a 1-parameter lambda for a Java functional interface (BiConsumer)") {
      check(
        """val m = ["a": 1]
          |m.forEach { e -> println(e) }
          |""".stripMargin, 1, 2)
    }
    it("a typed-parameter lambda against Comparator") {
      check("val c: Comparator[Int] = (a: Int) -> a\n", 1, 2)
    }
  }
}
