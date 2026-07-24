package onion.tools.readiness.policy

import onion.tools.readiness.benchmark.*

enum PolicyCheckStatus(val wireName: String):
  case Pass extends PolicyCheckStatus("pass")
  case Fail extends PolicyCheckStatus("fail")
  case NotApplicable extends PolicyCheckStatus("not-applicable")
  case Unknown extends PolicyCheckStatus("unknown")

enum PolicyOverallStatus(val wireName: String):
  case Pass extends PolicyOverallStatus("pass")
  case Fail extends PolicyOverallStatus("fail")
  case Informational extends PolicyOverallStatus("informational")

final case class PerformanceBudget(
  scenarioId: String,
  medianCeilingNanos: Long,
  p95CeilingNanos: Long
)

final case class AbsolutePerformanceCheck(
  scenarioId: String,
  status: PolicyCheckStatus,
  medianNanos: Option[Long],
  p95Nanos: Option[Long],
  medianCeilingNanos: Long,
  p95CeilingNanos: Long,
  message: String
)

final case class AbsolutePerformanceEvaluation(
  referenceLane: Boolean,
  applicabilityReason: String,
  checks: Vector[AbsolutePerformanceCheck],
  overallStatus: PolicyOverallStatus
)

object AbsolutePerformancePolicy:
  private val GiB = 1024L * 1024L * 1024L

  val budgets: Vector[PerformanceBudget] = Vector(
    PerformanceBudget(
      "process-cold:onion:hello",
      1500000000L,
      2500000000L
    ),
    PerformanceBudget(
      "steady-fresh:onionc:hello",
      150000000L,
      300000000L
    ),
    PerformanceBudget(
      "steady-fresh:onionc:stats-app",
      750000000L,
      1200000000L
    ),
    PerformanceBudget(
      "persistent-session:repl:growing-state",
      100000000L,
      250000000L
    ),
    PerformanceBudget(
      "multi-file:onionc:automation-project",
      2000000000L,
      3000000000L
    )
  )

  def isReferenceLane(environment: EnvironmentMetadata): Boolean =
    referenceMismatches(environment).isEmpty

  def evaluate(
    environment: EnvironmentMetadata,
    scenarios: Vector[ScenarioResult]
  ): AbsolutePerformanceEvaluation =
    val mismatches = referenceMismatches(environment)
    val referenceLane = mismatches.isEmpty
    val byId = scenarios.map(result => result.metadata.id -> result).toMap
    val checks = budgets.map { budget =>
      val scenario = byId.get(budget.scenarioId)
      val summary = scenario.flatMap(_.summary)
      if !referenceLane then
        check(
          budget,
          PolicyCheckStatus.NotApplicable,
          summary,
          "absolute ceilings require the reference environment"
        )
      else
        scenario match
          case None =>
            check(
              budget,
              PolicyCheckStatus.Unknown,
              None,
              "required scenario evidence is missing"
            )
          case Some(result) if result.failure.nonEmpty =>
            check(
              budget,
              PolicyCheckStatus.Unknown,
              result.summary,
              s"scenario failed: ${result.failure.get.message}"
            )
          case Some(result) =>
            result.summary match
              case None =>
                check(
                  budget,
                  PolicyCheckStatus.Unknown,
                  None,
                  "required scenario summary is missing"
                )
              case Some(value) =>
                val breaches = Vector(
                  Option.when(value.medianNanos > budget.medianCeilingNanos)(
                    "median"
                  ),
                  Option.when(value.p95Nanos > budget.p95CeilingNanos)("p95")
                ).flatten
                if breaches.isEmpty then
                  check(
                    budget,
                    PolicyCheckStatus.Pass,
                    Some(value),
                    "within absolute ceiling"
                  )
                else
                  check(
                    budget,
                    PolicyCheckStatus.Fail,
                    Some(value),
                    s"${breaches.mkString(" and ")} exceeded absolute ceiling"
                  )
    }
    val overallStatus =
      if !referenceLane then PolicyOverallStatus.Informational
      else if checks.exists { value =>
        value.status == PolicyCheckStatus.Fail ||
        value.status == PolicyCheckStatus.Unknown
      }
      then PolicyOverallStatus.Fail
      else PolicyOverallStatus.Pass
    AbsolutePerformanceEvaluation(
      referenceLane = referenceLane,
      applicabilityReason =
        if referenceLane then "reference environment matched"
        else s"reference environment mismatch: ${mismatches.mkString(", ")}",
      checks = checks,
      overallStatus = overallStatus
    )

  private def check(
    budget: PerformanceBudget,
    status: PolicyCheckStatus,
    summary: Option[BenchmarkSummary],
    message: String
  ): AbsolutePerformanceCheck =
    AbsolutePerformanceCheck(
      scenarioId = budget.scenarioId,
      status = status,
      medianNanos = summary.map(_.medianNanos),
      p95Nanos = summary.map(_.p95Nanos),
      medianCeilingNanos = budget.medianCeilingNanos,
      p95CeilingNanos = budget.p95CeilingNanos,
      message = message
    )

  private def referenceMismatches(
    environment: EnvironmentMetadata
  ): Vector[String] =
    Vector(
      Option.unless(environment.osName == "Linux")("operating system"),
      Option.unless(environment.osReleaseId == "ubuntu")("distribution"),
      Option.unless(environment.osReleaseVersion == "24.04")(
        "distribution version"
      ),
      Option.unless(Set("amd64", "x86_64").contains(environment.osArch))(
        "architecture"
      ),
      Option.unless(environment.javaVendor == "Eclipse Adoptium")(
        "Java vendor"
      ),
      Option.unless(javaMajor(environment.javaVersion).contains(21))(
        "Java major version"
      ),
      Option.unless(environment.processors == 2)("assigned processors"),
      Option.unless(environment.totalMemoryBytes == 4L * GiB)(
        "assigned memory"
      ),
      Option.unless(environment.maxHeapBytes == 2L * GiB)("maximum heap"),
      Option.unless(
        environment.garbageCollectors.exists(_.startsWith("G1"))
      )("garbage collector")
    ).flatten

  private def javaMajor(version: String): Option[Int] =
    val parts = version.split("[._-]").toVector
    parts.headOption.flatMap(_.toIntOption) match
      case Some(1) => parts.lift(1).flatMap(_.toIntOption)
      case value => value
