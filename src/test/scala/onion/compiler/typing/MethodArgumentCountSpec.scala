package onion.compiler.typing

import onion.compiler.ClassTable
import onion.compiler.TypedAST.*
import onion.compiler.environment.AsmRefs.AsmMethodRef
import onion.compiler.environment.ReflectionRefs.ReflectClassType
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import org.scalatest.funsuite.AnyFunSuite

class MethodArgumentCountSpec extends AnyFunSuite:
  test("method comparison reads no defensive argument arrays"):
    val table = new ClassTable("")
    val owner = new ClassDefinition(null, false, 0, "Owner", null, Seq.empty)
    class CountingMethod(desc: String) extends AsmMethodRef(new MethodNode(Opcodes.ACC_PUBLIC, "call", desc, null, null), owner, table, Map.empty):
      var reads = 0
      override def arguments: Array[Type] =
        reads += 1
        super.arguments
    val one = new CountingMethod("(I)V")
    val two = new CountingMethod("(II)V")
    val wide = new CountingMethod("(J)V")
    val comparator = new MethodComparator
    assert(comparator.compare(one, two) < 0)
    assert(comparator.compare(one, wide) < 0)
    assert(comparator.compare(one, one) == 0)
    assert(one.reads + two.reads + wide.reads == 0)

  test("method comparison preserves its equal-name nonidentical-type boundary"):
    val owner = new ClassDefinition(null, false, 0, "Owner", null, Seq.empty)
    val first = new ClassDefinition(null, false, 0, "SameName", null, Seq.empty)
    val second = new ClassDefinition(null, false, 0, "SameName", null, Seq.empty)
    val left = new MethodDefinition(null, 0, owner, "call", Array(first, BasicType.INT), BasicType.VOID, null)
    val right = new MethodDefinition(null, 0, owner, "call", Array(second, BasicType.LONG), BasicType.VOID, null)
    // Existing comparator returns at the first nonidentical pair even when names
    // compare equal, rather than examining subsequent parameters.
    assert(new MethodComparator().compare(left, right) == 0)

  test("ASM argument count does not request a defensive argument array"):
    val table = new ClassTable("")
    val owner = new ClassDefinition(null, false, 0, "Owner", null, Seq.empty)
    val node = new MethodNode(Opcodes.ACC_PUBLIC, "sum", "(II)I", null, null)
    class CountingMethod extends AsmMethodRef(node, owner, table, Map.empty):
      var reads = 0
      override def arguments: Array[Type] =
        reads += 1
        super.arguments
    val method = new CountingMethod
    assert(method.argumentCount == 2)
    assert(method.reads == 0)
    val copy = method.arguments
    copy(0) = BasicType.BOOLEAN
    assert(method.arguments(0) eq BasicType.INT)

  test("reflection argument count and defensive arrays agree"):
    val table = new ClassTable("")
    val owner = new ReflectClassType(classOf[java.lang.String], table)
    val methods = owner.methods("substring")
    assert(methods.map(_.argumentCount).sorted.toSeq == Seq(1, 2))
    methods.foreach { method =>
      val args = method.arguments
      args(0) = BasicType.BOOLEAN
      assert(method.arguments(0) eq BasicType.INT)
    }
