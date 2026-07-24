package onion.tools.readiness.benchmark

import onion.compiler.{
  CompilerConfig,
  FileInputSource,
  OnionCompiler,
  WarningLevel
}

final class FreshCompileScenario(
  workload: CompileWorkload,
  compilerConfig: CompilerConfig
) extends BenchmarkScenario:
  override val metadata: ScenarioMetadata =
    ScenarioMetadata(
      id = s"steady-fresh:onionc:${workload.id}",
      kind = ScenarioKind.SteadyFresh,
      workload = workload.label,
      workloadHash = workload.workloadHash
    )

  override def open(): BenchmarkSession = new BenchmarkSession:
    override def runIteration(index: Int): IterationPayload =
      val sources = workload.paths.map(path => new FileInputSource(path.toString))
      val result = new OnionCompiler(compilerConfig).compileDetailed(sources)
      if result.hasErrors then
        val messages = result.allErrors.map(_.message).mkString("; ")
        throw BenchmarkScenarioException(
          s"compilation failed for ${workload.label}: $messages"
        )
      IterationPayload(
        phases = result.timings.map { phase =>
          PhaseObservation(
            name = phase.name,
            elapsedNanos = phase.elapsedNanos,
            inputCount = phase.inputCount,
            outputCount = phase.outputCount
          )
        }.toVector,
        sourceMetrics = SourceMetrics(
          sourceCount = workload.sourceCount,
          lineCount = workload.lineCount,
          byteCount = workload.byteCount,
          generatedClasses = result.classes.size
        ),
        exitCode = 0
      )

object CompileScenarioCatalog:
  def default(repoRoot: java.nio.file.Path): Vector[BenchmarkScenario] =
    val config = CompilerConfig(
      classPath = Seq("."),
      superClass = "",
      encoding = "UTF-8",
      outputDirectory = "",
      maxErrorReports = 20,
      warningLevel = WarningLevel.Off
    )
    Vector(
      CompileWorkload.fromFiles(
        repoRoot,
        "hello",
        "run/Hello.on",
        Vector("run/Hello.on")
      ),
      CompileWorkload.fromFiles(
        repoRoot,
        "todo-manager",
        "run/TodoManager.on",
        Vector("run/TodoManager.on")
      ),
      CompileWorkload.fromFiles(
        repoRoot,
        "stats-app",
        "run/StatsApp.on",
        Vector("run/StatsApp.on")
      )
    ).map(workload => new FreshCompileScenario(workload, config))
