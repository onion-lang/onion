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
  }
}
