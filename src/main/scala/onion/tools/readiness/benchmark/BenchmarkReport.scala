package onion.tools.readiness.benchmark

import java.time.Instant

final case class PerformanceBenchmarkReport(
  schemaVersion: Int,
  generatedAt: String,
  git: GitMetadata,
  environment: EnvironmentMetadata,
  runConfig: BenchmarkRunConfig,
  scenarios: Vector[ScenarioResult],
  failures: Vector[BenchmarkFailure]
):
  def succeeded: Boolean = failures.isEmpty && scenarios.forall(_.succeeded)

object PerformanceBenchmarkReport:
  val CurrentSchemaVersion = 1

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
      failures = failures
    )
