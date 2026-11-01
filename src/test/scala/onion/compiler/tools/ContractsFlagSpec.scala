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

  describe("parseContractMode") {
    it("defaults to off") {
      assert(CompilerOptions.parseContractMode(None) == Some(ContractMode.Off))
    }

    it("accepts the three modes, in any case") {
      assert(mode("off") == Some(ContractMode.Off))
      assert(mode("runtime") == Some(ContractMode.Runtime))
      assert(mode("verify") == Some(ContractMode.Verify))
      assert(mode("RUNTIME") == Some(ContractMode.Runtime))
    }

    it("rejects an unknown value and a missing value") {
      assert(mode("always").isEmpty)
      assert(CompilerOptions.parseContractMode(Some(NoValuedParam)).isEmpty)
    }
  }

  describe("configFrom") {
    it("carries the mode into the compiler config") {
      val opts: Map[String, CommandLineParam] = Map(CompilerOptions.CONTRACTS -> ValuedParam("runtime"))
      val config = CompilerOptions.configFrom(opts, outputDirectory = "", verbose = false)
      assert(config.map(_.contracts) == Some(ContractMode.Runtime))
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
