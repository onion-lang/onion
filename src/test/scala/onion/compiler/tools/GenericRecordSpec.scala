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

    // #3101: the copy is the record at the receiver's type arguments, not the raw record.
    describe("copy keeps the type arguments (#3101)") {
      def run(body: String, file: String): Shell.Result = shell.run(
        s"""
          |record P[A, B](x: A, y: B)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |$body
          |  }
          |}
          |""".stripMargin,
        file,
        Array()
      )

      it("types a component read from a partial copy") {
        assert(Shell.Success("s") == run(
          """    val q = new P[String, Int]("s", 1).copy(y = 42)
            |    val s: String = q.x()
            |    return s""".stripMargin, "GenericCopyPartial.on"))
      }

      it("types a component read from a full copy") {
        assert(Shell.Success("s2") == run(
          """    val q = new P[String, Int]("s", 1).copy(y = 2).copy()
            |    val s: String = q.x()
            |    return s + q.y()""".stripMargin, "GenericCopyFull.on"))
      }

      it("does arithmetic on a primitive component of a copy") {
        assert(Shell.Success("43") == run(
          """    val n: Int = new P[String, Int]("s", 1).copy(x = "u").y() + 42
            |    return "" + n""".stripMargin, "GenericCopyArith.on"))
      }

      it("assigns a copy to the applied record type") {
        assert(Shell.Success("t") == run(
          """    val p: P[String, Int] = new P[String, Int]("s", 1)
            |    val q: P[String, Int] = p.copy(x = "t")
            |    return q.x()""".stripMargin, "GenericCopyAssign.on"))
      }

      it("still rejects a copy read at the wrong type") {
        val result = run(
          """    val q = new P[String, Int]("s", 1).copy(y = 42)
            |    val n: Int = q.x()
            |    return "" + n""".stripMargin, "GenericCopyWrong.on")
        assert(result == Shell.Failure(-1))
      }
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
