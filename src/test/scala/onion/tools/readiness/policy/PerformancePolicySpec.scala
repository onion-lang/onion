package onion.tools.readiness.policy

import onion.tools.readiness.benchmark.*
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class PerformancePolicySpec
    extends AnyFunSpec
    with Matchers
    with OptionValues:
  private val fourGiB = 4L * 1024L * 1024L * 1024L
  private val twoGiB = 2L * 1024L * 1024L * 1024L

  private val reference =
    EnvironmentMetadata(
      javaVendor = "Eclipse Adoptium",
      javaVersion = "21.0.11",
      osName = "Linux",
      osArch = "amd64",
      osReleaseId = "ubuntu",
      osReleaseVersion = "24.04",
      processors = 2,
      totalMemoryBytes = fourGiB,
      maxHeapBytes = twoGiB,
      garbageCollectors = Vector("G1 Young Generation", "G1 Old Generation"),
      jvmArguments = Vector("-Xmx2g", "-XX:+UseG1GC")
    )

  private def scenario(
    budget: PerformanceBudget,
    medianNanos: Long = -1L,
    p95Nanos: Long = -1L,
    failure: Option[BenchmarkFailure] = None
  ): ScenarioResult =
    val kind =
      if budget.scenarioId.startsWith("process-cold") then ScenarioKind.ProcessCold
      else if budget.scenarioId.startsWith("persistent-session") then
        ScenarioKind.PersistentSession
      else if budget.scenarioId.startsWith("multi-file") then ScenarioKind.MultiFile
      else ScenarioKind.SteadyFresh
    val median =
      if medianNanos < 0L then budget.medianCeilingNanos else medianNanos
    val p95 = if p95Nanos < 0L then budget.p95CeilingNanos else p95Nanos
    ScenarioResult(
      metadata = ScenarioMetadata(
        budget.scenarioId,
        kind,
        "test workload",
        "hash"
      ),
      runConfig = BenchmarkRunConfig(0, 1, 30000L),
      warmups = Vector.empty,
      measurements = Vector.empty,
      summary =
        if failure.isEmpty then Some(BenchmarkSummary(median, p95, median, p95))
        else None,
      failure = failure
    )

  private def atCeilings: Vector[ScenarioResult] =
    AbsolutePerformancePolicy.budgets.map(scenario(_))

  describe("AbsolutePerformancePolicy reference lane"):
    it("recognizes only the complete reference environment"):
      AbsolutePerformancePolicy.isReferenceLane(reference) shouldBe true

      val nonReference = Vector(
        reference.copy(osName = "Mac OS X"),
        reference.copy(osReleaseId = "debian"),
        reference.copy(osReleaseVersion = "22.04"),
        reference.copy(osArch = "aarch64"),
        reference.copy(javaVendor = "Oracle Corporation"),
        reference.copy(javaVersion = "17.0.12"),
        reference.copy(processors = 3),
        reference.copy(totalMemoryBytes = fourGiB + 1L),
        reference.copy(maxHeapBytes = twoGiB - 1L),
        reference.copy(garbageCollectors = Vector("ZGC"))
      )

      nonReference.foreach { environment =>
        withClue(environment) {
          AbsolutePerformancePolicy.isReferenceLane(environment) shouldBe false
        }
      }

  describe("AbsolutePerformancePolicy.evaluate"):
    it("passes every result exactly at its inclusive ceiling"):
      val result =
        AbsolutePerformancePolicy.evaluate(reference, atCeilings)

      result.referenceLane shouldBe true
      result.overallStatus shouldBe PolicyOverallStatus.Pass
      result.checks should have size 5
      all(result.checks.map(_.status)) shouldBe PolicyCheckStatus.Pass

    it("fails a median that exceeds its ceiling by one nanosecond"):
      val hello =
        AbsolutePerformancePolicy.budgets
          .find(_.scenarioId == "steady-fresh:onionc:hello")
          .value
      val scenarios =
        atCeilings.map {
          case result if result.metadata.id == hello.scenarioId =>
            scenario(hello, medianNanos = hello.medianCeilingNanos + 1L)
          case result => result
        }

      val result =
        AbsolutePerformancePolicy.evaluate(reference, scenarios)
      val check = result.checks.find(_.scenarioId == hello.scenarioId).value

      result.overallStatus shouldBe PolicyOverallStatus.Fail
      check.status shouldBe PolicyCheckStatus.Fail
      check.message should include ("median")

    it("fails a p95 that exceeds its ceiling by one nanosecond"):
      val repl =
        AbsolutePerformancePolicy.budgets
          .find(_.scenarioId.startsWith("persistent-session"))
          .value
      val scenarios =
        atCeilings.map {
          case result if result.metadata.id == repl.scenarioId =>
            scenario(repl, p95Nanos = repl.p95CeilingNanos + 1L)
          case result => result
        }

      val check =
        AbsolutePerformancePolicy
          .evaluate(reference, scenarios)
          .checks
          .find(_.scenarioId == repl.scenarioId)
          .value

      check.status shouldBe PolicyCheckStatus.Fail
      check.message should include ("p95")

    it("marks every absolute check not-applicable off the reference lane"):
      val result =
        AbsolutePerformancePolicy.evaluate(
          reference.copy(processors = 16),
          atCeilings
        )

      result.referenceLane shouldBe false
      result.overallStatus shouldBe PolicyOverallStatus.Informational
      all(result.checks.map(_.status)) shouldBe
        PolicyCheckStatus.NotApplicable

    it("fails with unknown checks when reference evidence is missing"):
      val result =
        AbsolutePerformancePolicy.evaluate(reference, Vector.empty)

      result.overallStatus shouldBe PolicyOverallStatus.Fail
      all(result.checks.map(_.status)) shouldBe PolicyCheckStatus.Unknown

    it("marks a failed reference scenario unknown"):
      val budget = AbsolutePerformancePolicy.budgets.head
      val failure = BenchmarkFailure(
        FailureCategory.ScenarioFailure,
        "child failed"
      )
      val scenarios =
        atCeilings.map {
          case result if result.metadata.id == budget.scenarioId =>
            scenario(budget, failure = Some(failure))
          case result => result
        }

      val check =
        AbsolutePerformancePolicy
          .evaluate(reference, scenarios)
          .checks
          .find(_.scenarioId == budget.scenarioId)
          .value

      check.status shouldBe PolicyCheckStatus.Unknown
      check.message should include ("child failed")
