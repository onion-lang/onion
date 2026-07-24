package onion.tools.readiness.benchmark

import java.nio.charset.StandardCharsets

final class PersistentReplScenario(
  factory: ReplClientFactory,
  workloadHash: String
) extends BenchmarkScenario:
  override val metadata =
    ScenarioMetadata(
      "persistent-session:repl:growing-state",
      ScenarioKind.PersistentSession,
      "generated growing REPL session",
      workloadHash
    )

  override def open(): BenchmarkSession =
    val client = factory.open()
    client.submit("val baseline = 40")
    new BenchmarkSession:
      private var submission = 0
      private var byteCount =
        "val baseline = 40".getBytes(StandardCharsets.UTF_8).length.toLong

      override def runIteration(index: Int): IterationPayload =
        submission += 1
        val code = s"baseline + $submission"
        byteCount += code.getBytes(StandardCharsets.UTF_8).length
        val output = client.submit(code)
        val expected = 40 + submission
        if !output.contains(s"= $expected") then
          throw BenchmarkScenarioException(
            s"REPL returned an unexpected result for '$code': ${output.trim}"
          )
        IterationPayload(
          Vector.empty,
          SourceMetrics(
            sourceCount = 1,
            lineCount = submission + 1,
            byteCount = byteCount,
            generatedClasses = 1
          ),
          exitCode = 0
        )

      override def close(): Unit = client.close()
