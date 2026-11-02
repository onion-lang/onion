package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.objectweb.asm.{ClassReader, Opcodes}
import org.objectweb.asm.tree.{AnnotationNode, ClassNode}
import org.scalatest.funspec.AnyFunSpec

import scala.jdk.CollectionConverters.*

/** A contract is written into the class file, so a module built later can read it (RFC #2557). */
class ContractInClassFileSpec extends AnyFunSpec {
  private def compile(src: String): Seq[(String, Array[Byte])] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, contracts = ContractMode.Check)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "M.on"))) match {
      case CompilationOutcome.Success(classes) => classes.map(c => c.className -> c.content)
      case other                               => fail(s"did not compile: $other")
    }
  }

  private def contractOf(bytes: Array[Byte], method: String): Option[Map[String, Seq[String]]] = {
    val node = new ClassNode()
    new ClassReader(bytes).accept(node, 0)
    node.methods.asScala.find(_.name == method).flatMap { m =>
      Option(m.invisibleAnnotations).map(_.asScala).getOrElse(Nil).find(_.desc == "Lonion/Contract;").map { a =>
        a.values.asScala.grouped(2).map { g =>
          g.head.asInstanceOf[String] -> g(1).asInstanceOf[java.util.List[?]].asScala.map(_.toString).toSeq
        }.toMap
      }
    }
  }

  private val source =
    """class Account {
      |public:
      |  def withdraw(x: Int): Int
      |    requires x > 0 && x <= 1000
      |    ensures result >= 0
      |  { return x }
      |  def plain(x: Int): Int { return x }
      |}""".stripMargin

  it("writes requires and ensures as text on the method") {
    val bytes = compile(source).find(_._1 == "Account").get._2
    val c = contractOf(bytes, "withdraw").getOrElse(fail("no @Contract on withdraw"))
    assert(c("requires") == Seq("(x > 0) && (x <= 1000)"), c.toString)
    assert(c("ensures") == Seq("result >= 0"), c.toString)
  }

  it("writes nothing on a method with no contract") {
    val bytes = compile(source).find(_._1 == "Account").get._2
    assert(contractOf(bytes, "plain").isEmpty)
  }
}
