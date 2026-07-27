package onion.compiler.tools

import onion.tools.Shell

import scala.io.{Codec, Source}

class RunSamplesSpec extends AbstractShellSpec {
  private def load(path: String): String = {
    val source = Source.fromFile(path)(Codec.UTF8)
    try source.mkString
    finally source.close()
  }

  private def runSample(path: String): Shell.Result =
    shell.run(load(path), path, Array())

  describe("run/ samples") {
    it("runs ValVarInference.on") {
      assert(Shell.Success(60) == runSample("run/ValVarInference.on"))
    }

    it("runs FunctionTypesSample.on") {
      assert(Shell.Success(12) == runSample("run/FunctionTypesSample.on"))
    }

    it("runs PairSample.on") {
      assert(Shell.Success("x") == runSample("run/PairSample.on"))
    }

    it("runs JavaCollectionsSample.on") {
      assert(Shell.Success("a") == runSample("run/JavaCollectionsSample.on"))
    }

    it("runs PrimitivePrint.on") {
      assert(Shell.Success(0) == runSample("run/PrimitivePrint.on"))
    }

    it("runs RecordLaws.on") {
      assert(Shell.Success("ok") == runSample("run/RecordLaws.on"))
    }

    it("runs ToolDemo.on: contract, plan (no effects), then execution") {
      val dir = java.nio.file.Files.createTempDirectory("tool-demo-spec")
      val src = dir.resolve("access.log"); val out = dir.resolve("report.txt")
      java.nio.file.Files.writeString(src,
        "10.0.0.1 GET /index 200\nbroken\n10.0.0.2 GET /about 404\n")
      def run(args: String*): Shell.Result =
        shell.run(load("run/ToolDemo.on"), "run/ToolDemo.on", args.toArray)

      assert(Shell.Success(0) == run("--contract"))
      // --plan binds the real arguments and performs nothing.
      assert(Shell.Success(0) == run(src.toString, out.toString, "--plan"))
      assert(!java.nio.file.Files.exists(out), "--plan wrote the report")
      // The real run writes it.
      assert(Shell.Success(0) == run(src.toString, out.toString, "--top", "1"))
      val report = java.nio.file.Files.readString(out)
      assert(report.contains("lines read:    2"), report)
      assert(report.contains("lines skipped: 1"), report)
      assert(report.contains("line 2"), report)
    }

    it("runs ConfigEditDemo.on: the edit changes exactly one line") {
      val dir = java.nio.file.Files.createTempDirectory("config-edit-spec")
      val conf = dir.resolve("app.conf")
      val original =
        "# app config \u2014 do not commit secrets\nhost = prod.example.com\n\n" +
        "# port history: 8080 until 2025\nport   =   8080\ndebug = false\nowner = kota\n"
      java.nio.file.Files.writeString(conf, original)
      def run(args: String*): Shell.Result =
        shell.run(load("run/ConfigEditDemo.on"), "run/ConfigEditDemo.on", args.toArray)

      // Plan first: nothing changes.
      assert(Shell.Success(0) == run(conf.toString, "9090", "--plan"))
      assert(java.nio.file.Files.readString(conf) == original, "--plan modified the file")
      // The edit changes exactly the port line; every other byte survives.
      assert(Shell.Success(0) == run(conf.toString, "9090"))
      val edited = java.nio.file.Files.readString(conf)
      val diff = original.split("\n", -1).zip(edited.split("\n", -1)).filter { case (a, b) => a != b }
      assert(diff.toSeq == Seq(("port   =   8080", "port   =   9090")), diff.toSeq.toString)
    }

    it("runs FixedWidthDemo.on") {
      // A user-written Shape: prints the padded record, then eachLine splits
      // 2 good rows from 1 bad one. Compiling at all proves the L1 example held.
      assert(runSample("run/FixedWidthDemo.on").isInstanceOf[Shell.Success])
    }

    it("runs BrokenLogDemo.on") {
      // Returns the number of lines it refused to pretend it had read: a thousand-line
      // log with every 200th line truncated.
      assert(Shell.Success(5) == runSample("run/BrokenLogDemo.on"))
    }

    it("runs Primes.on") {
      assert(Shell.Success(null) == runSample("run/Primes.on"))
    }

    it("runs LogSummary.on") {
      assert(Shell.Success(null) == runSample("run/LogSummary.on"))
    }

    it("runs DeptReport.on") {
      assert(Shell.Success(null) == runSample("run/DeptReport.on"))
    }

    it("runs WordStats.on") {
      assert(Shell.Success(null) == runSample("run/WordStats.on"))
    }

    it("runs AdtExpr.on") {
      assert(Shell.Success(null) == runSample("run/AdtExpr.on"))
    }

    it("runs SetOperations.on") {
      assert(Shell.Success("union=[apple, banana, cherry, date] intersection=[banana, cherry] diff=[apple]") ==
        runSample("run/SetOperations.on"))
    }

    it("runs PrimitivePredicate.on") {
      assert(Shell.Success("[1, 3, 5]") == runSample("run/PrimitivePredicate.on"))
    }

    it("runs PrimitiveFunctionalInterfaces.on") {
      assert(Shell.Success("[isPositive(5) = true, answer.get() = 42, doubleIt(21) = 42]") ==
        runSample("run/PrimitiveFunctionalInterfaces.on"))
    }

    it("runs SortWithPrimitiveComparator.on") {
      assert(Shell.Success("[10, 20, 30] [10, 20, 30]") == runSample("run/SortWithPrimitiveComparator.on"))
    }

    it("runs CollectionUtilities.on") {
      assert(Shell.Success("[Bob, Alice, Charlie]") == runSample("run/CollectionUtilities.on"))
    }
  }
}
