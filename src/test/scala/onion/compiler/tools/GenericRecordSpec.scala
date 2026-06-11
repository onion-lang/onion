package onion.compiler.tools

import onion.tools.Shell

/**
 * Generic records: record Pair[A, B](first: A, second: B) — components
 * type through the applied record, and copy/destructuring keep working.
 */
class GenericRecordSpec extends AbstractShellSpec {

  describe("Generic records") {
    it("constructs and reads components with substituted types") {
      val result = shell.run(
        """
          |record Pair[A, B](first: A, second: B)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Pair[String, Integer]("gen", 9)
          |    val s: String = p.first()
          |    return s + ":" + p.second()
          |  }
          |}
          |""".stripMargin,
        "GenericRecordBasic.on",
        Array()
      )
      assert(Shell.Success("gen:9") == result)
    }

    it("destructures with substituted component types") {
      val result = shell.run(
        """
          |record Pair[A, B](first: A, second: B)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val (s, n) = new Pair[String, Integer]("d", 7)
          |    return "" + s + n
          |  }
          |}
          |""".stripMargin,
        "GenericRecordDestructure.on",
        Array()
      )
      assert(Shell.Success("d7") == result)
    }

    it("supports named-argument partial copy with primitive boxing") {
      val result = shell.run(
        """
          |record Pair[A, B](first: A, second: B)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Pair[String, Integer]("keep", 9)
          |    val q = p.copy(second = 42)
          |    return q.first() + ":" + q.second()
          |  }
          |}
          |""".stripMargin,
        "GenericRecordCopy.on",
        Array()
      )
      assert(Shell.Success("keep:42") == result)
    }

    it("accepts nullable type arguments") {
      val result = shell.run(
        """
          |record Pair[A, B](first: A, second: B)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val none: String? = null
          |    val p = new Pair[String?, String](none, "ok")
          |    return "" + p.first() + "-" + p.second()
          |  }
          |}
          |""".stripMargin,
        "GenericRecordNullable.on",
        Array()
      )
      assert(Shell.Success("null-ok") == result)
    }
  }
}
