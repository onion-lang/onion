package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A type error inside a lambda must be reported when the lambda goes to a call that is itself
 * a constructor argument (`new K(call { ... })`). The constructor muted errors while typing its
 * arguments and kept the result, so the program compiled and then failed with a VerifyError,
 * or ran code that was never type-checked (#3178; the method-call shape was #3174).
 */
class NewObjectNestedLambdaErrorSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private val defs =
    """class K {
      |  val s: String
      |public:
      |  def this(s: String) { this.s = s }
      |  def get(): String = s
      |}
      |record R(s: String)
      |def call(f: (Int) -> String): String = f(1)
      |""".stripMargin

  describe("a type error in a lambda nested in a constructor argument (#3178)") {
    it("is E0000 for new K(call { ... })") {
      assert(errorCodes(defs + "println(new K(call { x -> val n: Int = \"s\"; \"got \" + (n + 1) }).get())\n") == Seq("E0000"))
    }
    it("is E0000 when assigned to a typed val") {
      assert(errorCodes(defs + "val k: K = new K(call { x -> val n: Int = \"s\"; \"y\" })\n") == Seq("E0000"))
    }
    it("is E0000 for a record constructor") {
      assert(errorCodes(defs + "val r = new R(call { x -> val n: Int = \"s\"; \"y\" })\n") == Seq("E0000"))
    }
    it("still compiles a correct lambda") {
      assert(errorCodes(defs + "val k = new K(call { x -> \"v\" + x })\nprintln(k.get())\n").isEmpty)
    }
  }
}
