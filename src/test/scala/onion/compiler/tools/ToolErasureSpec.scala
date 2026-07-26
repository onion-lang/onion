package onion.compiler.tools

import java.io.{PrintWriter, StringReader, StringWriter}
import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.{Textifier, TraceClassVisitor}
import org.scalatest.funspec.AnyFunSpec

/**
 * Erasure is a feasibility condition, not an optimization (issue #357): there is no IR
 * between the typed AST and codegen, so effects must vanish inside Typing. A `tool`
 * therefore compiles to exactly the bytecode of the equivalent function — the backend
 * learns nothing. This spec pins that by disassembling both and comparing.
 */
class ToolErasureSpec extends AnyFunSpec {

  private def compileBytes(source: String): Array[Byte] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, checkLaws = false)
    val result = new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Erasure.on")))
    assert(!result.hasErrors, s"compile failed: ${result.allErrors.map(_.message).mkString("; ")}")
    result.classes.head.content
  }

  private def disassemble(bytes: Array[Byte]): String = {
    val sw = new StringWriter()
    new ClassReader(bytes).accept(
      new TraceClassVisitor(null, new Textifier(), new PrintWriter(sw)), 0)
    // The requires clause shifts the body down a couple of source lines, so the debug
    // LINENUMBER entries legitimately differ; instructions must not.
    sw.toString.linesIterator.filterNot(_.contains("LINENUMBER")).mkString("\n")
  }

  it("a tool emits the same bytecode as the equivalent function") {
    val asTool =
      """tool copy(src: String, dst: String): Int
        |  requires { read(src), write(dst) }
        |{
        |  Files::writeText(dst, Files::readText(src))
        |  return 0
        |}
        |class Test {
        |public:
        |  static def main(args: String[]): Int { return 0 }
        |}
        |""".stripMargin
    val asDef =
      """def copy(src: String, dst: String): Int
        |{
        |  Files::writeText(dst, Files::readText(src))
        |  return 0
        |}
        |class Test {
        |public:
        |  static def main(args: String[]): Int { return 0 }
        |}
        |""".stripMargin

    val toolText = disassemble(compileBytes(asTool))
    val defText  = disassemble(compileBytes(asDef))
    assert(toolText == defText,
      s"tool and def bytecode differ — effects leaked below typing:\n--- tool ---\n$toolText\n--- def ---\n$defText")
  }
}
