package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for cast validation: the compiler must reject casts that can never
 * succeed at runtime (e.g. String -> Int).
 */
class CastValidationSpec extends AbstractShellSpec {

  describe("Cast validation") {

    it("rejects casting a String to Int") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: String = "42"
          |    return s as Int
          |  }
          |}
          |""".stripMargin,
        "StringAsInt.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects casting an Int to String") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val n: Int = 42
          |    val s: String = n as String
          |    return s.length()
          |  }
          |}
          |""".stripMargin,
        "IntAsString.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("allows unboxing an Integer to Int") {
      val result = shell.run(
        """
          |import { java.lang.Integer as JInt }
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val boxed: JInt = 42
          |    return boxed as Int
          |  }
          |}
          |""".stripMargin,
        "IntegerAsInt.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("allows casting Object to a reference type") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val o: Object = "hello"
          |    val s: String = o as String
          |    return s.length()
          |  }
          |}
          |""".stripMargin,
        "ObjectAsString.on",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("rejects casting a List[String] to a String[]") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val list: List[String] = ["a", "b"]
          |    val arr = (list as String[])
          |    return arr.length
          |  }
          |}
          |""".stripMargin,
        "ListAsStringArray.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects casting a String[] to a List[String]") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val arr: String[] = new String[2]
          |    val list = (arr as List[String])
          |    return list.size
          |  }
          |}
          |""".stripMargin,
        "StringArrayAsList.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("still allows casting Object to an array type") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val o: Object = new String[2]
          |    val arr: String[] = (o as String[])
          |    return arr.length
          |  }
          |}
          |""".stripMargin,
        "ObjectAsStringArray.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("still allows casting an array to Object") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val arr: String[] = new String[2]
          |    val o = (arr as Object)
          |    return (o as String[]).length
          |  }
          |}
          |""".stripMargin,
        "StringArrayAsObject.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("allows casting along the class hierarchy") {
      val result = shell.run(
        """
          |class Animal { }
          |class Dog extends Animal {
          |public:
          |  def bark(): Int = 1
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val a: Animal = new Dog()
          |    val d: Dog = a as Dog
          |    return d.bark()
          |  }
          |}
          |""".stripMargin,
        "HierarchyCast.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }
}
