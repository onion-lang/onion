package onion.tools.readiness.benchmark

import onion.compiler.{CompilerConfig, WarningLevel}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.concurrent.Executors

class FreshCompileScenarioSpec extends AnyFunSpec with Matchers:
  private def config =
    CompilerConfig(
      classPath = Seq("."),
      superClass = "",
      encoding = "UTF-8",
      outputDirectory = "",
      maxErrorReports = 20,
      warningLevel = WarningLevel.Off
    )

  describe("CompileWorkload"):
    it("counts sources, lines, bytes, and hashes file contents deterministically"):
      val root = Files.createTempDirectory("onion-benchmark-workload")
      Files.writeString(root.resolve("One.on"), "val one = 1\n", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("Two.on"), "val two = 2", StandardCharsets.UTF_8)

      val first = CompileWorkload.fromFiles(
        root,
        "fixture",
        "fixture",
        Vector("One.on", "Two.on")
      )
      val second = CompileWorkload.fromFiles(
        root,
        "fixture",
        "fixture",
        Vector("One.on", "Two.on")
      )

      first.sourceCount shouldBe 2
      first.lineCount shouldBe 2
      first.byteCount should be > 0L
      first.workloadHash shouldBe second.workloadHash

  describe("FreshCompileScenario"):
    it("creates a fresh compiler result with phase and source metadata"):
      val root = Files.createTempDirectory("onion-benchmark-compile")
      Files.writeString(
        root.resolve("Hello.on"),
        """IO::println("hello")""",
        StandardCharsets.UTF_8
      )
      val workload =
        CompileWorkload.fromFiles(root, "hello", "hello", Vector("Hello.on"))
      val scenario = new FreshCompileScenario(workload, config)
      val executor = Executors.newSingleThreadExecutor()
      val engine = new BenchmarkEngine(
        BenchmarkRunConfig(0, 1, 30000L),
        NanoClock.System,
        executor
      )
      val result = try engine.run(scenario) finally engine.close()

      result.failure shouldBe None
      result.measurements should have size 1
      result.measurements.head.phases.map(_.name) should contain ("Parsing")
      result.measurements.head.phases.map(_.name) should contain ("BytecodeGeneration")
      result.measurements.head.sourceMetrics.generatedClasses should be > 0

  describe("CompileScenarioCatalog"):
    it("uses the actual practical small, medium, and large scripts"):
      val scenarios = CompileScenarioCatalog.default(Paths.get(".").toAbsolutePath.normalize())
      scenarios.map(_.metadata.id) shouldBe Vector(
        "steady-fresh:onionc:hello",
        "steady-fresh:onionc:todo-manager",
        "steady-fresh:onionc:stats-app"
      )
      scenarios.last.metadata.kind shouldBe ScenarioKind.SteadyFresh
      scenarios.last.metadata.workload shouldBe "run/StatsApp.on"
