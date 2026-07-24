package onion.tools.readiness.benchmark

import java.io.{BufferedWriter, InputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

trait ReplClient extends AutoCloseable:
  def submit(code: String): String

trait ReplClientFactory:
  def open(): ReplClient

final class ProcessReplClient private (
  process: Process,
  input: BufferedWriter,
  stdout: ReplOutputPump,
  timeoutMillis: Long
) extends ReplClient:
  override def submit(code: String): String =
    try
      input.write(code)
      input.newLine()
      input.flush()
      stdout.readUntil("onion> ", timeoutMillis)
    catch
      case interrupted: InterruptedException =>
        process.destroyForcibly()
        Thread.currentThread().interrupt()
        throw interrupted

  override def close(): Unit =
    if process.isAlive then
      try
        input.write(":quit")
        input.newLine()
        input.flush()
        if !process.waitFor(1L, TimeUnit.SECONDS) then
          process.destroyForcibly()
      catch
        case _: Exception => process.destroyForcibly()
    input.close()

object ProcessReplClient:
  def start(
    runtime: JvmRuntime,
    workingDirectory: Path,
    timeoutMillis: Long
  ): ProcessReplClient =
    val builder =
      new ProcessBuilder(
        runtime.javaExecutable.toString,
        "-cp",
        runtime.classPath,
        "onion.tools.Repl"
      )
        .directory(workingDirectory.toFile)
    builder.environment().put("TERM", "dumb")
    val process = builder.start()
    val stderr = new StreamDrainer(process.getErrorStream, "repl-stderr")
    stderr.start()
    val stdout = new ReplOutputPump(process.getInputStream, "repl-stdout")
    stdout.start()
    val writer =
      new BufferedWriter(
        new OutputStreamWriter(process.getOutputStream, StandardCharsets.UTF_8)
      )
    val client = new ProcessReplClient(process, writer, stdout, timeoutMillis)
    try
      stdout.readUntil("onion> ", timeoutMillis)
      client
    catch
      case error: Throwable =>
        client.close()
        throw error

private final class ReplOutputPump(stream: InputStream, name: String):
  private val End = -1
  private val queue = new LinkedBlockingQueue[Int]()
  private val failure = new AtomicReference[Throwable]()
  private val thread = new Thread(
    new Runnable:
      override def run(): Unit =
        try
          var next = stream.read()
          while next >= 0 do
            queue.put(next)
            next = stream.read()
        catch case error: Throwable => failure.set(error)
        finally
          queue.offer(End)
          stream.close()
    ,
    s"onion-$name"
  )
  thread.setDaemon(true)

  def start(): Unit = thread.start()

  def readUntil(marker: String, timeoutMillis: Long): String =
    val expected = marker.getBytes(StandardCharsets.UTF_8)
    val bytes = scala.collection.mutable.ArrayBuffer.empty[Byte]
    val deadline =
      System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    while !endsWith(bytes, expected) do
      val remaining = deadline - System.nanoTime()
      if remaining <= 0L then
        throw BenchmarkScenarioException(
          s"REPL prompt timed out after $timeoutMillis ms"
        )
      val next: Integer = queue.poll(remaining, TimeUnit.NANOSECONDS)
      if next == null then
        throw BenchmarkScenarioException(
          s"REPL prompt timed out after $timeoutMillis ms"
        )
      if next.intValue() == End then
        val cause = failure.get()
        val detail =
          if cause == null then "end of stream"
          else Option(cause.getMessage).getOrElse(cause.getClass.getSimpleName)
        throw BenchmarkScenarioException(s"REPL exited before prompt: $detail")
      bytes += next.byteValue()
    new String(bytes.toArray, StandardCharsets.UTF_8)

  private def endsWith(
    bytes: scala.collection.mutable.ArrayBuffer[Byte],
    expected: Array[Byte]
  ): Boolean =
    bytes.length >= expected.length &&
      expected.indices.forall { index =>
        bytes(bytes.length - expected.length + index) == expected(index)
      }

private final class StreamDrainer(stream: InputStream, name: String):
  private val thread = new Thread(
    new Runnable:
      override def run(): Unit =
        try
          while stream.read() >= 0 do ()
        finally stream.close()
    ,
    s"onion-$name"
  )
  thread.setDaemon(true)

  def start(): Unit = thread.start()
