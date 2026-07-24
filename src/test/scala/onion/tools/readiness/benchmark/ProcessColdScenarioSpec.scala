package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors

class ProcessColdScenarioSpec extends AnyFunSpec with Matchers:
  describe("ProcessColdScenario"):
    it("launches a new script-runner process for every iteration"):
      val root = Files.createTempDirectory("onion-process-cold")
      Files.writeString(root.resolve("Hello.on"), """IO::println("Hello")""")
      val workload =
        CompileWorkload.fromFiles(root, "hello", "Hello.on", Vector("Hello.on"))
      var launches = 0
      val launcher = new ProcessLauncher:
        override def run(
          command: Vector[String],
          workingDirectory: java.nio.file.Path,
          environment: Map[String, String]
        ): ProcessOutcome =
          launches += 1
          ProcessOutcome(0, "Hello\n", "")
      val runtime = JvmRuntime(root.resolve("java"), "runtime-classpath")
      val scenario =
        new ProcessColdScenario(workload, runtime, launcher, "Hello\n")
      val executor = Executors.newSingleThreadExecutor()
      val engine = new BenchmarkEngine(
        BenchmarkRunConfig(1, 2, 1000L),
        NanoClock.System,
        executor
      )

      val result = try engine.run(scenario) finally engine.close()

      launches shouldBe 3
      result.failure shouldBe None
      result.metadata.id shouldBe "process-cold:onion:hello"
      scenario.defaultWarmupIterations shouldBe 3

    it("fails when a successful process prints the wrong result"):
      val root = Files.createTempDirectory("onion-process-output")
      Files.writeString(root.resolve("Hello.on"), """IO::println("Hello")""")
      val workload =
        CompileWorkload.fromFiles(root, "hello", "Hello.on", Vector("Hello.on"))
      val launcher = new ProcessLauncher:
        override def run(
          command: Vector[String],
          workingDirectory: java.nio.file.Path,
          environment: Map[String, String]
        ): ProcessOutcome = ProcessOutcome(0, "wrong\n", "")
      val scenario = new ProcessColdScenario(
        workload,
        JvmRuntime(root.resolve("java"), "cp"),
        launcher,
        "Hello\n"
      )
      val session = scenario.open()

      val thrown = intercept[BenchmarkScenarioException] {
        session.runIteration(0)
      }
      thrown.getMessage should include ("unexpected stdout")
      session.close()

    it("runs Hello through a real fresh child JVM"):
      val repoRoot = Paths.get(".").toAbsolutePath.normalize()
      val workload = CompileWorkload.fromFiles(
        repoRoot,
        "hello",
        "run/Hello.on",
        Vector("run/Hello.on")
      )
      val scenario = new ProcessColdScenario(
        workload,
        JvmRuntime.current(),
        ProcessLauncher.System,
        "Hello\n"
      )
      val executor = Executors.newSingleThreadExecutor()
      val engine = new BenchmarkEngine(
        BenchmarkRunConfig(0, 1, 30000L),
        NanoClock.System,
        executor
      )

      val result = try engine.run(scenario) finally engine.close()

      result.failure shouldBe None
      result.measurements should have size 1
