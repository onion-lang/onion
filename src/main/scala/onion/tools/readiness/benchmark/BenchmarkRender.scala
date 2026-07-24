package onion.tools.readiness.benchmark

import onion.Json
import onion.tools.readiness.policy.{
  AbsolutePerformanceCheck,
  AbsolutePerformanceEvaluation,
  PolicyCheckStatus
}

import java.util.{ArrayList, LinkedHashMap, List as JList, Map as JMap}

object BenchmarkRender:
  def json(report: PerformanceBenchmarkReport): String =
    Json.stringifyPretty(reportObject(report))

  def text(report: PerformanceBenchmarkReport): String =
    val lines = Vector.newBuilder[String]
    lines += s"benchmark schema=${report.schemaVersion} commit=${report.git.commit}"
    lines +=
      s"  absolute-policy=${report.policy.overallStatus.wireName} reference-lane=${report.policy.referenceLane}"
    report.policy.checks
      .filter { check =>
        check.status == PolicyCheckStatus.Fail ||
        check.status == PolicyCheckStatus.Unknown
      }
      .foreach { check =>
        lines +=
          s"    ${check.scenarioId} ${check.status.wireName}: ${check.message}"
      }
    report.failures.foreach { failure =>
      lines += s"  FAILED [${failure.category.wireName}]: ${failure.message}"
    }
    report.scenarios.foreach { scenario =>
      scenario.summary match
        case Some(summary) =>
          lines += f"  ${scenario.metadata.id}%-40s median=${millis(summary.medianNanos)}%.2fms p95=${millis(summary.p95Nanos)}%.2fms ${scenario.measurements.size}%d measured"
        case None =>
          val message = scenario.failure.map(_.message).getOrElse("missing summary")
          lines += s"  ${scenario.metadata.id} FAILED: $message"
    }
    lines.result().mkString(System.lineSeparator())

  private def reportObject(report: PerformanceBenchmarkReport): JMap[String, Object] =
    obj(
      "schemaVersion" -> Int.box(report.schemaVersion),
      "generatedAt" -> report.generatedAt,
      "commit" -> report.git.commit,
      "dirty" -> Boolean.box(report.git.dirty),
      "environment" -> obj(
        "javaVendor" -> report.environment.javaVendor,
        "javaVersion" -> report.environment.javaVersion,
        "osName" -> report.environment.osName,
        "osArch" -> report.environment.osArch,
        "osReleaseId" -> report.environment.osReleaseId,
        "osReleaseVersion" -> report.environment.osReleaseVersion,
        "processors" -> Int.box(report.environment.processors),
        "totalMemoryBytes" -> Long.box(report.environment.totalMemoryBytes),
        "maxHeapBytes" -> Long.box(report.environment.maxHeapBytes),
        "garbageCollectors" -> arr(
          report.environment.garbageCollectors.map(value => value: Object)
        ),
        "jvmArguments" -> arr(
          report.environment.jvmArguments.map(value => value: Object)
        )
      ),
      "runConfig" -> runConfigObject(report.runConfig),
      "scenarios" -> arr(report.scenarios.map(scenarioObject)),
      "failures" -> arr(report.failures.map(failureObject)),
      "policy" -> policyObject(report.policy)
    )

  private def policyObject(value: AbsolutePerformanceEvaluation): Object =
    obj(
      "referenceLane" -> Boolean.box(value.referenceLane),
      "applicabilityReason" -> value.applicabilityReason,
      "overallStatus" -> value.overallStatus.wireName,
      "checks" -> arr(value.checks.map(policyCheckObject))
    )

  private def policyCheckObject(value: AbsolutePerformanceCheck): Object =
    obj(
      "scenarioId" -> value.scenarioId,
      "status" -> value.status.wireName,
      "medianNanos" -> value.medianNanos.map(Long.box).orNull,
      "p95Nanos" -> value.p95Nanos.map(Long.box).orNull,
      "medianCeilingNanos" -> Long.box(value.medianCeilingNanos),
      "p95CeilingNanos" -> Long.box(value.p95CeilingNanos),
      "message" -> value.message
    )

  private def scenarioObject(result: ScenarioResult): Object =
    obj(
      "id" -> result.metadata.id,
      "kind" -> result.metadata.kind.wireName,
      "workload" -> result.metadata.workload,
      "workloadHash" -> result.metadata.workloadHash,
      "runConfig" -> runConfigObject(result.runConfig),
      "warmups" -> arr(result.warmups.map(observationObject)),
      "measurements" -> arr(result.measurements.map(observationObject)),
      "summary" -> result.summary.map(summaryObject).orNull,
      "failure" -> result.failure.map(failureObject).orNull
    )

  private def runConfigObject(value: BenchmarkRunConfig): Object =
    obj(
      "warmupIterations" -> Int.box(value.warmupIterations),
      "measuredIterations" -> Int.box(value.measuredIterations),
      "timeoutMillis" -> Long.box(value.timeoutMillis)
    )

  private def observationObject(value: IterationObservation): Object =
    obj(
      "index" -> Int.box(value.index),
      "kind" -> value.kind.wireName,
      "elapsedNanos" -> Long.box(value.elapsedNanos),
      "exitCode" -> Int.box(value.exitCode),
      "sourceMetrics" -> obj(
        "sourceCount" -> Int.box(value.sourceMetrics.sourceCount),
        "lineCount" -> Int.box(value.sourceMetrics.lineCount),
        "byteCount" -> Long.box(value.sourceMetrics.byteCount),
        "generatedClasses" -> Int.box(value.sourceMetrics.generatedClasses)
      ),
      "phases" -> arr(value.phases.map { phase =>
        obj(
          "name" -> phase.name,
          "elapsedNanos" -> Long.box(phase.elapsedNanos),
          "inputCount" -> Int.box(phase.inputCount),
          "outputCount" -> Int.box(phase.outputCount)
        )
      })
    )

  private def summaryObject(value: BenchmarkSummary): Object =
    obj(
      "medianNanos" -> Long.box(value.medianNanos),
      "p95Nanos" -> Long.box(value.p95Nanos),
      "minNanos" -> Long.box(value.minNanos),
      "maxNanos" -> Long.box(value.maxNanos)
    )

  private def failureObject(value: BenchmarkFailure): Object =
    obj(
      "category" -> value.category.wireName,
      "message" -> value.message,
      "iteration" -> value.iteration.map(Int.box).orNull
    )

  private def obj(entries: (String, Object)*): JMap[String, Object] =
    val result = new LinkedHashMap[String, Object]()
    entries.foreach { case (key, value) => result.put(key, value) }
    result

  private def arr(values: Seq[Object]): JList[Object] =
    val result = new ArrayList[Object]()
    values.foreach(result.add)
    result

  private def millis(nanos: Long): Double =
    nanos.toDouble / 1000000.0
