package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/**
 * Regression test for issue #2507: a record component typed with a `type` alias to a
 * scalar (`type Port = Int`) broke `from re"..."`, `derive!(Json)` and `shape` synthesis
 * with an E0021 about a constructor call the user never wrote, because the synthesized
 * parser read the component type as written and so never converted the captured text.
 * The alias is now resolved first, so the component behaves like the type it names.
 */
class RecordAliasComponentSpec extends AbstractShellSpec {

  private def runMain(body: String): Shell.Result =
    shell.run(body, "None", Array())

  describe("a record component typed with an alias") {
    it("converts in a from re\"...\" parser") {
      val r = runMain(
        """type Port = Int
          |record Addr(host: String, port: Port) from re"(\S+):(\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Addr? = Addr::parse("h:80")
          |    if a != null { return a.host() + "/" + (a.port() + 1) } else { return "FAIL" }
          |  }
          |}
          |""".stripMargin)
      assert(Shell.Success("h/81") == r)
    }

    it("converts through a chain of aliases and a nullable alias") {
      val r = runMain(
        """type Count = Int
          |type N = Count
          |type MaybeN = Int?
          |record P(a: N, b: N?, c: MaybeN) from re"(\d+)-(\d+)?-(\d+)?"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p: P? = P::parse("1--3")
          |    if p != null { return (p.a() + 1) + " " + p.b() + " " + p.c() } else { return "FAIL" }
          |  }
          |}
          |""".stripMargin)
      assert(Shell.Success("2 null 3") == r)
    }

    it("round-trips through derive!(Json)") {
      val r = runMain(
        """type Port = Int
          |type Name = String
          |record Cfg(name: Name, port: Port) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c: Cfg? = Cfg::fromJson("{\"name\": \"web\", \"port\": 8080}")
          |    if c != null { return c.name() + ":" + (c.port() + 1) + " " + Cfg::toJson(c) } else { return "FAIL" }
          |  }
          |}
          |""".stripMargin)
      assert(Shell.Success("web:8081 {\"name\":\"web\",\"port\":8080}") == r)
    }

    it("reads in a regex shape and a json shape") {
      val r = runMain(
        """type Port = Int
          |type Tags = List[String]
          |record Addr(host: String, port: Port, alt: Port?, more: List[Port], tags: Tags) {
          |  shape doc = json
          |}
          |record Hp(host: String, port: Port) {
          |  shape line = re"(\S+):(\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = Addr::doc().parse("{\"host\": \"h\", \"port\": 80, \"more\": [1, 2], \"tags\": [\"a\"]}").get()
          |    val h = Hp::line().parse("x:9").get()
          |    return a.host() + " " + (a.port() + 1) + " " + a.alt() + " " + a.more() + " " + a.tags() + " " + (h.port() + 1)
          |  }
          |}
          |""".stripMargin)
      assert(Shell.Success("h 81 null [1, 2] [a] 10") == r)
    }

    it("to an unsupported type is still E0061, not E0021") {
      val result = new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10)).compileDetailed(
        Seq(new StreamInputSource(() => new StringReader(
          """type When = java.util.Date
            |record Ev(name: String, at: When) from re"(\S+) (\S+)"
            |""".stripMargin), "Test.on")))
      val codes = result.allErrors.flatMap(_.code)
      assert(codes.contains("E0061"), codes)
      assert(!codes.contains("E0021"), codes)
    }
  }
}
