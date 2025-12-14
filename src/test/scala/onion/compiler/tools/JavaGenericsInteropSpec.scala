package onion.compiler.tools

import onion.tools.Shell

class JavaGenericsInteropSpec extends AbstractShellSpec {
  describe("Java generics interop") {
    it("accepts type arguments on Java generic classes") {
      val result = shell.run(
        """
          |class JavaList {
          |public:
          |  static def main(args: String[]): String {
          |    list: ArrayList[String] = new ArrayList[String]
          |    list.add("ok")
          |    return list.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaList.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("handles Java generic methods") {
      val result = shell.run(
        """
          |class JavaGenericMethod {
          |public:
          |  static def main(args: String[]): String {
          |    list = Collections::singletonList[String]("x")
          |    return list.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaGenericMethod.on",
        Array()
      )
      assert(Shell.Success("x") == result)
    }

    it("handles multiple type arguments") {
      val result = shell.run(
        """
          |class JavaMap {
          |public:
          |  static def main(args: String[]): String {
          |    map: HashMap[String, String] = new HashMap[String, String]
          |    map.put("k", "v")
          |    return map.get("k")
          |  }
          |}
          |""".stripMargin,
        "JavaMap.on",
        Array()
      )
      assert(Shell.Success("v") == result)
    }

    it("preserves generic supertypes for applied Java classes") {
      val result = shell.run(
        """
          |class JavaListAsSuper {
          |public:
          |  static def main(args: String[]): String {
          |    impl: ArrayList[String] = new ArrayList[String]
          |    impl.add("ok")
          |    asList: List[String] = impl
          |    return asList.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaListAsSuper.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("supports Collections.copy with wildcards") {
      val result = shell.run(
        """
          |class JavaCollectionsCopy {
          |public:
          |  static def main(args: String[]): String {
          |    src: ArrayList[String] = new ArrayList[String]
          |    src.add("x")
          |    dest: ArrayList[Object] = new ArrayList[Object]
          |    dest.add("y")
          |
          |    Collections::copy[String](dest, src)
          |    return "" + dest.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsCopy.on",
        Array()
      )
      assert(Shell.Success("x") == result)
    }

    it("supports Collections.sort with inferred type arguments") {
      val result = shell.run(
        """
          |class JavaCollectionsSort {
          |public:
          |  static def main(args: String[]): String {
          |    list: ArrayList[String] = new ArrayList[String]
          |    list.add("b")
          |    list.add("a")
          |    Collections::sort(list)
          |    return list.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsSort.on",
        Array()
      )
      assert(Shell.Success("a") == result)
    }
  }
}
