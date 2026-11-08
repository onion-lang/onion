package onion.compiler.tools

import onion.tools.Shell

/** `xs[i]++` and `xs[i]--` on a List or Map element behave as `xs[i] += 1` (#2587). */
class ListIndexIncDecSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |import { java.util.* }
         |class Test {
         |public:
         |  static def main(args: String[]): Int {
         |$body
         |  }
         |}
         |""".stripMargin, "None", Array())

  private val list = "    val ns: List[Int] = new ArrayList[Int]()\n    ns.add(1)\n    ns.add(10)\n"

  describe("++ and -- on an indexed element") {
    it("ns[0]++ adds one to a List[Int] element") {
      assert(Shell.Success(2) == run(list + "    ns[0]++\n    return ns[0]"))
    }
    it("ns[1]-- subtracts one") {
      assert(Shell.Success(9) == run(list + "    ns[1]--\n    return ns[1]"))
    }
    it("the value of ns[0]++ is the old one") {
      assert(Shell.Success(1) == run(list + "    val old = ns[0]++\n    return old"))
    }
    it("a Map counter works: counts[k]++") {
      val m = "    val m: Map[String, Int] = new HashMap[String, Int]()\n    m[\"a\"] = 4\n    m[\"a\"]++\n    return m[\"a\"]"
      assert(Shell.Success(5) == run(m))
    }
    it("the index expression runs once") {
      val body = list + "    val c = new java.util.concurrent.atomic.AtomicInteger(0)\n    foreach i in 0..2 { ns[c.getAndIncrement() * 0]++ }\n    return ns[0] * 10 + c.get()"
      assert(Shell.Success(43) == run(body))
    }
    it("an array element still works") {
      assert(Shell.Success(1) == run("    val a: Int[] = new Int[2]\n    a[0]++\n    return a[0]"))
    }
    it("a List[String] element is still rejected with E0001") {
      val r = run("    val ss: List[String] = new ArrayList[String]()\n    ss.add(\"x\")\n    ss[0]++\n    return 0")
      assert(Shell.Failure(-1) == r)
    }
  }
}
