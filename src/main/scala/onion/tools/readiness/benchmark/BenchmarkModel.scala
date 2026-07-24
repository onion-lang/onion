package onion.tools.readiness.benchmark

enum ScenarioKind(val wireName: String):
  case ProcessCold extends ScenarioKind("process-cold")
  case SteadyFresh extends ScenarioKind("steady-fresh")
  case PersistentSession extends ScenarioKind("persistent-session")
  case MultiFile extends ScenarioKind("multi-file")

enum ObservationKind(val wireName: String):
  case Warmup extends ObservationKind("warmup")
  case Measurement extends ObservationKind("measurement")

enum FailureCategory(val wireName: String):
  case InvalidMeasurement extends FailureCategory("invalid-measurement")
  case ScenarioFailure extends FailureCategory("scenario-failure")
  case UnstableEnvironment extends FailureCategory("unstable-environment")
  case BudgetFailure extends FailureCategory("budget-failure")

final case class SourceMetrics(
  sourceCount: Int,
  lineCount: Int,
  byteCount: Long,
  generatedClasses: Int
)

final case class PhaseObservation(
  name: String,
  elapsedNanos: Long,
  inputCount: Int,
  outputCount: Int
)

final case class IterationObservation(
  index: Int,
  kind: ObservationKind,
  elapsedNanos: Long,
  phases: Vector[PhaseObservation],
  sourceMetrics: SourceMetrics,
  exitCode: Int
)

final case class BenchmarkSummary(
  medianNanos: Long,
  p95Nanos: Long,
  minNanos: Long,
  maxNanos: Long
)

final case class BenchmarkFailure(
  category: FailureCategory,
  message: String,
  iteration: Option[Int] = None
)

final case class ScenarioMetadata(
  id: String,
  kind: ScenarioKind,
  workload: String,
  workloadHash: String
)

final case class ScenarioResult(
  metadata: ScenarioMetadata,
  runConfig: BenchmarkRunConfig,
  warmups: Vector[IterationObservation],
  measurements: Vector[IterationObservation],
  summary: Option[BenchmarkSummary],
  failure: Option[BenchmarkFailure]
):
  def succeeded: Boolean = failure.isEmpty
