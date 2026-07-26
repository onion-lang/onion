package onion.compiler.effects

import org.scalatest.funspec.AnyFunSpec

/**
 * The effect table (issue #356): the shipped resource classifies the effectful stdlib
 * entry points, a specific entry beats its class wildcard, and anything unlisted is
 * `unknown` — not known pure.
 */
class EffectTableSpec extends AnyFunSpec {
  import Effect._

  private def of(cls: String, m: String): Set[Effect] =
    EffectTable.effectsOrUnknown(cls, m)

  describe("filesystem") {
    it("classifies reads, writes and both-sided operations") {
      assert(of("onion.Files", "readText") == Set(Read))
      assert(of("onion.Files", "writeText") == Set(Write))
      assert(of("onion.Files", "delete") == Set(Write))
      assert(of("onion.Files", "copy") == Set(Read, Write))
      assert(of("onion.Files", "move") == Set(Read, Write))
      assert(of("onion.FileResource", "text") == Set(Read))
      assert(of("onion.FileResource", "append") == Set(Write))
    }
    it("knows the path-string helpers never touch the disk") {
      for (m <- Seq("ext", "stem", "joinPath", "getFileName", "getParent", "withExtension"))
        assert(of("onion.Files", m).isEmpty, m)
    }
    it("classifies getAbsolutePath as env (resolves against the cwd)") {
      assert(of("onion.Files", "getAbsolutePath") == Set(Env))
    }
  }

  describe("processes, network, console") {
    it("marks every Proc entry exec via the wildcard") {
      for (m <- Seq("run", "exec", "capture", "status", "stdout"))
        assert(of("onion.Proc", m) == Set(Exec), m)
    }
    it("splits Http between pure builders and net calls") {
      assert(of("onion.Http", "get") == Set(Net))
      assert(of("onion.Http", "postJson") == Set(Net))
      assert(of("onion.Http", "buildUrl").isEmpty)
      assert(of("onion.Http", "encodeUrl").isEmpty)
      assert(of("onion.HttpResource", "read") == Set(Net))
      assert(of("onion.HttpResource", "url").isEmpty)
    }
    it("marks IO console except the pure formatter") {
      assert(of("onion.IO", "println") == Set(Console))
      assert(of("onion.IO", "readLine") == Set(Console))
      assert(of("onion.IO", "format").isEmpty)
    }
    it("marks the exiting Cli entry points console+exec, the rest pure") {
      assert(of("onion.Cli", "parse") == Set(Console, Exec))
      assert(of("onion.Cli", "requireArgs") == Set(Console, Exec))
      assert(of("onion.Cli", "tryParse").isEmpty)
      assert(of("onion.Cli", "parseInt").isEmpty)
    }
  }

  describe("clock, randomness, environment") {
    it("confines DateTime's clock to now/nowString") {
      assert(of("onion.DateTime", "now") == Set(Clock))
      assert(of("onion.DateTime", "nowString") == Set(Clock))
      assert(of("onion.DateTime", "parse").isEmpty)
      assert(of("onion.DateTime", "addDays").isEmpty)
    }
    it("marks Timing clock except the formatters") {
      assert(of("onion.Timing", "nanos") == Set(Clock))
      assert(of("onion.Timing", "sleep") == Set(Clock))
      assert(of("onion.Timing", "formatMillis").isEmpty)
    }
    it("marks all of Rand, and OnionMath's two random entries") {
      assert(of("onion.Rand", "nextInt") == Set(Rand))
      assert(of("onion.Rand", "shuffle") == Set(Rand))
      assert(of("onion.OnionMath", "random") == Set(Rand))
      assert(of("onion.OnionMath", "randomInt") == Set(Rand))
      assert(of("onion.OnionMath", "sin").isEmpty)
    }
    it("classifies Config: fs load, env reads, pure accessors") {
      assert(of("onion.Config", "loadJson") == Set(Read))
      assert(of("onion.Config", "getEnv") == Set(Env))
      assert(of("onion.Config", "getWithEnvOverride") == Set(Env))
      assert(of("onion.Config", "getString").isEmpty)
    }
    it("classifies the JDK effect points") {
      assert(of("java.lang.System", "getenv") == Set(Env))
      assert(of("java.lang.System", "currentTimeMillis") == Set(Clock))
      assert(of("java.lang.System", "exit") == Set(Exec))
      assert(of("java.lang.Runtime", "exec") == Set(Exec))
      assert(of("java.lang.Thread", "sleep") == Set(Clock))
      assert(of("java.io.PrintStream", "println") == Set(Console))
    }
  }

  describe("the pure baseline") {
    it("covers the pure onion stdlib and shape combinators") {
      for (cls <- Seq("onion.Json", "onion.Strings", "onion.Shapes", "onion.Outcome",
                      "onion.Origin", "onion.Regex", "onion.Result", "onion.Option"))
        assert(of(cls, "anything").isEmpty, cls)
    }
    it("covers common JDK value and collection types") {
      assert(of("java.lang.String", "length").isEmpty)
      assert(of("java.lang.StringBuilder", "append").isEmpty)
      assert(of("java.util.List", "get").isEmpty)
      assert(of("java.util.LinkedHashMap", "put").isEmpty)
      assert(of("java.lang.Object", "<init>").isEmpty)
      assert(of("java.lang.Integer", "parseInt").isEmpty)
    }
    it("treats invoking a function value as pure (charged at creation)") {
      assert(of("onion.Function1", "call").isEmpty)
    }
  }

  describe("what the table cannot vouch for") {
    it("reports an unlisted Java method as unknown, not pure") {
      assert(of("javax.swing.JFrame", "setVisible") == Set(Unknown))
      assert(of("java.io.BufferedReader", "readLine") == Set(Unknown))
      assert(of("com.example.Anything", "whatever") == Set(Unknown))
    }
    it("reports printStackTrace as console even though Throwable is otherwise pure") {
      assert(of("java.lang.Throwable", "printStackTrace") == Set(Console))
      assert(of("java.lang.Throwable", "getMessage").isEmpty)
    }
  }

  describe("the parser") {
    it("applies specific-beats-wildcard") {
      val t = EffectTable.parseLines(Iterator("a.B#*=exec", "a.B#quiet=pure"))
      assert(t.exact((("a.B", "quiet"))).isEmpty)
      assert(t.wildcard("a.B") == Set(Effect.Exec))
    }
    it("fails loudly on a malformed line rather than dropping it") {
      intercept[RuntimeException](EffectTable.parseLines(Iterator("no-equals-here")))
      intercept[RuntimeException](EffectTable.parseLines(Iterator("a.B=read")))
      intercept[RuntimeException](EffectTable.parseLines(Iterator("a.B#m=notarealeffect")))
    }
    it("renders pure and sorted effect sets") {
      assert(Effect.render(Set.empty) == "pure")
      assert(Effect.render(Set(Effect.Write, Effect.Read)) == "read,write")
    }
  }
}
