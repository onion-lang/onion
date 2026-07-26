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

    it("runs BrokenLogDemo.on") {
      // Returns the number of lines it refused to pretend it had read: a thousand-line
      // log with every 200th line truncated.
      assert(Shell.Success(5) == runSample("run/BrokenLogDemo.on"))
    }
  }
}
