package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * The static helpers that build a `Server.Response` (`json`, `text`, `html`, `status`,
 * `notFound`) live on `Server`, not on the nested `Response`, so `Response::json(body)`
 * failed with "did you mean: of". When a static call on a nested class misses but the
 * enclosing class has a static method of that name and arity, the hint now names it.
 */
class EnclosingStaticHintSpec extends AbstractShellSpec {
  private def messagesOf(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  describe("a static call on a nested class whose enclosing class has the method") {
    it("Response::json names Server::json") {
      val msgs = messagesOf("import { onion.Server.Response }\nval r = Response::json(\"{}\")\n")
      assert(msgs.contains("Server::json"), msgs)
    }
    it("Response::status(201, body) names Server::status") {
      val msgs = messagesOf("import { onion.Server.Response }\nval r = Response::status(201, \"ok\")\n")
      assert(msgs.contains("Server::status"), msgs)
    }
    it("does not fire when the enclosing class has no such method") {
      val msgs = messagesOf("import { onion.Server.Response }\nval r = Response::frobnicate(1)\n")
      assert(!msgs.contains("Server::"), msgs)
    }
  }
}
