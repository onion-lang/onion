package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/** A cycle through a generic supertype is E0016, not a ClassCircularityError at load (#2974). */
class GenericInheritanceCycleSpec extends AnyFunSpec {

  private def codes(source: String): Seq[String] =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 20))
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "S.on")))
      .allErrors
      .flatMap(_.code)
      .toSeq

  describe("inheritance cycles through type arguments") {
    it("two generic classes extending each other") {
      assert(codes("class A[T] extends B[T] { }\nclass B[T] extends A[T] { }\nprintln(1)\n").contains("E0016"))
    }
    it("a generic class extending itself") {
      assert(codes("class Loop[T] extends Loop[T] { }\nprintln(1)\n").contains("E0016"))
    }
    it("a generic interface conforming to itself") {
      assert(codes("interface I[T] conforms I[T] { }\nprintln(1)\n").contains("E0016"))
    }
    it("a generic hierarchy without a cycle still compiles") {
      assert(codes("class A[T] { }\nclass B[T] extends A[T] { }\nclass C extends B[String] { }\nprintln(1)\n").isEmpty)
    }
  }
}
