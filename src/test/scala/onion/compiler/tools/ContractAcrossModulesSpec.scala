package onion.compiler.tools

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}
import java.nio.file.{Files, Path}
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/**
 * A contract crosses a module boundary (RFC #2557): module A is compiled to class files, and module B
 * is compiled separately against them. B sees A's contracts only if they were written into A's class
 * files and read back.
 */
class ContractAcrossModulesSpec extends AnyFunSpec {
  /** Compile `src` to a directory of class files and return the directory. */
  private def build(src: String): Path = {
    val dir = Files.createTempDirectory("onion-module-a")
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, contracts = ContractMode.Check)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "A.on"))) match {
      case CompilationOutcome.Success(classes) =>
        classes.foreach { c =>
          val f = dir.resolve(c.className.replace('.', '/') + ".class")
          Files.createDirectories(f.getParent)
          Files.write(f, c.content)
        }
        dir
      case other => fail(s"module A did not compile: $other")
    }
  }

  /** Compile `src` as module B against the class files in `classes`; returns the result and what `--verbose` printed. */
  private def compileAgainst(classes: Path, src: String) = {
    val config = new CompilerConfig(Seq(classes.toString, "."), null, "UTF-8", "", 10, verbose = true,
      warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val buffer = new ByteArrayOutputStream()
    val old = System.err
    System.setErr(new PrintStream(buffer, true, "UTF-8"))
    try {
      val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "B.on")))
      (r, buffer.toString("UTF-8"))
    } finally System.setErr(old)
  }

  private val moduleA =
    """class Step {
      |public:
      |  def step(x: Int): Int
      |    requires x >= 0 && x < 100
      |    ensures result >= 0 && result < 100
      |  { return x }
      |}""".stripMargin

  it("proves a history in module B from a contract written in module A's class file") {
    val dir = build(moduleA)
    val b =
      """class Counter {
        |  invariant n >= 0 && n < 1000
        |  history n >= old(n)
        |public:
        |  var n: Int
        |  def this { n = 0 }
        |  def add(s: Step, k: Int): void
        |    requires k >= 0 && k < 100
        |  { if n < 1000 { n = n + s.step(k) } }
        |}""".stripMargin
    val (r, err) = compileAgainst(dir, b)
    assert(!r.hasErrors, r.diagnostics.toString)
    // proved, not merely silent: the call to the built Step.step was understood through its contract
    assert(err.contains("[history] proved: Counter.add: n >= old(n)"), err)
  }

  private val moduleB =
    """class Counter {
      |  invariant n >= 0 && n < 1000
      |  history n >= old(n)
      |public:
      |  var n: Int
      |  def this { n = 0 }
      |  def add(s: Step, k: Int): void
      |    requires k >= 0 && k < 100
      |  { if n < 1000 { n = n + s.step(k) } }
      |}""".stripMargin

  it("does not prove it when module A's contract allows a negative result") {
    val weak =
      """class Step {
        |public:
        |  def step(x: Int): Int
        |    requires x >= 0 && x < 100
        |    ensures result > -100 && result < 100
        |  { return x }
        |}""".stripMargin
    val (_, err) = compileAgainst(build(weak), moduleB)
    assert(!err.contains("[history] proved: Counter.add"), err)
  }

  it("does not use a callee that module A built without a contract") {
    val bare = "class Step {\npublic:\n  def step(x: Int): Int { return x }\n}"
    val (_, err) = compileAgainst(build(bare), moduleB)
    assert(!err.contains("[history] proved: Counter.add"), err)
  }
}
