package onion.tools.readiness.benchmark

import java.nio.file.Files
import scala.jdk.CollectionConverters.*

final class ProcessColdScenario(
  workload: CompileWorkload,
  runtime: JvmRuntime,
  launcher: ProcessLauncher,
  expectedStdout: String
) extends BenchmarkScenario:
  require(
    workload.sourceCount == 1,
    "process-cold script scenario requires exactly one source"
  )

  override val metadata: ScenarioMetadata =
    ScenarioMetadata(
      id = s"process-cold:onion:${workload.id}",
      kind = ScenarioKind.ProcessCold,
      workload = workload.label,
      workloadHash = workload.workloadHash
    )

  override def defaultWarmupIterations: Int = 3

  override def open(): BenchmarkSession =
    val workingDirectory = Files.createTempDirectory("onion-process-cold")
    new BenchmarkSession:
      override def runIteration(index: Int): IterationPayload =
        val outcome = launcher.run(
          Vector(
            runtime.javaExecutable.toString,
            "-cp",
            runtime.classPath,
            "onion.tools.ScriptRunner",
            workload.paths.head.toString
          ),
          workingDirectory,
          Map("TERM" -> "dumb")
        )
        if outcome.exitCode != 0 then
          throw BenchmarkScenarioException(
            s"${workload.label} exited with exit code ${outcome.exitCode}; " +
              s"stdout: ${bounded(outcome.stdout)}; " +
              s"stderr: ${bounded(outcome.stderr)}"
          )
        if
          normalize(outcome.stdout) != normalize(expectedStdout)
        then
          throw BenchmarkScenarioException(
            s"unexpected stdout for ${workload.label}: ${outcome.stdout.trim}"
          )
        IterationPayload(
          phases = Vector.empty,
          sourceMetrics = SourceMetrics(
            workload.sourceCount,
            workload.lineCount,
            workload.byteCount,
            generatedClasses = 0
          ),
          exitCode = outcome.exitCode
        )

      override def close(): Unit =
        if Files.exists(workingDirectory) then
          val entries = Files.walk(workingDirectory)
          try
            entries.iterator().asScala.toVector.reverse.foreach { entry =>
              Files.deleteIfExists(entry)
            }
          finally entries.close()

  private def normalize(value: String): String =
    value.replace("\r\n", "\n")

  private def bounded(value: String): String =
    val limit = 2048
    val normalized = normalize(value).trim
    if normalized.length <= limit then normalized
    else normalized.take(limit) + "…"
