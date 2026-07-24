package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class PersistentReplScenarioSpec extends AnyFunSpec with Matchers:
  describe("PersistentReplScenario"):
    it("performs setup once and submits growing-state expressions"):
      val submitted = Vector.newBuilder[String]
      var closes = 0
      val factory = new ReplClientFactory:
        override def open(): ReplClient = new ReplClient:
          override def submit(code: String): String =
            submitted += code
            if code.startsWith("val baseline") then ""
            else s"res: Int = ${40 + code.split(" ").last.toInt}"
          override def close(): Unit = closes += 1
      val scenario = new PersistentReplScenario(factory, "hash")
      val session = scenario.open()

      val first = session.runIteration(0)
      val second = session.runIteration(1)
      session.close()

      submitted.result() shouldBe Vector(
        "val baseline = 40",
        "baseline + 1",
        "baseline + 2"
      )
      first.exitCode shouldBe 0
      second.sourceMetrics.lineCount shouldBe 3
      closes shouldBe 1
      scenario.metadata.kind shouldBe ScenarioKind.PersistentSession
