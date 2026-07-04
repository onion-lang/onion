package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Unit tests for the auto-CLI runtime parser `onion.Cli.parse`, exercised
 * directly (a top-level auto-CLI main does not surface its return value through
 * the Shell, and `--help` calls System.exit, so the parser is tested here).
 *
 * Spec string: "name" positional, "name=" value flag, "name?" boolean switch.
 * Covers the GNU `--name=value` form (#286); the space form and switches stay
 * working. `--help` (#287) is verified out-of-process.
 */
class CliParseSpec extends AnyFunSpec {
  describe("onion.Cli.parse") {
    it("accepts the --name=value form (#286)") {
      val r = onion.Cli.parse(Array("world", "--count=5"), "name,count=")
      assert(r.toSeq == Seq("world", "5"))
    }

    it("still accepts the --name value (space) form") {
      val r = onion.Cli.parse(Array("world", "--count", "5"), "name,count=")
      assert(r.toSeq == Seq("world", "5"))
    }

    it("accepts --switch=true and a bare switch (#286)") {
      val explicit = onion.Cli.parse(Array("world", "--loud=true"), "name,loud?")
      assert(explicit.toSeq == Seq("world", "true"))
      val bare = onion.Cli.parse(Array("world", "--loud"), "name,loud?")
      assert(bare.toSeq == Seq("world", "true"))
    }

    it("handles a value that itself contains an equals sign") {
      val r = onion.Cli.parse(Array("--expr=a=b"), "expr=")
      assert(r.toSeq == Seq("a=b"))
    }
  }
}
