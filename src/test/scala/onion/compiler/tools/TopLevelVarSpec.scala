package onion.compiler.tools

import java.io.StringReader

import onion.compiler.{CompilerConfig, OnionClassLoader, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * A mutable top-level `var` must have SINGLE storage. Before the fix, the #165
 * local-first promotion gave a `var` two backing stores -- later top-level statements
 * wrote the local, top-level `def`s wrote the mirrored field -- and they silently
 * diverged (the classic miscompilation: `bump()` twice, then read, saw 0 not 2).
 * A `var` (no M_FINAL) is now promoted FIELD-ONLY so both defs and later top-level
 * statements resolve the name to the same field. `val` keeps local-first (see
 * TopLevelValSpec) for smart-cast.
 *
 * These are script-style programs (top-level statements + defs, no explicit `main`);
 * the shared top-level `var` is promoted to a public static field of the script's
 * synthetic class. We run the synthetic `main` (executing the top-level statements and
 * the def calls) and then read that field reflectively -- a value-level check that is
 * independent of stdout, so it stays deterministic under ScalaTest's parallel suites.
 */
class TopLevelVarSpec extends AnyFunSpec {
  private val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  /** Compile+run the script-style source, then read the promoted static field `name`. */
  private def runAndReadField(source: String, name: String): Any = {
    val result = new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "TopLevelVar.on")))
    assert(!result.hasErrors, s"failed to compile: ${result.allErrors.map(_.message).mkString("; ")}")
    val loader = new OnionClassLoader(getClass.getClassLoader, Seq("."), result.classes)
    // The synthetic script class is the one carrying `main(String[])`.
    val scriptClass = result.classes.iterator
      .map(c => Class.forName(c.className, true, loader))
      .find(cl =>
        try { cl.getMethod("main", classOf[Array[String]]); true }
        catch { case _: NoSuchMethodException => false }
      )
      .getOrElse(fail("no synthetic main class found"))
    scriptClass.getMethod("main", classOf[Array[String]]).invoke(null, Array.empty[String])
    val field = scriptClass.getField(name)
    field.get(null)
  }

  describe("top-level var single-storage") {
    it("sees a def's mutation of a top-level var from a later top-level read") {
      // var calls; def bump() increments it; two top-level bump() calls -> field is 2.
      assert(runAndReadField(
        """
          |var calls: Int = 0
          |def bump(): void { calls = calls + 1 }
          |bump()
          |bump()
          |""".stripMargin,
        "calls"
      ) == 2)
    }

    it("sees a top-level statement's write of a var from inside a def") {
      // top-level `c = 5`, then def get() reads the same field.
      assert(runAndReadField(
        """
          |var c: Int = 0
          |def get(): Int = c
          |c = 5
          |""".stripMargin,
        "c"
      ) == 5)
    }

    it("still handles a top-level-only var (no def) correctly") {
      assert(runAndReadField(
        """
          |var c: Int = 0
          |c = c + 1
          |c = c + 1
          |""".stripMargin,
        "c"
      ) == 2)
    }

    it("keeps def writes, def reads and top-level reads all agreeing") {
      // def set(10) then top-level `c = c + 5` -> single store ends at 15.
      assert(runAndReadField(
        """
          |var c: Int = 0
          |def set(n: Int): void { c = n }
          |def get(): Int = c
          |set(10)
          |c = c + 5
          |""".stripMargin,
        "c"
      ) == 15)
    }
    it("supports ++ on a top-level var (post-increment through the field)") {
      assert(runAndReadField(
        """
          |var n: Int = 0
          |def bump(): void { n++ }
          |bump()
          |bump()
          |""".stripMargin,
        "n"
      ) == 2)
    }

  }
}
