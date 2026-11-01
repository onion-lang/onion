package onion.compiler.tools

import onion.compiler.{CompilerConfig, ContractMode}
import onion.tools.CompilerOptions
import onion.tools.option.{CommandLineParam, NoValuedParam, ValuedParam}
import org.scalatest.funspec.AnyFunSpec

/**
 * `--contracts <off|runtime|verify>` (RFC #2557, design section 6).
 *
 * Contract clauses are a preview feature. `off` is the default and a clause is then an
 * error, so a contract is never silently ignored.
 */
class ContractsFlagSpec extends AnyFunSpec {

  private def mode(value: String): Option[ContractMode] =
    CompilerOptions.parseContractMode(Some(ValuedParam(value)))

  private def captureErr(body: => Any): String = {
    val buf = new java.io.ByteArrayOutputStream()
    val saved = System.err
    try { System.setErr(new java.io.PrintStream(buf, true, "UTF-8")); body } finally System.setErr(saved)
    new String(buf.toByteArray, "UTF-8")
  }

  describe("parseContractMode") {
    it("defaults to off") {
      assert(CompilerOptions.parseContractMode(None) == Some(ContractMode.Off))
    }

    it("accepts the modes that do something, in any case") {
      assert(mode("off") == Some(ContractMode.Off))
      assert(mode("check") == Some(ContractMode.Check))
      assert(mode("CHECK") == Some(ContractMode.Check))
    }

    it("refuses runtime and verify until they check something") {
      assert(mode("runtime").isEmpty)
      assert(mode("verify").isEmpty)
    }

    it("says which value to use instead, and does not list the reserved ones as accepted") {
      val err = captureErr(mode("verify"))
      assert(err.contains("--contracts"), err)
      assert(err.contains("check"), err)
      assert(err.contains("verify"), err)
      val bad = captureErr(mode("always"))
      assert(bad.contains("off") && bad.contains("check"), bad)
      assert(!bad.contains("runtime"), bad)
    }

    it("rejects an unknown value and a missing value") {
      assert(mode("always").isEmpty)
      assert(CompilerOptions.parseContractMode(Some(NoValuedParam)).isEmpty)
    }
  }

  describe("configFrom") {
    it("carries the mode into the compiler config") {
      val opts: Map[String, CommandLineParam] = Map(CompilerOptions.CONTRACTS -> ValuedParam("check"))
      val config = CompilerOptions.configFrom(opts, outputDirectory = "", verbose = false)
      assert(config.map(_.contracts) == Some(ContractMode.Check))
    }

    it("leaves contracts off when the flag is absent") {
      val config = CompilerOptions.configFrom(Map.empty, outputDirectory = "", verbose = false)
      assert(config.map(_.contracts) == Some(ContractMode.Off))
    }

    it("treats the flag as one that takes a value") {
      assert(CompilerOptions.VALUE_OPTIONS.contains(CompilerOptions.CONTRACTS))
    }
  }

  describe("CompilerConfig") {
    it("defaults to contracts off, so existing callers are unchanged") {
      val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10)
      assert(config.contracts == ContractMode.Off)
    }
  }
}
