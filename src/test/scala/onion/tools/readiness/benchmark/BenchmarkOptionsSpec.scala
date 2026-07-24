package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues

import java.nio.file.Paths

class BenchmarkOptionsSpec extends AnyFunSpec with Matchers with OptionValues:
  describe("BenchmarkOptions.parse"):
    it("uses readiness-safe defaults"):
      BenchmarkOptions.parse(Array.empty) shouldBe Right(
        BenchmarkOptions(
          runConfig = BenchmarkRunConfig(8, 25, 30000L),
          output = Paths.get("target/readiness/benchmark-v3.json"),
          stdoutFormat = BenchmarkOutputFormat.Text
        )
      )
      BenchmarkOptions.parse(Array.empty).toOption.value.warmupOverride shouldBe None

    it("preserves --iterations and --json compatibility"):
      val parsed =
        BenchmarkOptions.parse(Array("--iterations", "3", "--json")).toOption.value
      parsed.runConfig.measuredIterations shouldBe 3
      parsed.stdoutFormat shouldBe BenchmarkOutputFormat.Json

    it("accepts explicit warmup, timeout, and output values"):
      val parsed = BenchmarkOptions.parse(
        Array(
          "--warmups", "2",
          "--timeout-seconds", "5",
          "--output", "target/custom.json",
          "--format", "text"
        )
      ).toOption.value
      parsed.runConfig shouldBe BenchmarkRunConfig(2, 25, 5000L)
      parsed.output shouldBe Paths.get("target/custom.json")
      parsed.warmupOverride shouldBe Some(2)

    it("rejects unknown and invalid options"):
      BenchmarkOptions.parse(Array("--iterations", "0")).left.toOption.value should include ("positive")
      BenchmarkOptions.parse(Array("--wat")).left.toOption.value should include ("unknown")
