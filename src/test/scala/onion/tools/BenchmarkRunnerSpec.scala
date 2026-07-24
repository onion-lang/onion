package onion.tools

import onion.tools.readiness.benchmark.{
  BenchmarkOptions,
  BenchmarkOutputFormat,
  BenchmarkRunConfig,
  FailureCategory
}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class BenchmarkRunnerSpec extends AnyFunSpec with Matchers:
  describe("BenchmarkRunner.buildReport"):
    it("returns a structured invalid-measurement report when workloads are missing"):
      val root = Files.createTempDirectory("onion-benchmark-missing-workloads")
      val options = BenchmarkOptions(
        runConfig = BenchmarkRunConfig(0, 1, 1000L),
        output = root.resolve("report.json"),
        stdoutFormat = BenchmarkOutputFormat.Text
      )

      val report = BenchmarkRunner.buildReport(root, options)

      report.succeeded shouldBe false
      report.scenarios shouldBe empty
      report.failures.map(_.category) should contain (
        FailureCategory.InvalidMeasurement
      )
      report.failures.map(_.message).mkString(" ") should include (
        "workload source does not exist"
      )
