package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for SAM conversion (issue #127): closures implement a Java
 * functional interface's single abstract method when the target has no
 * FunctionN-style `call`, plus the vararg type-argument inference fix
 * that makes Colls::mutableListOf(1,2,3) a List[Integer].
 */
class SamConversionSpec extends AbstractShellSpec {

  describe("SAM conversion") {
    it("implements Runnable from a zero-arg lambda") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sb = new StringBuffer()
          |    val r: Runnable = () -> { sb.append("ran"); }
          |    r.run()
          |    return sb.toString()
          |  }
          |}
          |""".stripMargin,
        "SamRunnable.on",
        Array()
      )
      assert(Shell.Success("ran") == result)
    }

    it("implements Comparator and sorts through Collections.sort") {
      val result = shell.run(
        """
          |import { java.util.Comparator }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val cmp: Comparator[Integer] = (a, b) -> (b as Int) - (a as Int)
          |    val xs = Colls::mutableListOf(1, 3, 2)
          |    Collections::sort(xs, cmp)
          |    return xs.toString()
          |  }
          |}
          |""".stripMargin,
        "SamComparator.on",
        Array()
      )
      assert(Shell.Success("[3, 2, 1]") == result)
    }

    it("passes a SAM value to a Java constructor (Thread)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sb = new StringBuffer()
          |    val r: Runnable = () -> { sb.append("threaded"); }
          |    val t = new Thread(r)
          |    t.start()
          |    t.join()
          |    return sb.toString()
          |  }
          |}
          |""".stripMargin,
        "SamThread.on",
        Array()
      )
      assert(Shell.Success("threaded") == result)
    }

    it("ignores abstract Object-method redeclarations when finding the SAM") {
      // Comparator also declares equals(Object); compare must still be chosen
      val result = shell.run(
        """
          |import { java.util.Comparator }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val cmp: Comparator[String] = (a, b) -> a.length() - b.length()
          |    return cmp.compare("ab", "a")
          |  }
          |}
          |""".stripMargin,
        "SamObjectMethods.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }

  describe("Vararg type-argument inference") {
    it("infers List[Integer] from mutableListOf(1, 2, 3)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = Colls::mutableListOf(10, 20, 30)
          |    val v: Int = xs.get(1)
          |    return v + xs.size()
          |  }
          |}
          |""".stripMargin,
        "VarargInference.on",
        Array()
      )
      assert(Shell.Success(23) == result)
    }
  }

  describe("Argument-position lambdas") {
    it("types an untyped lambda against a static call's parameter (Comparator)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = Colls::mutableListOf(1, 3, 2)
          |    Collections::sort(xs, (a, b) -> (b as Int) - (a as Int))
          |    return xs.toString()
          |  }
          |}
          |""".stripMargin,
        "ArgPositionLambda.on",
        Array()
      )
      assert(Shell.Success("[3, 2, 1]") == result)
    }

    it("casts a type-variable-typed value to a primitive inside the lambda") {
      val result = shell.run(
        """
          |import { java.util.Comparator }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val cmp: Comparator[Integer] = (a, b) -> (a as Int) - (b as Int)
          |    return cmp.compare(10, 4)
          |  }
          |}
          |""".stripMargin,
        "TypeVarPrimitiveCast.on",
        Array()
      )
      assert(Shell.Success(6) == result)
    }
  }

  describe("Primitive type arguments for generic SAMs") {
    it("implements Comparator[Int] with primitive-typed lambda parameters") {
      val result = shell.run(
        """
          |import { java.util.Comparator }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val cmp: Comparator[Int] = (a: Int, b: Int) -> a - b
          |    return cmp.compare(5, 3)
          |  }
          |}
          |""".stripMargin,
        "SamComparatorPrimitive.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("sorts a List[Int] using a primitive-typed Comparator value") {
      val result = shell.run(
        """
          |import { java.util.Comparator }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = Colls::mutableListOf(1, 3, 2)
          |    val cmp: Comparator[Int] = (a: Int, b: Int) -> a - b
          |    Collections::sort(xs, cmp)
          |    return xs.toString()
          |  }
          |}
          |""".stripMargin,
        "SortListIntPrimitive.on",
        Array()
      )
      assert(Shell.Success("[1, 2, 3]") == result)
    }

    it("types an explicit-typed lambda at argument position against Comparator") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = Colls::mutableListOf(3, 1, 2)
          |    Collections::sort(xs, (a: Int, b: Int) -> a - b)
          |    return xs.toString()
          |  }
          |}
          |""".stripMargin,
        "ArgPositionPrimitiveLambda.on",
        Array()
      )
      assert(Shell.Success("[1, 2, 3]") == result)
    }

    it("types an explicit-typed lambda for an instance method SAM parameter") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = Colls::mutableListOf(3, 1, 2)
          |    xs.sort((a: Int, b: Int) -> a - b)
          |    return xs.toString()
          |  }
          |}
          |""".stripMargin,
        "InstanceMethodPrimitiveLambda.on",
        Array()
      )
      assert(Shell.Success("[1, 2, 3]") == result)
    }

    it("implements Supplier[Int] with a primitive return expression") {
      val result = shell.run(
        """
          |import { java.util.function.Supplier }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: Supplier[Int] = () -> 42
          |    return s.get() as Int
          |  }
          |}
          |""".stripMargin,
        "SupplierIntPrimitive.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("implements Predicate[Int] with a primitive parameter and boolean result") {
      val result = shell.run(
        """
          |import { java.util.function.Predicate }
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val p: Predicate[Int] = (x: Int) -> x > 0
          |    return p.test(5)
          |  }
          |}
          |""".stripMargin,
        "PredicateIntPrimitive.on",
        Array()
      )
      assert(Shell.Success(true) == result)
    }

    it("implements Comparator[Double] with primitive Double parameters") {
      val result = shell.run(
        """
          |import { java.util.Comparator }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val cmp: Comparator[Double] = (a: Double, b: Double) -> (a - b) as Int
          |    return cmp.compare(1.5, 2.5)
          |  }
          |}
          |""".stripMargin,
        "ComparatorDoublePrimitive.on",
        Array()
      )
      assert(Shell.Success(-1) == result)
    }

    it("implements Function1[Int, Int] with primitive parameter and return") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val f: Function1[Int, Int] = (x: Int) -> x * 2
          |    return f.call(21) as Int
          |  }
          |}
          |""".stripMargin,
        "Function1IntIntPrimitive.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }
}
