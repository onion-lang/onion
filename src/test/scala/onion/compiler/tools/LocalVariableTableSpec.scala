package onion.compiler.tools

import java.io.StringReader

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.objectweb.asm.{ClassReader, Opcodes}
import org.objectweb.asm.tree.{ClassNode, LocalVariableNode, MethodNode}
import org.scalatest.funspec.AnyFunSpec

import scala.jdk.CollectionConverters.*

/**
 * Generated methods must carry a LocalVariableTable.
 *
 * Line numbers were already emitted, so a JVM debugger could step through `.on` source —
 * and then show nothing at all for any variable, because `visitLocalVariable` appeared
 * nowhere in the compiler. Stepping without being able to look at a value is most of the
 * way to useless, and it is the sort of gap that never gets noticed from inside: every
 * compiler test asserts on behaviour, and behaviour is identical either way.
 *
 * The names come from the typed AST's scopes, which are the only place a variable is still
 * called what the author called it by the time codegen runs.
 */
class LocalVariableTableSpec extends AnyFunSpec {

  private def classesOf(source: String, debug: Boolean = true): Seq[ClassNode] = {
    val config = new CompilerConfig(
      Seq("."), null, "UTF-8", "", 10, checkLaws = false, emitDebugInfo = debug)
    val result = new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Debug.on")))
    assert(!result.hasErrors, s"compile failed: ${result.allErrors.map(_.message).mkString("; ")}")
    result.classes.map { compiled =>
      val node = new ClassNode()
      new ClassReader(compiled.content).accept(node, 0)
      node
    }
  }

  private def method(classes: Seq[ClassNode], className: String, methodName: String): MethodNode = {
    val node = classes.find(_.name.endsWith(className))
      .getOrElse(fail(s"no class ending in $className among ${classes.map(_.name).mkString(", ")}"))
    node.methods.asScala.find(_.name == methodName)
      .getOrElse(fail(s"no method $methodName in ${node.name}"))
  }

  private def locals(m: MethodNode): Seq[LocalVariableNode] =
    Option(m.localVariables).map(_.asScala.toSeq).getOrElse(Seq.empty)

  private val sample =
    """class Debug {
      |public:
      |  static def compute(factor: Int): Int {
      |    val doubled: Int = factor * 2
      |    var running: Int = 0
      |    var i: Int = 0
      |    while i < doubled {
      |      running = running + i
      |      i = i + 1
      |    }
      |    return running
      |  }
      |  static def main(args: String[]): Int { return compute(3) }
      |}
      |""".stripMargin

  describe("a compiled method") {
    it("names its parameters, so a debugger shows what was passed in") {
      val entries = locals(method(classesOf(sample), "Debug", "compute"))
      assert(entries.map(_.name).contains("factor"),
        s"parameters missing from the LocalVariableTable: ${entries.map(_.name).mkString(", ")}")
    }

    it("names its locals, so a debugger can show their values") {
      val entries = locals(method(classesOf(sample), "Debug", "compute"))
      val names = entries.map(_.name).toSet
      for (expected <- Seq("doubled", "running", "i"))
        assert(names.contains(expected),
          s"local `$expected` missing from the LocalVariableTable: ${names.toSeq.sorted.mkString(", ")}")
    }

    it("records a descriptor matching each variable's type") {
      val entries = locals(method(classesOf(sample), "Debug", "compute"))
      val byName = entries.map(e => e.name -> e.desc).toMap
      assert(byName.get("factor").contains("I"), s"factor: ${byName.get("factor")}")
      assert(byName.get("doubled").contains("I"), s"doubled: ${byName.get("doubled")}")
    }

    it("gives every entry a range the JVM will accept") {
      // start must precede end and both must be real labels, or the class fails
      // verification -- an invalid table is worse than none.
      val m = method(classesOf(sample), "Debug", "compute")
      val entries = locals(m)
      assert(entries.nonEmpty)
      for (entry <- entries) {
        assert(entry.start != null && entry.end != null, s"${entry.name} has a null bound")
        assert(entry.index >= 0, s"${entry.name} has a negative slot")
      }
    }

    it("names `this` in an instance method") {
      val source =
        """class Holder {
          |  var value: Int
          |public:
          |  def this { this.value = 0 }
          |  def bump(step: Int): Int {
          |    this.value = this.value + step
          |    return this.value
          |  }
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin
      val m = method(classesOf(source), "Holder", "bump")
      assert((m.access & Opcodes.ACC_STATIC) == 0, "bump should be an instance method")
      val names = locals(m).map(_.name).toSet
      assert(names.contains("this"), s"slot 0 unnamed: ${names.toSeq.sorted.mkString(", ")}")
      assert(names.contains("step"), s"parameter missing: ${names.toSeq.sorted.mkString(", ")}")
    }
  }

  describe("-g:none") {
    it("omits the table entirely, for anyone who wants the smaller class file") {
      val entries = locals(method(classesOf(sample, debug = false), "Debug", "compute"))
      assert(entries.isEmpty,
        s"debug info was emitted despite being switched off: ${entries.map(_.name).mkString(", ")}")
    }
  }
}
