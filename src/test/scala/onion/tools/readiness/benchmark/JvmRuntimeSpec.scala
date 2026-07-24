package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class JvmRuntimeSpec extends AnyFunSpec with Matchers:
  describe("JvmRuntime.current"):
    it("resolves an executable Java command and the loaded Onion classpath"):
      val runtime = JvmRuntime.current()

      Files.isRegularFile(runtime.javaExecutable) shouldBe true
      runtime.classPath should include ("scala")
      runtime.classPath.split(java.io.File.pathSeparator).exists { entry =>
        entry.contains("classes") || entry.endsWith(".jar")
      } shouldBe true

  describe("ProcessLauncher.System"):
    it("captures stdout, stderr, and exit code"):
      val runtime = JvmRuntime.current()
      val workingDirectory = Files.createTempDirectory("onion-process-probe")
      val outcome = ProcessLauncher.System.run(
        Vector(
          runtime.javaExecutable.toString,
          "-version"
        ),
        workingDirectory,
        Map.empty
      )

      outcome.exitCode shouldBe 0
      (outcome.stdout + outcome.stderr).toLowerCase should include ("version")
