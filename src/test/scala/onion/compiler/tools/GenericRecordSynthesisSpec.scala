package onion.compiler.tools

import onion.tools.Shell

/**
 * `derive!`, `from re"..."` and `shape` on a generic record (#2321): the synthesized
 * statics are generic over the record's own type parameters, so they no longer refer to
 * the raw record type and trip E0066 at a `record` keyword the user wrote correctly.
 */
class GenericRecordSynthesisSpec extends AbstractShellSpec {

  private def run(source: String, name: String): Shell.Result =
    shell.run(source.stripMargin, name, Array())

  describe("Synthesized methods on a generic record") {
    it("derive!(Json, Yaml) round-trips") {
      val result = run(
        """
          |record Box[T](n: Int, s: String) derive!(Json, Yaml)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = new Box[String](1, "x")
          |    val j: Box[String]? = Box::fromJson(Box::toJson(b))
          |    val y: Box[String]? = Box::fromYaml(Box::toYaml(b))
          |    return Box::toJson(b) + "|" + j + "|" + y + "|" + Box::fromJsonList("[{\"n\":2,\"s\":\"y\"}]")
          |  }
          |}
          |""",
        "GenericRecordDeriveJson.on"
      )
      assert(Shell.Success("""{"n":1,"s":"x"}|Box(n=1, s=x)|Box(n=1, s=x)|[Box(n=2, s=y)]""") == result)
    }

    it("from re\"...\" parses, including a bounded type parameter") {
      val result = run(
        """
          |record Tag[T extends CharSequence](n: Int, name: String?) from re"(\d+)(?: (\w+))?"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val t: Tag[String]? = Tag::parse("5 bob")
          |    return "" + t + "|" + Tag::parse("nope") + "|" + Tag::parseAll("1 a\n2\nx")
          |  }
          |}
          |""",
        "GenericRecordFromRegex.on"
      )
      assert(Shell.Success("Tag(n=5, name=bob)|null|[Tag(n=1, name=a), Tag(n=2, name=null)]") == result)
    }

    it("shape clauses parse and print") {
      val result = run(
        """
          |record Pair[A, B](n: Int, s: String) {
          |  shape line = re"(\d+):(\w+)"
          |  shape doc = json
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val line: onion.Shape[Pair[String, Int]] = Pair::line()
          |    val p = line.parse("7:ab").get()
          |    return "" + p + "|" + line.print(p) + "|" + Pair::doc().parse("{\"n\":9,\"s\":\"w\"}").get()
          |  }
          |}
          |""",
        "GenericRecordShape.on"
      )
      assert(Shell.Success("Pair(n=7, s=ab)|7:ab|Pair(n=9, s=w)") == result)
    }

    it("still reports a bad component or regex with its own code, not E0066") {
      def codes(src: String): Seq[String] =
        new onion.compiler.OnionCompiler(new onion.compiler.CompilerConfig(List("."), null, "UTF-8", "", 10))
          .compile(Seq(new onion.compiler.StreamInputSource(() => new java.io.StringReader(src), "test.on"))) match {
          case onion.compiler.CompilationOutcome.Failure(errs) => errs.flatMap(_.errorCode)
          case other                                           => fail(s"expected a compile failure, got $other")
        }
      val e62 = codes("record Bad[T](v: T) derive!(Json)\n")
      assert(e62.contains("E0062") && !e62.contains("E0066"), e62)
      val e60 = codes("record Bad[T](n: Int) from re\"(\\d+) (\\d+)\"\n")
      assert(e60.contains("E0060") && !e60.contains("E0066"), e60)
      val e61 = codes("record Bad[T](xs: Map[String, String]) {\n  shape doc = json\n}\nprintln(Bad::doc())\n")
      assert(e61.contains("E0061") && !e61.contains("E0066") && !e61.contains("E0005"), e61)
    }
  }
}
