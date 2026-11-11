package onion.compiler.tools

import onion.tools.Shell

/** `[1, 2]` typed `List[Long?]` (or a `Map[K, Long?]` literal) holds Longs, not Integers (#2698). */
class NullableLiteralWideningSpec extends AbstractShellSpec {
  private def run(body: String, ret: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): $ret {
         |$body
         |  }
         |}
         |""".stripMargin, "None", Array())

  describe("Int elements of a literal typed with a nullable wider numeric") {
    it("val xs: List[Long?] = [1, 2] reads back a Long") {
      assert(Shell.Success(1L) == run("val xs: List[Long?] = [1, 2]\nval a: Long? = xs[0]\nreturn a ?: 0L", "Long"))
    }
    it("val ds: List[Double?] = [1, 2.5] reads back a Double") {
      assert(Shell.Success(1.0) == run("val ds: List[Double?] = [1, 2.5]\nval d: Double? = ds[0]\nreturn d ?: 0.0", "Double"))
    }
    it("a mixed [1, null, 3L] typed List[Long?] holds only Longs") {
      assert(Shell.Success("Long,null,Long") == run(
        "val xs: List[Long?] = [1, null, 3L]\nval sb = new StringBuilder()\nforeach x in xs {\n  if sb.length() > 0 { sb.append(\",\") }\n  val o: Object? = x\n  sb.append(if o == null { \"null\" } else { o.getClass().getSimpleName() })\n}\nreturn sb.toString()",
        "String"))
    }
    it("a Map[String, Long?] literal value reads back a Long") {
      assert(Shell.Success(1L) == run("val ms: Map[String, Long?] = [\"a\": 1]\nval m: Long? = ms[\"a\"]\nreturn m ?: 0L", "Long"))
    }
    it("a Map[Long?, String] literal key is a Long") {
      assert(Shell.Success("x") == run("val ms: Map[Long?, String] = [1: \"x\"]\nreturn ms[1L] ?: \"missing\"", "String"))
    }
    it("List[Int?] = [1, 2] is unchanged") {
      assert(Shell.Success(1) == run("val xs: List[Int?] = [1, 2]\nval a: Int? = xs[0]\nreturn a ?: 0", "Int"))
    }
  }
}
