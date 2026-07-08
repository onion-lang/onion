package onion.compiler.tools

import onion.compiler.{CompileError, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/**
 * Regression spec for issue #316: a failed trailing-lambda BODY must NOT roll up
 * into a misleading "method not found" for the outer call.
 *
 *   val ys = xs.map { x => x.noSuchMethod() }
 *
 * The single real mistake (Int.noSuchMethod) must surface; the redundant
 * "List[Int].map() not found" must be suppressed. Crucially, the suppression
 * must NOT swallow a genuinely absent method: when the closure body is fine but
 * the method truly does not exist, method-not-found must still be reported.
 */
class ErrorAvalancheSpec extends AbstractShellSpec {
  // Compile-only, exposing the collected diagnostics so we can assert on error
  // CODES (locale-independent) rather than localized message text.
  private def diagnose(script: String, fileName: String): Vector[CompileError] = {
    val config = new CompilerConfig(Seq(), null, java.nio.charset.Charset.defaultCharset().name(), "", 100)
    val compiler = new OnionCompiler(config)
    compiler.compileDetailed(Seq(new StreamInputSource(() => new StringReader(script), fileName))).allErrors
  }

  private def codes(errors: Vector[CompileError]): Vector[String] =
    errors.flatMap(_.errorCode)

  describe("issue #316: trailing-closure body errors must not avalanche") {
    it("reports only the closure body error, not an outer method-not-found") {
      val script =
        """
          |val xs: List[Int] = [1, 2, 3]
          |val ys = xs.map { x => x.noSuchMethod() }
        """.stripMargin
      // (a) the program fails to compile
      assert(Shell.Failure(-1) == shell.run(script, "Avalanche.on", Array()))
      // (b) exactly one error survives, and it is the real body error (E0005),
      //     not doubled up with an outer "map not found".
      val errors = diagnose(script, "Avalanche.on")
      assert(codes(errors) == Vector("E0005"))
      // The single error must be about noSuchMethod, never about map.
      assert(errors.exists(_.message.contains("noSuchMethod")))
      assert(!errors.exists(_.message.contains("map")))
    }

    it("also suppresses the avalanche for filter") {
      val script =
        """
          |val xs: List[Int] = [1, 2, 3]
          |val ys = xs.filter { x => x.noSuchMethod() }
        """.stripMargin
      assert(Shell.Failure(-1) == shell.run(script, "AvalancheFilter.on", Array()))
      val errors = diagnose(script, "AvalancheFilter.on")
      assert(codes(errors) == Vector("E0005"))
      assert(!errors.exists(_.message.contains("filter")))
    }

    // --- Regression guards: genuine method-not-found MUST still error. ---

    it("still errors on a missing method with no closure") {
      val script =
        """
          |val xs: List[Int] = [1, 2, 3]
          |val ys = xs.nonExistentMethod()
        """.stripMargin
      assert(Shell.Failure(-1) == shell.run(script, "Guard1.on", Array()))
      assert(codes(diagnose(script, "Guard1.on")).contains("E0005"))
    }

    it("still errors on a valid closure followed by a real missing method") {
      val script =
        """
          |val xs: List[Int] = [1, 2, 3]
          |val ys = xs.map { x => x + 1 }.nonExistentMethod()
        """.stripMargin
      assert(Shell.Failure(-1) == shell.run(script, "Guard2.on", Array()))
      assert(codes(diagnose(script, "Guard2.on")).contains("E0005"))
    }

    it("still errors on a missing method WITH a well-typed closure (must not be swallowed)") {
      val script =
        """
          |val xs: List[Int] = [1, 2, 3]
          |val ys = xs.nonExistentMethod { x => x + 1 }
        """.stripMargin
      assert(Shell.Failure(-1) == shell.run(script, "Guard3.on", Array()))
      // The closure body is fine; the method is genuinely absent -> must report.
      val errors = diagnose(script, "Guard3.on")
      assert(codes(errors).contains("E0005"))
      assert(errors.exists(_.message.contains("nonExistentMethod")))
    }

    it("still errors on a wrong-argument-type call with no closure") {
      val script =
        """
          |val xs: List[Int] = [1, 2, 3]
          |val ys = xs.get("notAnInt")
        """.stripMargin
      assert(Shell.Failure(-1) == shell.run(script, "Guard4.on", Array()))
      assert(codes(diagnose(script, "Guard4.on")).contains("E0005"))
    }

    it("still compiles a valid trailing-closure pipeline") {
      val script =
        """
          |val xs: List[Int] = [1, 2, 3]
          |val ys = xs.map { x => x + 1 }
          |println(ys)
        """.stripMargin
      assert(shell.run(script, "Valid.on", Array()) != Shell.Failure(-1))
    }
  }
}
