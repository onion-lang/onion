package onion.tools

import onion.tools.readiness.benchmark.{
  BenchmarkOptions,
  BenchmarkOutputFormat,
  BenchmarkRunConfig,
  BenchmarkScenario,
  BenchmarkSession,
  FailureCategory,
  IterationPayload,
  ScenarioKind,
  ScenarioMetadata,
  SourceMetrics
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

    it("uses scenario warmups unless the CLI overrides them"):
      val processScenario = new BenchmarkScenario:
        override val metadata =
          ScenarioMetadata("process", ScenarioKind.ProcessCold, "test", "hash")
        override def defaultWarmupIterations: Int = 3
        override def open(): BenchmarkSession =
          throw new UnsupportedOperationException()

      BenchmarkRunner.effectiveConfig(
        processScenario,
        BenchmarkOptions()
      ).warmupIterations shouldBe 3

      val overrideOptions =
        BenchmarkOptions.parse(Array("--warmups", "0")).toOption.get
      BenchmarkRunner.effectiveConfig(
        processScenario,
        overrideOptions
      ).warmupIterations shouldBe 0

    it("runs and records each scenario with its effective configuration"):
      def scenario(
        id: String,
        kind: ScenarioKind,
        warmups: Int
      ): BenchmarkScenario =
        new BenchmarkScenario:
          override val metadata =
            ScenarioMetadata(id, kind, "test", "hash")
          override def defaultWarmupIterations: Int = warmups
          override def open(): BenchmarkSession = new BenchmarkSession:
            override def runIteration(index: Int): IterationPayload =
              IterationPayload(
                Vector.empty,
                SourceMetrics(1, 1, 1L, 1),
                0
              )

      val scenarios = Vector(
        scenario("process", ScenarioKind.ProcessCold, 3),
        scenario("steady", ScenarioKind.SteadyFresh, 8)
      )
      val options = BenchmarkOptions(
        runConfig = BenchmarkRunConfig(8, 1, 1000L)
      )

      val results = BenchmarkRunner.runScenarios(scenarios, options)

      results.map(_.runConfig.warmupIterations) shouldBe Vector(3, 8)
      results.map(_.warmups.size) shouldBe Vector(3, 8)
