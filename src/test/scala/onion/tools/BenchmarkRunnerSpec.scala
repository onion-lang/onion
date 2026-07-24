package onion.tools

import onion.tools.readiness.benchmark.{
  BenchmarkOptions,
  BenchmarkOutputFormat,
  BenchmarkRunConfig,
  BenchmarkScenario,
  BenchmarkSession,
  EnvironmentMetadata,
  FailureCategory,
  GitMetadata,
  IterationPayload,
  PerformanceBenchmarkReport,
  ScenarioKind,
  ScenarioMetadata,
  SourceMetrics
}
import onion.tools.readiness.policy.{
  AbsolutePerformanceEvaluation,
  PolicyOverallStatus
}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class BenchmarkRunnerSpec extends AnyFunSpec with Matchers:
  private def report(
    policyStatus: PolicyOverallStatus,
    referenceLane: Boolean
  ): PerformanceBenchmarkReport =
    PerformanceBenchmarkReport(
      schemaVersion = PerformanceBenchmarkReport.CurrentSchemaVersion,
      generatedAt = "2026-07-24T00:00:00Z",
      git = GitMetadata("deadbeef", dirty = false),
      environment = EnvironmentMetadata(
        javaVendor = "test",
        javaVersion = "21",
        osName = "Linux",
        osArch = "amd64",
        processors = 2,
        maxHeapBytes = 1L,
        garbageCollectors = Vector("test"),
        jvmArguments = Vector.empty
      ),
      runConfig = BenchmarkRunConfig(0, 1, 1000L),
      scenarios = Vector.empty,
      failures = Vector.empty,
      policy = AbsolutePerformanceEvaluation(
        referenceLane = referenceLane,
        applicabilityReason = "test",
        checks = Vector.empty,
        overallStatus = policyStatus
      )
    )

  describe("PerformanceBenchmarkReport policy outcome"):
    it("keeps a successful non-reference report informational"):
      val result = report(
        PolicyOverallStatus.Informational,
        referenceLane = false
      )

      result.policy.overallStatus shouldBe PolicyOverallStatus.Informational
      result.succeeded shouldBe true

    it("fails a report whose reference policy failed"):
      val result = report(PolicyOverallStatus.Fail, referenceLane = true)

      result.policy.overallStatus shouldBe PolicyOverallStatus.Fail
      result.succeeded shouldBe false

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
