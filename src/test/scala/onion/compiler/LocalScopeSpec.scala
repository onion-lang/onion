package onion.compiler

import org.scalatest.funsuite.AnyFunSuite

class LocalScopeSpec extends AnyFunSuite {
  private def binding(index: Int) = LocalBinding(index, TypedAST.BasicType.INT, isMutable = true)

  test("a duplicate first binding keeps the original value before and after growth") {
    val scope = new LocalScope(null)
    val first = binding(0)
    assert(!scope.put("value", first))
    assert(scope.put(new String("value"), binding(99)))
    assert(scope.get("value").exists(_ eq first))
    assert(!scope.put("other", binding(1)))
    assert(scope.put("value", binding(98)))
    assert(scope.lookup("value") eq first)
    assert(scope.bindingEntries.map((n, b) => n -> b.index).toMap == Map("value" -> 0, "other" -> 1))
  }

  test("growth keeps every binding and does not change the parent shadowing relation") {
    val parent = new LocalScope(null)
    val inherited = binding(1000)
    parent.put("inherited", inherited)
    parent.put("value", binding(1001))
    val scope = new LocalScope(parent)
    assert(scope.lookup("inherited") eq inherited)
    assert(scope.get("inherited").isEmpty)
    assert(!scope.contains("inherited"))
    scope.put("value", binding(0))
    for (i <- 1 to 128) assert(!scope.put(s"v$i", binding(i)))
    assert(scope.lookup("value").index == 0)
    assert(parent.lookup("value").index == 1001)
    assert(scope.lookup("inherited") eq inherited)
    assert(scope.lookup("missing") == null)
    assert(scope.entries.map(_.index).toSet == (0 to 128).toSet)
    assert(scope.allNames.size == 130)
  }

  test("empty and singleton snapshots are detached from later insertions") {
    val scope = new LocalScope(null)
    assert(scope.bindingEntries.isEmpty)
    assert(scope.names.isEmpty)
    assert(scope.entries.isEmpty)
    assert(scope.lookup("missing") == null)
    scope.put("first", binding(0))
    val names = scope.names
    val entries = scope.entries
    assert(scope.bindingEntries.toList.map(_._1) == List("first"))
    scope.put("second", binding(1))
    entries.clear()
    assert(names == Set("first"))
    assert(scope.names == Set("first", "second"))
    assert(scope.entries.size == 2)
  }

  test("null keys and null bindings retain the existing map contract across growth") {
    val scope = new LocalScope(null)
    assert(!scope.put(null, null))
    assert(scope.contains(null))
    assert(scope.get(null) == Some(null))
    assert(scope.put(null, binding(1)))
    assert(!scope.put("other", binding(2)))
    assert(scope.get(null) == Some(null))
    assert(scope.contains(null))
    assert(scope.names == Set(null, "other"))
  }
}
