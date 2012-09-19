package onion.compiler

import org.specs2.mutable.Specification
import org.specs2.mutable.BeforeAfter

class LocalScopeSpecification extends Specification {
  sequential

  trait fixture extends BeforeAfter {
    val scope = new LocalScope(null)
    val bindings = Array[LocalBinding](
      new LocalBinding(0, IRT.BASIC_TYPE_REF_INT),
      new LocalBinding(1, IRT.BASIC_TYPE_REF_DOUBLE),
      new LocalBinding(2, IRT.BASIC_TYPE_REF_LONG),
      new LocalBinding(3, IRT.BASIC_TYPE_REF_DOUBLE)
    )
    val names = Array("hoge", "foo", "bar", "hogehoge")
    val names2 = Array("a", "b", "c", "d")

    def before = {
      putAll(scope, names, bindings)
    }
    def after = null
  }
  
  private def putAll(scope: LocalScope, names: Array[String], bindings: Array[LocalBinding]): Boolean = {
    var contains = false
    for (i <- 0 until names.length) {
      if(scope.put(names(i), bindings(i))){
        contains = true
      }
    }
    contains;
  }

  """LocalScope""" should {
    "can have LocalBindings" in new fixture {
      scope.entries.size() must_== 4
    }
  }
}