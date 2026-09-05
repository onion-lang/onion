package onion.compiler.typing

import onion.compiler.ClassTable
import onion.compiler.TypedAST.*
import org.scalatest.funsuite.AnyFunSuite

class MethodArityPruningSpec extends AnyFunSuite:
  private class CountingTable extends ClassTable(""):
    var specializations = 0
    override def specializedArgsOf(views: AnyRef, method: Method)(compute: => Array[Type]): Array[Type] =
      specializations += 1
      super.specializedArgsOf(views, method)(compute)

  private def checkRejected(arguments: Array[Type], supplied: Int, vararg: Boolean): Unit =
    val table = new CountingTable
    val owner = new ClassDefinition(null, false, 0, "Owner", null, Seq.empty)
    val method = new MethodDefinition(null, 0, owner, "call", arguments, BasicType.VOID, null, vararg = vararg)
    val support = new MethodResolutionSupport(Map.empty, new Array[Term](supplied), table)
    assert(!support.applicable(method))
    assert(table.specializations == 0, "arity rejection must precede type specialization")

  test("rejects too few fixed arguments before type specialization"):
    checkRejected(Array(BasicType.INT), 0, false)

  test("rejects too many fixed arguments before type specialization"):
    checkRejected(Array.empty, 1, false)

  test("rejects missing fixed prefix of varargs before type specialization"):
    val table = new ClassTable("")
    checkRejected(Array(BasicType.INT, table.loadArray(BasicType.INT, 1)), 0, true)

  test("keeps omitted default arguments applicable"):
    val table = new CountingTable
    val owner = new ClassDefinition(null, false, 0, "Owner", null, Seq.empty)
    val method = new MethodDefinition(null, 0, owner, "call", Array(BasicType.INT), BasicType.VOID, null)
    method.setArgumentsWithDefaults(Array(MethodArgument("value", BasicType.INT, Some(new IntValue(7)))))
    val support = new MethodResolutionSupport(Map.empty, Array.empty, table)
    assert(support.applicable(method))
    assert(table.specializations == 1)

  test("keeps empty and expanded varargs applicable"):
    val table = new CountingTable
    val owner = new ClassDefinition(null, false, 0, "Owner", null, Seq.empty)
    val method = new MethodDefinition(null, 0, owner, "call", Array(table.loadArray(BasicType.INT, 1)), BasicType.VOID, null, vararg = true)
    val array = new NewArrayWithValues(null, table.loadArray(BasicType.INT, 1), Array[Term](new IntValue(1)))
    for supplied <- Seq(Array.empty[Term], Array[Term](new IntValue(1), new IntValue(2)), Array[Term](array)) do
      val support = new MethodResolutionSupport(Map.empty, supplied, table)
      assert(support.applicable(method))

  test("preserves the fixed-arity fallback for a zero-parameter vararg declaration"):
    val table = new CountingTable
    val owner = new ClassDefinition(null, false, 0, "Owner", null, Seq.empty)
    val method = new MethodDefinition(null, 0, owner, "call", Array.empty, BasicType.VOID, null, vararg = true)
    assert(new MethodResolutionSupport(Map.empty, Array.empty, table).applicable(method))
    checkRejected(Array.empty, 1, true)
