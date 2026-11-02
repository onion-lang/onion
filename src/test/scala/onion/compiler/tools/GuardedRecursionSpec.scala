package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/** A guarded self-recursive method keeps tail-call optimization: only the entry is guarded (design 12). */
class GuardedRecursionSpec extends AnyFunSpec {
  private val src =
    """class R {
      |public:
      |  static def sum(n: Int, acc: Int): Int
      |    requires n >= 0
      |    ensures result >= acc
      |  {
      |    if n == 0 { return acc }
      |    return R::sum(n - 1, acc + 1)
      |  }
      |}""".stripMargin

  it("runs 1,000,000 deep under --contracts runtime without a stack overflow") {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, contracts = ContractMode.Runtime)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "R.on"))) match {
      case CompilationOutcome.Success(cs) =>
        val loader = new ClassLoader(getClass.getClassLoader) {
          override def findClass(n: String): Class[?] = cs.find(_.className == n).map(c => defineClass(n, c.content, 0, c.content.length)).getOrElse(super.findClass(n))
        }
        val m = loader.loadClass("R").getMethod("sum", classOf[Int], classOf[Int])
        assert(m.invoke(null, Int.box(1000000), Int.box(0)) == Int.box(1000000))
      case other => fail(s"did not compile: $other")
    }
  }
}
