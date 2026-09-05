package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import org.scalatest.funsuite.AnyFunSuite

class OverrideContractPruningSpec extends AnyFunSuite:
  private class CountingTable extends ClassTable(""):
    val erasedNames = scala.collection.mutable.ArrayBuffer.empty[String]
    override def erasedParamsOf(method: Method)(compute: => String): String =
      erasedNames += method.name
      super.erasedParamsOf(method)(compute)

  private def check(includeMatch: Boolean): CountingTable =
    val table = new CountingTable
    val typing = new Typing(CompilerConfig(Seq.empty, "", "UTF-8", "", 10)):
      override private[compiler] def table_ : ClassTable = table
    val base = new ClassDefinition(null, false, 0, "Base", null, Seq.empty)
    val child = new ClassDefinition(null, false, 0, "Child", base, Seq.empty)
    def method(owner: ClassDefinition, name: String): MethodDefinition =
      new MethodDefinition(null, Modifier.PUBLIC, owner, name, Array(BasicType.INT), BasicType.VOID, null)
    base.add(method(base, "inherited"))
    child.add(method(child, "unrelated"))
    if includeMatch then child.add(method(child, "inherited"))
    DuplicationChecks.checkOverrideContracts(typing, child, Location(1, 1))
    table

  test("does not erase implementations with no possible inherited name match"):
    assert(check(false).erasedNames.isEmpty)

  test("still checks matching contracts without erasing unrelated implementations"):
    val table = check(true)
    assert(table.erasedNames.contains("inherited"))
    assert(!table.erasedNames.contains("unrelated"))

  test("preserves the last implementation for duplicate erased keys"):
    val typing = new Typing(CompilerConfig(Seq.empty, "", "UTF-8", "", 10))
    val base = new ClassDefinition(null, false, 0, "Base", null, Seq.empty)
    val child = new ClassDefinition(null, false, 0, "Child", base, Seq.empty)
    base.add(new MethodDefinition(null, Modifier.PUBLIC, base, "call", Array(BasicType.INT), BasicType.VOID, null))
    child.add(new MethodDefinition(null, Modifier.PUBLIC, child, "call", Array(BasicType.INT), BasicType.INT, null))
    child.add(new MethodDefinition(null, Modifier.PUBLIC, child, "call", Array(BasicType.INT), BasicType.VOID, null))
    // Duplicate signatures are diagnosed elsewhere. This pass historically used
    // the last implementation when deciding which return type to check.
    DuplicationChecks.checkOverrideContracts(typing, child, Location(1, 1))
    assert(typing.session.global.diagnostics.getProblems.isEmpty)
