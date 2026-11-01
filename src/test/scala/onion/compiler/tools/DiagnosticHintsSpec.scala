package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression tests for user-facing diagnostic hints.
 */
class DiagnosticHintsSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String): Seq[String] = {
    val result = new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )
    result.allErrors.map(_.message)
  }

  describe("INCOMPATIBLE_TYPE nullable hint") {
    it("suggests null-handling operators when a nullable value is assigned to a non-null type") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val name: String? = null
          |    val sure: String = name
          |    return 0
          |  }
          |}
        """.stripMargin
      )
      assert(errors.exists(_.contains("null")), s"expected a null-safety hint, got: $errors")
    }

    it("does not add a hint for an ordinary type mismatch") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = "hello"
          |    return 0
          |  }
          |}
        """.stripMargin
      )
      assert(errors.forall(!_.contains("null")), s"did not expect a null hint, got: $errors")
    }
  }

  describe("VARIABLE_NOT_FOUND did-you-mean hint") {
    it("suggests a similar local variable name for a typo") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val userName = "ok"
          |    return usrName.length()
          |  }
          |}
        """.stripMargin
      )
      assert(errors.exists(_.contains("userName")), s"expected did-you-mean hint, got: $errors")
    }
  }

  describe("List/array cast hint (#2258)") {
    it("hints at toArray for a List[T] as T[] cast") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val list: List[String] = ["a", "b"]
          |    val arr = (list as String[])
          |    return arr.length
          |  }
          |}
        """.stripMargin
      )
      assert(errors.exists(_.contains("toArray(new String[0])")), s"expected a toArray hint, got: $errors")
    }

    it("hints at Colls::toList for a T[] as List[T] cast") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val arr: String[] = new String[2]
          |    val list = (arr as List[String])
          |    return list.size
          |  }
          |}
        """.stripMargin
      )
      assert(errors.exists(_.contains("Colls::toList(arr)")), s"expected a Colls::toList hint, got: $errors")
    }

    it("suppresses the toArray hint for a primitive-component List[Int] as Int[] cast, since toArray has no such overload") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [1, 2]
          |    val a = (xs as Int[])
          |    return a.length
          |  }
          |}
        """.stripMargin
      )
      assert(errors.forall(!_.contains("toArray")), s"did not expect a toArray hint for a primitive array, got: $errors")
      assert(errors.forall(!_.contains("Consider a cast")), s"did not expect a misleading 'consider a cast' here, got: $errors")
    }

    it("suppresses the Colls::toList hint for a primitive-component Int[] as List[Int] cast, since toList has no such overload") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val arr: Int[] = new Int[2]
          |    val list = (arr as List[Int])
          |    return list.size
          |  }
          |}
        """.stripMargin
      )
      assert(errors.forall(!_.contains("Colls::toList")), s"did not expect a Colls::toList hint for a primitive array, got: $errors")
    }

    it("hints at a fully-dimensioned array literal for a List[Int[]] as Int[][] cast") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int[]] = [new Int[1], new Int[2]]
          |    val a = (xs as Int[][])
          |    return a.length
          |  }
          |}
        """.stripMargin
      )
      assert(errors.exists(_.contains("toArray(new Int[0][0])")), s"expected a fully-dimensioned toArray hint, got: $errors")
    }

    // #2305: `toArray(new String[0])` on a `List[Int]` throws ArrayStoreException, so the
    // snippet is offered only when the elements fit the array's component type.
    def listCastErrors(listType: String, init: String, arrayType: String): Seq[String] = compile(
      s"""
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val xs: $listType = $init
        |    val a = (xs as $arrayType)
        |    return a.length
        |  }
        |}
      """.stripMargin
    )

    it("gives no toArray snippet when the List's elements do not fit the array (List[Int] as String[])") {
      val errors = listCastErrors("List[Int]", "[1, 2]", "String[]")
      assert(errors.exists(_.contains("List[Int]")), s"expected the plain type mismatch, got: $errors")
      assert(errors.forall(!_.contains("toArray")), s"did not expect a toArray hint, got: $errors")
    }

    it("gives no toArray snippet for a List[Object] as String[] cast, whose elements may not be Strings") {
      val errors = listCastErrors("List[Object]", "[\"a\"]", "String[]")
      assert(errors.forall(!_.contains("toArray")), s"did not expect a toArray hint, got: $errors")
    }

    it("gives no toArray snippet for a List[String] as String[][] cast") {
      val errors = listCastErrors("List[String]", "[\"a\"]", "String[][]")
      assert(errors.forall(!_.contains("toArray")), s"did not expect a toArray hint, got: $errors")
    }

    it("still hints at toArray when the elements widen to the component (List[String] as Object[], List[Int] as Number[])") {
      val objects = listCastErrors("List[String]", "[\"a\"]", "Object[]")
      assert(objects.exists(_.contains("toArray(new Object[0])")), s"expected a toArray hint, got: $objects")
      val numbers = listCastErrors("List[Int]", "[1]", "Number[]")
      assert(numbers.exists(_.contains("toArray(new Number[0])")), s"expected a toArray hint, got: $numbers")
    }

    // #2314: the hint's snippet must compile when typed in.
    it("names JInteger[] as written and hints toArray(new JInteger[0])") {
      val errors = listCastErrors("List[Int]", "[1, 2]", "JInteger[]")
      assert(errors.exists(_.contains("toArray(new JInteger[0])")), s"expected a JInteger hint, got: $errors")
      assert(errors.forall(!_.contains("new Int[0]")), s"did not expect a primitive snippet, got: $errors")
    }

    it("gives no toArray snippet for a generic component (List[String] as Comparable[])") {
      val errors = listCastErrors("List[String]", "[\"a\"]", "Comparable[]")
      assert(errors.exists(_.contains("Comparable[]")), s"expected the type mismatch, got: $errors")
      assert(errors.forall(!_.contains("toArray")), s"did not expect a toArray hint, got: $errors")
    }

    it("gives no toArray snippet for a List of a bare type parameter") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def conv[T](xs: List[T]): String[] = (xs as String[])
          |  static def main(args: String[]): Int { return 0 }
          |}
        """.stripMargin
      )
      assert(errors.exists(_.contains("List[T]")), s"expected the type mismatch, got: $errors")
      assert(errors.forall(!_.contains("toArray")), s"did not expect a toArray hint, got: $errors")
    }

    it("does not add the List/array hint for an ordinary incompatible cast") {
      val errors = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: String = "42"
          |    return s as Int
          |  }
          |}
        """.stripMargin
      )
      assert(errors.forall(e => !e.contains("toArray") && !e.contains("Colls::toList")), s"did not expect a List/array hint, got: $errors")
    }
  }
}
