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
  }
}
