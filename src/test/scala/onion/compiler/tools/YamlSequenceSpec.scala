package onion.compiler.tools

import onion.tools.Shell

/**
 * `Yaml::parse`/`Yaml::stringify` now also handle a top-level YAML sequence of flat
 * block mappings (`- key: value` items), the shape `derive!(Yaml)`'s `fromYamlList`
 * needs (#1978). An element itself stays a flat mapping — no nested maps/sequences
 * within an item, same "flat" scope the mapping format already had.
 */
class YamlSequenceSpec extends AbstractShellSpec {
  describe("Yaml::parse / Yaml::stringify on a top-level sequence") {
    it("parses a sequence of flat mappings into a List of LinkedHashMap") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.List; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val parsed = Yaml::parse("- name: Alice\n  age: 30\n- name: Bob\n  age: 25\n")
          |    val list = (parsed as List)
          |    if list.size != 2 { return "size=" + list.size }
          |    val m0 = (list.get(0) as Map)
          |    val m1 = (list.get(1) as Map)
          |    return m0.get("name") + "," + m0.get("age") + "," + m1.get("name") + "," + m1.get("age")
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Alice,30,Bob,25") == result)
    }

    it("stringifies a List of Maps into a YAML sequence of flat mappings") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.ArrayList; java.util.List; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m0: Map[String, Object] = ["x": 1L, "y": 2L]
          |    val m1: Map[String, Object] = ["x": 3L, "y": 4L]
          |    val list: List[Object] = new ArrayList[Object]() as List[Object]
          |    list.add(m0)
          |    list.add(m1)
          |    return Yaml::stringify(list)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("- x: 1\n  y: 2\n- x: 3\n  y: 4\n") == result)
    }

    it("round-trips a sequence through stringify then parse") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.ArrayList; java.util.List; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m0: Map[String, Object] = ["name": "Alice", "age": 30L]
          |    val m1: Map[String, Object] = ["name": "Bob", "age": 25L]
          |    val list: List[Object] = new ArrayList[Object]() as List[Object]
          |    list.add(m0)
          |    list.add(m1)
          |    val back = (Yaml::parse(Yaml::stringify(list)) as List)
          |    if back.size != 2 { return "size=" + back.size }
          |    if back.get(0) == m0 && back.get(1) == m1 { return "ok" } else { return "mismatch" }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("still parses a flat mapping (non-sequence) the same as before") {
      val result = shell.run(
        """
          |import { onion.Yaml::*; java.util.Map; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val parsed = (Yaml::parse("name: Alice\nage: 30\n") as Map)
          |    return parsed.get("name") + "," + parsed.get("age")
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Alice,30") == result)
    }
  }
}
