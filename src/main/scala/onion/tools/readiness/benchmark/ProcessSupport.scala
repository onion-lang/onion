package onion.tools.readiness.benchmark

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.{Callable, Executors, Future, TimeUnit}
import scala.jdk.CollectionConverters.*

final case class ProcessOutcome(
  exitCode: Int,
  stdout: String,
  stderr: String
)

trait ProcessLauncher:
  def run(
    command: Vector[String],
    workingDirectory: Path,
    environment: Map[String, String]
  ): ProcessOutcome

object ProcessLauncher:
  object System extends ProcessLauncher:
    override def run(
      command: Vector[String],
      workingDirectory: Path,
      environment: Map[String, String]
    ): ProcessOutcome =
      val builder = new ProcessBuilder(command*)
        .directory(workingDirectory.toFile)
      builder.environment().putAll(environment.asJava)
      val process = builder.start()
      val readers = Executors.newFixedThreadPool(
        2,
        runnable =>
          val thread = new Thread(runnable, "onion-process-output")
          thread.setDaemon(true)
          thread
      )
      val stdout = readAsync(readers, process.getInputStream)
      val stderr = readAsync(readers, process.getErrorStream)
      try
        val exitCode = process.waitFor()
        ProcessOutcome(exitCode, stdout.get(), stderr.get())
      catch
        case interrupted: InterruptedException =>
          process.destroyForcibly()
          try
            if !process.waitFor(5L, TimeUnit.SECONDS) then
              interrupted.addSuppressed(
                new IllegalStateException(
                  "child process did not terminate after forced destruction"
                )
              )
          catch
            case cleanupInterrupted: InterruptedException =>
              interrupted.addSuppressed(cleanupInterrupted)
          stdout.cancel(true)
          stderr.cancel(true)
          Thread.currentThread().interrupt()
          throw interrupted
      finally
        readers.shutdownNow()

    private def readAsync(
      readers: java.util.concurrent.ExecutorService,
      stream: java.io.InputStream
    ): Future[String] =
      readers.submit(new Callable[String]:
        override def call(): String =
          try new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          finally stream.close()
      )
