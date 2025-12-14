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
  }
}

