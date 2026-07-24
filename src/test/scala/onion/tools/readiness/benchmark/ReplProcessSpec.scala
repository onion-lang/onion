package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.{
  ByteArrayInputStream,
  ByteArrayOutputStream,
  InputStream,
  OutputStream
}
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class ReplProcessSpec extends AnyFunSpec with Matchers:
  private final class StubbornProcess extends Process:
    private val input = new ByteArrayInputStream(Array.emptyByteArray)
    private val error = new ByteArrayInputStream(Array.emptyByteArray)
    private val output = new ByteArrayOutputStream()
    var waits = 0
    var forciblyDestroyed = false
    private var alive = true

    override def getOutputStream(): OutputStream = output
    override def getInputStream(): InputStream = input
    override def getErrorStream(): InputStream = error
    override def waitFor(): Int =
      alive = false
      0
    override def waitFor(timeout: Long, unit: TimeUnit): Boolean =
      waits += 1
      if forciblyDestroyed then alive = false
      !alive
    override def exitValue(): Int =
      if alive then throw new IllegalThreadStateException()
      0
    override def destroy(): Unit = ()
    override def destroyForcibly(): Process =
      forciblyDestroyed = true
      this
    override def isAlive(): Boolean = alive

  describe("ProcessReplClient"):
    it("retains state across submissions to the real Onion REPL"):
      val workingDirectory = Files.createTempDirectory("onion-repl-process")
      val client = ProcessReplClient.start(
        JvmRuntime.current(),
        workingDirectory,
        timeoutMillis = 30000L
      )
      try
        client.submit("val baseline = 40")
        val output = client.submit("baseline + 2")
        output should include ("res0: Int = 42")
      finally client.close()

    it("waits for a forcibly destroyed REPL before returning from close"):
      val process = new StubbornProcess()
      val client = ProcessReplClient.attach(process, timeoutMillis = 1000L)

      client.close()

      process.forciblyDestroyed shouldBe true
      process.waits shouldBe 2
      process.isAlive shouldBe false
