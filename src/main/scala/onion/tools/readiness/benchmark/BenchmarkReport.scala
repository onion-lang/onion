package onion.tools.readiness.benchmark

import onion.tools.readiness.policy.{
  AbsolutePerformanceEvaluation,
  AbsolutePerformancePolicy,
  PolicyOverallStatus
}
import java.time.Instant

final case class PerformanceBenchmarkReport(
  schemaVersion: Int,
  generatedAt: String,
  git: GitMetadata,
  environment: EnvironmentMetadata,
  runConfig: BenchmarkRunConfig,
  scenarios: Vector[ScenarioResult],
  failures: Vector[BenchmarkFailure],
  policy: AbsolutePerformanceEvaluation
):
  def succeeded: Boolean =
    failures.isEmpty &&
      scenarios.forall(_.succeeded) &&
      policy.overallStatus != PolicyOverallStatus.Fail

object PerformanceBenchmarkReport:
  val CurrentSchemaVersion = 3

  def create(
    git: GitMetadata,
    environment: EnvironmentMetadata,
    runConfig: BenchmarkRunConfig,
    scenarios: Vector[ScenarioResult],
    failures: Vector[BenchmarkFailure] = Vector.empty
  ): PerformanceBenchmarkReport =
    PerformanceBenchmarkReport(
      schemaVersion = CurrentSchemaVersion,
      generatedAt = Instant.now().toString,
      git = git,
      environment = environment,
      runConfig = runConfig,
      scenarios = scenarios,
      failures = failures,
      policy = AbsolutePerformancePolicy.evaluate(environment, scenarios)
    )
