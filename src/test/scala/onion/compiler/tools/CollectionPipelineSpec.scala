package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for collection pipelines: paren-less trailing lambdas plus the
 * builtin extension methods that back java.util.List/Iterable with
 * onion.Colls / onion.Iterables statics, with closure parameter types
 * inferred from the extension's signature.
 */
class CollectionPipelineSpec extends AbstractShellSpec {

  describe("Collection pipelines") {
    it("maps with a paren-less trailing lambda") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "b"]
          |    return xs.map { s => s.toUpperCase() }.toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineMap.on",
        Array()
      )
      assert(Shell.Success("[A, B]") == result)
    }

    it("chains filter and map") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["alpha beta", "gamma", "alpha delta"]
          |    return xs.filter { s => s.contains("alpha") }.map { s => s.length() }.toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineChain.on",
        Array()
      )
      assert(Shell.Success("[10, 11]") == result)
    }

    it("joins elements with mkString") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "b", "c"]
          |    return xs.mkString(", ")
          |  }
          |}
          |""".stripMargin,
        "PipelineMkString.on",
        Array()
      )
      assert(Shell.Success("a, b, c") == result)
    }

    it("computes min and max of a comparable list") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = [3, 1, 4, 1, 5]
          |    return (xs.min() as Int) + ":" + (xs.max() as Int)
          |  }
          |}
          |""".stripMargin,
        "PipelineMinMax.on",
        Array()
      )
      assert(Shell.Success("1:5") == result)
    }

    it("takes and drops a leading run with takeWhile/dropWhile") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = [1, 2, 3, 8, 1]
          |    return xs.takeWhile { x => x < 5 }.toString() + xs.dropWhile { x => x < 5 }.toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineTakeWhile.on",
        Array()
      )
      assert(Shell.Success("[1, 2, 3][8, 1]") == result)
    }

    it("returns head and tail") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = [10, 20, 30]
          |    return (xs.head() as Int) + ":" + xs.tail().toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineHeadTail.on",
        Array()
      )
      assert(Shell.Success("10:[20, 30]") == result)
    }

    it("keeps explicit Colls:: calls working") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = [1, 2, 3]
          |    return Colls::map(xs, (x: Integer) -> (x as Int) * 2).toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineExplicit.on",
        Array()
      )
      assert(Shell.Success("[2, 4, 6]") == result)
    }

    it("does not shadow real instance methods") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = [1, 2, 3]
          |    return xs.size()
          |  }
          |}
          |""".stripMargin,
        "PipelineNoShadow.on",
        Array()
      )
      assert(Shell.Success(3) == result)
    }
    it("maps over arrays (String.split pipeline)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val parts = "a,bb,ccc".split(",")
          |    return parts.map { s => s.length() }.toString()
          |  }
          |}
          |""".stripMargin,
        "ArrayPipeline.on",
        Array()
      )
      assert(Shell.Success("[1, 2, 3]") == result)
    }
    it("destructures map entries with foreach (k, v)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val ages = ["alice": 12, "bob": 34]
          |    var s = ""
          |    foreach (name, age) in ages {
          |      s = s + name + "=" + age + ";"
          |    }
          |    return s
          |  }
          |}
          |""".stripMargin,
        "ForeachKV.on",
        Array()
      )
      assert(Shell.Success("alice=12;bob=34;") == result)
    }

    it("partitions a list into [matching, non-matching]") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "bb", "ccc"]
          |    return xs.partition { s => s.length() > 1 }.toString()
          |  }
          |}
          |""".stripMargin,
        "PipelinePartition.on",
        Array()
      )
      assert(Shell.Success("[[bb, ccc], [a]]") == result)
    }

    it("partitions with an empty matching side") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "b"]
          |    return xs.partition { s => s.length() > 5 }.toString()
          |  }
          |}
          |""".stripMargin,
        "PipelinePartitionEmpty.on",
        Array()
      )
      assert(Shell.Success("[[], [a, b]]") == result)
    }

    it("removes duplicates with distinct (preserving first-occurrence order)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "b", "a", "c", "b"]
          |    return xs.distinct().toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineDistinct.on",
        Array()
      )
      assert(Shell.Success("[a, b, c]") == result)
    }

    it("sorts by a key with sortedBy") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["ccc", "a", "bb"]
          |    return xs.sortedBy { s => s.length() }.toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineSortedBy.on",
        Array()
      )
      assert(Shell.Success("[a, bb, ccc]") == result)
    }

    it("reduces a list with reduce (no initial value)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = [1, 2, 3, 4]
          |    val total = xs.reduce { a, b => (a as Int) + (b as Int) }
          |    return "" + (total as Int)
          |  }
          |}
          |""".stripMargin,
        "PipelineReduce.on",
        Array()
      )
      assert(Shell.Success("10") == result)
    }

    it("reduces a single-element list to that element") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = [42]
          |    val only = xs.reduce { a, b => (a as Int) + (b as Int) }
          |    return "" + (only as Int)
          |  }
          |}
          |""".stripMargin,
        "PipelineReduceSingle.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("transforms map values with mapValues") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["a": 1, "b": 2]
          |    return m.mapValues { v => (v as Int) * 10 }.toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineMapValues.on",
        Array()
      )
      assert(Shell.Success("{a=10, b=20}") == result)
    }

    it("filters map entries with filterMap") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["a": 1, "b": 2, "c": 3]
          |    return m.filterMap { k, v => (v as Int) > 1 }.toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineFilterMap.on",
        Array()
      )
      assert(Shell.Success("{b=2, c=3}") == result)
    }

    it("any/all/none on a matching list") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "bb", "ccc"]
          |    val a = xs.any { s => s.length() > 2 }
          |    val b = xs.all { s => s.length() > 0 }
          |    val c = xs.none { s => s.length() > 5 }
          |    return "" + a + "," + b + "," + c
          |  }
          |}
          |""".stripMargin,
        "PipelineQuantTrue.on",
        Array()
      )
      assert(Shell.Success("true,true,true") == result)
    }

    it("any/all/none on a non-matching list") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "bb"]
          |    val a = xs.any { s => s.length() > 5 }
          |    val b = xs.all { s => s.length() > 1 }
          |    val c = xs.none { s => s.length() > 0 }
          |    return "" + a + "," + b + "," + c
          |  }
          |}
          |""".stripMargin,
        "PipelineQuantFalse.on",
        Array()
      )
      assert(Shell.Success("false,false,false") == result)
    }

    it("zips two lists into pairs, truncating to the shorter") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "b", "c"]
          |    val ys = ["x", "y"]
          |    return xs.zip(ys).toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineZip.on",
        Array()
      )
      assert(Shell.Success("[[a, x], [b, y]]") == result)
    }

    it("concatenates two lists") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "b"]
          |    val ys = ["c", "d"]
          |    return xs.concat(ys).toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineConcat.on",
        Array()
      )
      assert(Shell.Success("[a, b, c, d]") == result)
    }

    it("flattens a list of lists") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xss = [["a", "b"], ["c"]]
          |    return xss.flatten().toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineFlatten.on",
        Array()
      )
      assert(Shell.Success("[a, b, c]") == result)
    }

    it("lists a map's keys in insertion order") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["a": 1, "b": 2, "c": 3]
          |    return m.keys().toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineKeys.on",
        Array()
      )
      assert(Shell.Success("[a, b, c]") == result)
    }

    it("lists a map's values in insertion order") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["a": 1, "b": 2, "c": 3]
          |    return m.values().toString()
          |  }
          |}
          |""".stripMargin,
        "PipelineValues.on",
        Array()
      )
      assert(Shell.Success("[1, 2, 3]") == result)
    }

    it("folds from the left with foldl") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = [1, 2, 3, 4]
          |    val total = xs.foldl(0) { acc, x => (acc as Int) + (x as Int) }
          |    return "" + (total as Int)
          |  }
          |}
          |""".stripMargin,
        "PipelineFoldl.on",
        Array()
      )
      assert(Shell.Success("10") == result)
    }

    it("checks a universal predicate with forAll") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = ["a", "bb", "ccc"]
          |    val a = xs.forAll { s => s.length() > 0 }
          |    val b = xs.forAll { s => s.length() > 1 }
          |    return "" + a + "," + b
          |  }
          |}
          |""".stripMargin,
        "PipelineForAll.on",
        Array()
      )
      assert(Shell.Success("true,false") == result)
    }
  }
}
