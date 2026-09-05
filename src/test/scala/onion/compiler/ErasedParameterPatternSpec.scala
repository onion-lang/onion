package onion.compiler

import onion.compiler.typing.ErasedParameterPattern
import org.scalatest.funsuite.AnyFunSuite

class ErasedParameterPatternSpec extends AnyFunSuite {
  private val intForms = Set("I", "Ljava/lang/Integer;")

  test("raw implementation descriptors must match every contract position") {
    val contract = ErasedParameterPattern(Array(intForms, Set("Ljava/lang/String;")))
    assert(contract.accepts(Array("I", "Ljava/lang/String;")))
    assert(contract.accepts(Array("Ljava/lang/Integer;", "Ljava/lang/String;")))
    assert(!contract.accepts(Array("J", "Ljava/lang/String;")))
    assert(!contract.accepts(Array("I", "Ljava/lang/Object;")))
    assert(!contract.accepts(Array("I")))
  }

  test("override patterns must intersect at every position without combining positions") {
    val left = ErasedParameterPattern(Array(intForms, Set("J")))
    assert(left.overlaps(ErasedParameterPattern(Array(Set("I"), Set("J", "Ljava/lang/Long;")))))
    assert(!left.overlaps(ErasedParameterPattern(Array(Set("J"), intForms))))
    assert(!left.overlaps(ErasedParameterPattern(Array(intForms))))
  }

  test("raw acceptance does not expand the implementation's boxing alternatives") {
    val rawOnly = ErasedParameterPattern(Array(Set("Ljava/lang/Integer;")))
    assert(!rawOnly.accepts(Array("I")))
    assert(rawOnly.overlaps(ErasedParameterPattern(Array(intForms))))
  }

  test("empty parameter lists match only empty parameter lists") {
    val empty = ErasedParameterPattern(Array.empty[Set[String]])
    assert(empty.accepts(Array.empty[String]))
    assert(empty.overlaps(empty))
    assert(!empty.accepts(Array("I")))
    assert(!empty.overlaps(ErasedParameterPattern(Array(intForms))))
  }

  test("wide patterns match without enumerating their Cartesian product") {
    val wide = ErasedParameterPattern(Array.fill(64)(intForms))
    val actual = Array.tabulate(64)(i => if (i % 2 == 0) "I" else "Ljava/lang/Integer;")
    assert(wide.accepts(actual))
    assert(wide.overlaps(ErasedParameterPattern(Array.fill(64)(Set("I")))))
    actual(63) = "J"
    assert(!wide.accepts(actual))
    val mismatch = Array.fill(64)(Set("I"))
    mismatch(63) = Set("J")
    assert(!wide.overlaps(ErasedParameterPattern(mismatch)))
  }
}
