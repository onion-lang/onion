package onion.compiler.typing

/** Boxed/primitive alternatives kept per position rather than expanded into
  * complete signatures. Matching takes linear space and time in the arity. */
private[compiler] final class ErasedParameterPattern(private val alternatives: Array[Set[String]]) {
  def accepts(actual: Array[String]): Boolean = {
    if (actual.length != alternatives.length) return false
    var i = 0
    while (i < alternatives.length) {
      if (!alternatives(i).contains(actual(i))) return false
      i += 1
    }
    true
  }

  def overlaps(other: ErasedParameterPattern): Boolean = {
    if (other.alternatives.length != alternatives.length) return false
    var i = 0
    while (i < alternatives.length) {
      if (!alternatives(i).exists(other.alternatives(i).contains)) return false
      i += 1
    }
    true
  }
}
