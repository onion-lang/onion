package onion.compiler.tools

import onion.tools.Shell

class IterablesSpec extends AbstractShellSpec {
  describe("Iterables library") {
    it("maps over iterables without recursion") {
      val result = shell.run(
        """
          |import { onion.Iterables; java.util.ArrayList; java.lang.Iterable; }
          |class Test {
          |public:
          |  static def main(args: String[]): JInteger {
          |    val list = new ArrayList[JInteger];
          |    list.add(new JInteger(1));
          |    list.add(new JInteger(2));
          |    val it: Iterable[JInteger] = list;
          |    val mapper: (JInteger) -> JInteger =
          |      (x: JInteger) -> { return new JInteger(x.intValue() + 1); };
          |    val mapped: Iterable[JInteger] =
          |      Iterables::map[JInteger, JInteger](it, mapper);
          |    val folder: (JInteger, JInteger) -> JInteger =
          |      (acc: JInteger, x: JInteger) -> {
          |        return new JInteger(acc.intValue() + x.intValue());
          |      };
          |    val sum: JInteger =
          |      Iterables::foldl[JInteger, JInteger](mapped, new JInteger(0), folder);
          |    return sum;
          |  }
          |}
          |""".stripMargin,
        "IterablesMap.on",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("filters iterables with predicates") {
      val result = shell.run(
        """
          |import { onion.Iterables; java.util.ArrayList; java.lang.Iterable; }
          |class Test {
          |public:
          |  static def main(args: String[]): JInteger {
          |    val list = new ArrayList[JInteger];
          |    list.add(new JInteger(1));
          |    list.add(new JInteger(2));
          |    list.add(new JInteger(3));
          |    list.add(new JInteger(4));
          |    val it: Iterable[JInteger] = list;
          |    val isEven: (JInteger) -> JBoolean =
          |      (x: JInteger) -> { return new JBoolean(x.intValue() % 2 == 0); };
          |    val filtered: Iterable[JInteger] =
          |      Iterables::filter[JInteger](it, isEven);
          |    val folder: (JInteger, JInteger) -> JInteger =
          |      (acc: JInteger, x: JInteger) -> {
          |        return new JInteger(acc.intValue() + x.intValue());
          |      };
          |    val sum: JInteger =
          |      Iterables::foldl[JInteger, JInteger](filtered, new JInteger(0), folder);
          |    return sum;
          |  }
          |}
          |""".stripMargin,
        "IterablesFilter.on",
        Array()
      )
      assert(Shell.Success(6) == result)
    }

    it("sorts a list with a comparator") {
      val result = shell.run(
        """
          |import { onion.Iterables }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = Colls::listOf(3, 1, 4, 1, 5, 9, 2, 6)
          |    val ys = Iterables::sort(xs, (a: Int, b: Int) -> a - b)
          |    return ys.toString()
          |  }
          |}
          |""".stripMargin,
        "IterablesSort.on",
        Array()
      )
      assert(Shell.Success("[1, 1, 2, 3, 4, 5, 6, 9]") == result)
    }

    it("exists returns true when a predicate matches at least one element") {
      val result = shell.run(
        """
          |import { onion.Iterables }
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val xs = Colls::listOf(1, 3, 4, 7)
          |    return Iterables::exists(xs, (x: Int) -> x % 2 == 0)
          |  }
          |}
          |""".stripMargin,
        "IterablesExists.on",
        Array()
      )
      assert(Shell.Success(true) == result)
    }

    it("exists returns false when no element matches the predicate") {
      val result = shell.run(
        """
          |import { onion.Iterables }
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val xs = Colls::listOf(1, 3, 5, 7)
          |    return Iterables::exists(xs, (x: Int) -> x % 2 == 0)
          |  }
          |}
          |""".stripMargin,
        "IterablesExistsFalse.on",
        Array()
      )
      assert(Shell.Success(false) == result)
    }

    it("forAll returns true when every element matches the predicate") {
      val result = shell.run(
        """
          |import { onion.Iterables }
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val xs = Colls::listOf(2, 4, 6, 8)
          |    return Iterables::forAll(xs, (x: Int) -> x % 2 == 0)
          |  }
          |}
          |""".stripMargin,
        "IterablesForAll.on",
        Array()
      )
      assert(Shell.Success(true) == result)
    }

    it("forAll returns false when one element fails the predicate") {
      val result = shell.run(
        """
          |import { onion.Iterables }
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val xs = Colls::listOf(2, 4, 5, 8)
          |    return Iterables::forAll(xs, (x: Int) -> x % 2 == 0)
          |  }
          |}
          |""".stripMargin,
        "IterablesForAllFalse.on",
        Array()
      )
      assert(Shell.Success(false) == result)
    }

    it("listOf builds a list from varargs") {
      val result = shell.run(
        """
          |import { onion.Iterables }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = Iterables::listOf(3, 1, 4, 1, 5)
          |    return xs.toString()
          |  }
          |}
          |""".stripMargin,
        "IterablesListOf.on",
        Array()
      )
      assert(Shell.Success("[3, 1, 4, 1, 5]") == result)
    }
  }
}
