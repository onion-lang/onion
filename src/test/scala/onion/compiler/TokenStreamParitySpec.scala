package onion.compiler

import onion.compiler.parser.{JJOnionParser, JJOnionParserConstants as K, OnionLexer, Token}

import java.io.{File, StringReader}
import org.scalatest.funspec.AnyFunSpec
import scala.io.Source

/**
 * `OnionLexer`'s own class doc claims its token stream is "pinned by the token-golden
 * comparison over `run/` and `src/test/run/`" (see its header comment), but no such
 * comparison runs anywhere in the suite -- friction issue #1954. `FastPathParserParitySpec`
 * only compares the resulting AST, and only for programs the handwritten `OnionParser`
 * accepts outright; it would not notice `OnionLexer` drifting from the generated tokenizer
 * (a different token kind, image, or position) as long as both parsers still happened to
 * build the same tree, or as long as the drift only shows up once `OnionParser` has already
 * given up and handed off to `JJOnionParser` for diagnostics.
 *
 * This drives the generated parser `JJOnionParser` itself with each tokenizer in turn --
 * the plain generated `JJOnionParserTokenManager` (via its `Reader` constructor) and
 * `OnionLexer` (the combination `Parsing.scala` already uses for `-Donion.parser.javacc=true`)
 * -- and compares every token `JJOnionParser` actually consumed while parsing, specials
 * (comments) included. Reading the persistent `token.next` list after a full parse, rather
 * than calling `getNextToken()` on each tokenizer directly, avoids having to reproduce the
 * grammar's own `SwitchTo` calls between the `DEFAULT`/`IN_STATEMENT` lexical states by hand.
 */
class TokenStreamParitySpec extends AnyFunSpec {

  private def samplesIn(dir: String): Seq[File] =
    Option(new File(dir).listFiles()).getOrElse(Array.empty[File]).toSeq
      .filter(_.getName.endsWith(".on"))
      .sortBy(_.getName)

  private def samples: Seq[File] = samplesIn("run") ++ samplesIn("src/test/run")

  private case class TokenSnapshot(
    kind: Int,
    image: String,
    beginLine: Int,
    beginColumn: Int,
    endLine: Int,
    endColumn: Int
  )

  private def snapshot(t: Token): TokenSnapshot =
    TokenSnapshot(t.kind, t.image, t.beginLine, t.beginColumn, t.endLine, t.endColumn)

  private def parseAndCollect(parser: JJOnionParser): Vector[TokenSnapshot] = {
    val sentinel = parser.token // the list head is built lazily; capture it before parsing
    parser.unit()
    val out = Vector.newBuilder[TokenSnapshot]
    var t = sentinel.next
    while (t != null) {
      var specials = List.empty[Token]
      var s = t.specialToken
      while (s != null) { specials = s :: specials; s = s.specialToken }
      specials.foreach(sp => out += snapshot(sp))
      out += snapshot(t)
      t = if (t.kind == K.EOF) null else t.next
    }
    out.result()
  }

  private def parseWithJavaCC(code: String): Vector[TokenSnapshot] =
    parseAndCollect(new JJOnionParser(new StringReader(code)))

  private def parseWithOnionLexer(code: String): Vector[TokenSnapshot] =
    parseAndCollect(new JJOnionParser(new OnionLexer(code)))

  describe("OnionLexer drives JJOnionParser through the same token stream as the generated tokenizer") {
    val files = samples

    it("finds the sample programs") {
      assert(files.nonEmpty, "no .on samples found under run/ or src/test/run/")
    }

    files.foreach { f =>
      it(s"${f.getPath}: token stream (kind/image/position, including comments) is identical") {
        val code = Source.fromFile(f, "UTF-8").mkString
        // A handful of fixtures under src/test/run/ predate the current grammar and don't
        // reach <EOF> in JJOnionParser.unit() at all (through either tokenizer) -- they are
        // parsed only by other means elsewhere, or not at all. Comparing token streams needs
        // a full parse to anchor the walk at, so skip a file neither tokenizer can get through
        // instead of failing on something this spec was never meant to check. A file only one
        // tokenizer can get through is exactly the divergence this spec exists to catch.
        val javacc = try Right(parseWithJavaCC(code)) catch { case e: Throwable => Left(e) }
        val onionLexer = try Right(parseWithOnionLexer(code)) catch { case e: Throwable => Left(e) }
        (javacc, onionLexer) match {
          case (Left(_), Left(_)) =>
            cancel(s"neither JJOnionParserTokenManager nor OnionLexer can fully parse ${f.getPath} via JJOnionParser.unit()")
          case (Right(a), Right(b)) =>
            assert(a == b)
          case (Left(e), Right(_)) =>
            fail(s"JJOnionParserTokenManager failed to parse ${f.getPath} but OnionLexer succeeded: $e")
          case (Right(_), Left(e)) =>
            fail(s"OnionLexer failed to parse ${f.getPath} but JJOnionParserTokenManager succeeded: $e")
        }
      }
    }
  }
}
