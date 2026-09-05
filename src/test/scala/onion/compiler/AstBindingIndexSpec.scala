package onion.compiler

import onion.compiler.typing.session.AstBindingIndex
import org.scalatest.funsuite.AnyFunSuite

class AstBindingIndexSpec extends AnyFunSuite {
  test("identity bindings survive growth even when all source nodes are structurally equal") {
    val index = new AstBindingIndex
    val nodes = Vector.fill(4096)(AST.Id(Location(1, 1), "same"))
    val values = Vector.tabulate(4096)(i => new TypedAST.IntValue(Location(1, 1), i))
    nodes.zip(values).foreach((ast, typed) => index.bind(ast, typed))
    assert(index.allTypedBindings.size == 4096)
    nodes.zip(values).foreach { (ast, typed) =>
      assert(index.typedOf(ast).exists(_ eq typed))
    }
    assert(index.typedOf(AST.Id(Location(1, 1), "same")).isEmpty)
    for (i <- Seq(0, 127, 1023, 4095)) {
      assert(index.astOf(values(i)).exists(_ eq nodes(i)))
    }
  }

  test("copying a binding view preserves identity and leaves the original untouched") {
    val index = new AstBindingIndex
    val first = AST.Id(Location(1, 1), "same")
    val second = AST.Id(Location(1, 1), "same")
    val value = new TypedAST.IntValue(Location(1, 1), 1)
    index.bind(first, value)
    val original = index.allTypedBindings
    val added = original.updated(second, value)
    assert(added.size == 2)
    assert(original.size == 1)
    assert(!original.contains(second))
    val removed = added.removed(first)
    assert(removed.size == 1)
    assert(removed.contains(second))
    assert(!removed.contains(first))
    assert(index.typedOf(first).exists(_ eq value))
  }
}
