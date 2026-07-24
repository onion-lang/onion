package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class ReplProcessSpec extends AnyFunSpec with Matchers:
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
