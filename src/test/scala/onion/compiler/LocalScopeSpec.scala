package onion.compiler

import org.scalatest.{FeatureSpec, BeforeAndAfter, GivenWhenThen}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LocalScopeSpec extends FeatureSpec with GivenWhenThen with BeforeAndAfter {
  var scope = new LocalScope(null)

  before {
    scope = new LocalScope(null)
    val bindings = Array[LocalBinding](
      new LocalBinding(0, IRT.BASIC_TYPE_REF_INT),
      new LocalBinding(1, IRT.BASIC_TYPE_REF_DOUBLE),
      new LocalBinding(2, IRT.BASIC_TYPE_REF_LONG),
      new LocalBinding(3, IRT.BASIC_TYPE_REF_DOUBLE)
    )
    val names = Array("hoge", "foo", "bar", "hogehoge")
    putAll(scope, names, bindings)
  }

  after {

  }

  private def putAll(scope: LocalScope, names: Array[String], bindings: Array[LocalBinding]): Boolean = {
    var contains = false
    for (i <- 0 until names.length) {
      if(scope.put(names(i), bindings(i))){
        contains = true
      }
    }
    contains
  }

  feature("a LocalScope") {
    scenario("have 4 LocalBindings") {
      assert(scope.entries.size === 4)
    }
  }
}