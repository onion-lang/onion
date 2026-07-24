package onion.tools.readiness.benchmark

import onion.Json
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
      schemaVersion = 1,
      generatedAt = "2026-07-24T00:00:00Z",
      git = GitMetadata("deadbeef", dirty = false),
      environment = EnvironmentMetadata(
        javaVendor = "Eclipse Adoptium",
        javaVersion = "21",
        osName = "Linux",
        osArch = "amd64",
        processors = 2,
        maxHeapBytes = 2147483648L,
        garbageCollectors = Vector("G1 Young Generation", "G1 Old Generation"),
        jvmArguments = Vector("-Xmx2g")
      ),
      runConfig = BenchmarkRunConfig(0, 1, 30000L),
      scenarios = Vector(scenario),
      failures = Vector.empty
    )

  describe("BenchmarkRender"):
    it("writes schema-versioned JSON with raw nanosecond observations"):
      val json = BenchmarkRender.json(report)
      Json.parseOrNull(json) should not be null
      json should include ("\"schemaVersion\": 1")
      json should include ("\"elapsedNanos\": 1000000")
      json should include ("\"kind\": \"steady-fresh\"")
      json should not include "elapsedMillis"

    it("renders a concise human summary in milliseconds"):
      val text = BenchmarkRender.text(report)
      text should include ("steady-fresh:onionc:hello")
      text should include ("median=1.00ms")
      text should include ("p95=1.00ms")
      text should include ("1 measured")
