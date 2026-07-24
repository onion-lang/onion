package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class BenchmarkMetadataSpec extends AnyFunSpec with Matchers:
  describe("EnvironmentMetadata.parseOsRelease"):
    it("extracts and unquotes Ubuntu identity"):
      EnvironmentMetadata.parseOsRelease(
        Seq("""ID="ubuntu"""", """VERSION_ID="24.04"""")
      ) shouldBe ("ubuntu", "24.04")

    it("uses unknown values when release keys are absent"):
      EnvironmentMetadata.parseOsRelease(Seq("NAME=Linux")) shouldBe
        ("unknown", "unknown")

  describe("EnvironmentMetadata.capture"):
    it("captures positive processor, heap, and memory evidence"):
      val environment = EnvironmentMetadata.capture()

      environment.processors should be > 0
      environment.maxHeapBytes should be > 0L
      environment.totalMemoryBytes should be > 0L
