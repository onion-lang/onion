package onion.tools.readiness.benchmark

import onion.Json
import onion.tools.readiness.policy.{
  AbsolutePerformanceCheck,
  AbsolutePerformanceEvaluation,
  PolicyCheckStatus,
  PolicyOverallStatus
}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class BenchmarkRenderSpec extends AnyFunSpec with Matchers:
  private val scenario =
    ScenarioResult(
      ScenarioMetadata(
        "steady-fresh:onionc:hello",
        ScenarioKind.SteadyFresh,
        "run/Hello.on",
        "abc"
      ),
      runConfig = BenchmarkRunConfig(0, 1, 30000L),
      warmups = Vector.empty,
      measurements = Vector(
        IterationObservation(
          0,
          ObservationKind.Measurement,
          1000000L,
          Vector(PhaseObservation("Parsing", 100L, 1, 1)),
          SourceMetrics(1, 1, 10L, 1),
          0
        )
      ),
      summary = Some(BenchmarkSummary(1000000L, 1000000L, 1000000L, 1000000L)),
      failure = None
    )

  private val report =
    PerformanceBenchmarkReport(
      schemaVersion = PerformanceBenchmarkReport.CurrentSchemaVersion,
      generatedAt = "2026-07-24T00:00:00Z",
      git = GitMetadata("deadbeef", dirty = false),
      environment = EnvironmentMetadata(
        javaVendor = "Eclipse Adoptium",
        javaVersion = "21",
        osName = "Linux",
        osArch = "amd64",
        osReleaseId = "ubuntu",
        osReleaseVersion = "24.04",
        processors = 2,
        totalMemoryBytes = 4294967296L,
        maxHeapBytes = 2147483648L,
        garbageCollectors = Vector("G1 Young Generation", "G1 Old Generation"),
        jvmArguments = Vector("-Xmx2g")
      ),
      runConfig = BenchmarkRunConfig(0, 1, 30000L),
      scenarios = Vector(scenario),
      failures = Vector.empty,
      policy = AbsolutePerformanceEvaluation(
        referenceLane = true,
        applicabilityReason = "reference environment matched",
        checks = Vector(
          AbsolutePerformanceCheck(
            scenarioId = "steady-fresh:onionc:hello",
            status = PolicyCheckStatus.Pass,
            medianNanos = Some(1000000L),
            p95Nanos = Some(1000000L),
            medianCeilingNanos = 150000000L,
            p95CeilingNanos = 300000000L,
            message = "within absolute ceiling"
          )
        ),
        overallStatus = PolicyOverallStatus.Pass
      )
    )

  describe("BenchmarkRender"):
    it("writes schema-versioned JSON with raw nanosecond observations"):
      val json = BenchmarkRender.json(report)
      Json.parseOrNull(json) should not be null
      PerformanceBenchmarkReport.CurrentSchemaVersion shouldBe 3
      json should include ("\"schemaVersion\": 3")
      json should include ("\"elapsedNanos\": 1000000")
      json should include ("\"kind\": \"steady-fresh\"")
      json should include ("\"runConfig\"")
      json should include ("\"warmupIterations\": 0")
      json should include ("\"osReleaseId\": \"ubuntu\"")
      json should include ("\"osReleaseVersion\": \"24.04\"")
      json should include ("\"totalMemoryBytes\": 4294967296")
      json should include ("\"overallStatus\": \"pass\"")
      json should include ("\"medianCeilingNanos\": 150000000")
      json should not include "elapsedMillis"

    it("renders a concise human summary in milliseconds"):
      val text = BenchmarkRender.text(report)
      text should include ("steady-fresh:onionc:hello")
      text should include ("median=1.00ms")
      text should include ("p95=1.00ms")
      text should include ("1 measured")
      text should include ("absolute-policy=pass")
      text should include ("reference-lane=true")
