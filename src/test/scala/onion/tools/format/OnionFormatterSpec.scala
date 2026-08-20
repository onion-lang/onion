package onion.tools.format

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `onion fmt`.
 *
 * The two properties that matter are checked over the whole `run/` corpus rather than on
 * toy inputs, because a formatter is exactly the kind of tool whose bugs live in the
 * constructs nobody thought to write a case for:
 *
 *   - **idempotence** — a second run changes nothing, or `--check` can never be trusted
 *   - **token preservation** — every token and comment survives, which for a
 *     whitespace-only formatter is what "does not change the program" means
 *
 * Comparing token streams rather than ASTs is deliberate: an AST comparison would have to
 * ignore locations (which necessarily move), and ignoring locations means ignoring exactly
 * the thing being changed.
 */
class OnionFormatterSpec extends AnyFunSuite with Matchers:

  private def samples: Seq[Path] =
    Files.list(Path.of("run")).iterator().asScala
      .filter(_.getFileName.toString.endsWith(".on")).toSeq.sortBy(_.toString)

  private def formatted(source: String): String = OnionFormatter.format(source).text

  // ------------------------------------------------------------------ properties

  test("formatting every run/ sample preserves its tokens and comments exactly"):
    val corpus = samples
    corpus should not be empty

    val broken = corpus.filter { path =>
      val source = Files.readString(path, UTF_8)
      OnionFormatter.signature(formatted(source)) != OnionFormatter.signature(source)
    }
    withClue(broken.map(_.getFileName).mkString(", ")) { broken shouldBe empty }

  test("formatting every run/ sample is idempotent"):
    val unstable = samples.filter { path =>
      val once = formatted(Files.readString(path, UTF_8))
      formatted(once) != once
    }
    withClue(unstable.map(_.getFileName).mkString(", ")) { unstable shouldBe empty }

  test("every line break the author wrote survives"):
    // The grammar makes a newline a real token, so moving one is a semantic change, not a
    // cosmetic one. This is what stops the formatter from ever becoming a reflower.
    val moved = samples.flatMap { path =>
      val source = Files.readString(path, UTF_8)
      val before = source.count(_ == '\n')
      val after = formatted(source).count(_ == '\n')
      if before == after then None else Some(s"${path.getFileName}: $before -> $after")
    }
    withClue(moved.take(5).mkString("; ")) { moved shouldBe empty }

  // ------------------------------------------------------------------ behaviour

  test("leaves indentation exactly as the author wrote it"):
    // Computing indentation from brace depth was built and measured first: it rewrote 36%
    // of the corpus, almost all of it wrongly, because Onion's continuations are not
    // bracketed. Nothing lexical separates a misindented line from an expression body, a
    // `.filter` chain, or an `example { … }` clause, so this reproduces and never
    // recomputes.
    val source =
      "class C {\n" +
      "public:\n" +
      "        def f(): Int {\n" +
      "  return 1\n" +
      "        }\n" +
      "}\n"
    formatted(source) shouldBe source

  test("keeps a line that continues an expression where the author put it"):
    val source = "val total = first +\n            second\n"
    formatted(source) shouldBe source

  test("keeps a method chain aligned as written"):
    val source = "val open = bookings.filter { b => b.active() }\n      .map { b => b.id() }\n"
    formatted(source) shouldBe source

  test("keeps a tab inside a line, not only at its start"):
    formatted("this.text\t\t= new JTextField\n") shouldBe "this.text\t\t= new JTextField\n"

  test("does not glue a soft keyword to a following parenthesis"):
    // A soft keyword is lexed as an identifier, so `in (i + 1)` looks exactly like a call
    // to something named `in` -- which is one of the reasons no rule touches a bracket.
    val source = "foreach j: Int in (i + 1)..<n { }\n"
    formatted(source) shouldBe source

  test("keeps tabs, which an early version silently expanded to eight spaces"):
    // JavaCC advances a column to the next multiple of eight for a tab, so rebuilding
    // indentation from column numbers turned tab-indented sample programs into
    // eight-space-indented ones -- 110 lines of churn in one file, from a formatter that
    // had just declared it would not touch indentation. Dogfooding caught this; none of
    // the properties above did.
    formatted("import {\n\tjava.util.*\n}\n") shouldBe "import {\n\tjava.util.*\n}\n"

  test("adds the newline a file is missing at its end"):
    // The one change to layout outside a line. An absent final newline is a defect rather
    // than a style: it is what makes a diff say "\\ No newline at end of file".
    formatted("val a = 1") shouldBe "val a = 1\n"
    formatted("val a = 1\n\n") shouldBe "val a = 1\n\n"

  test("keeps a comment, including one with nothing after it"):
    // A trailing comment attaches to EOF, so an implementation that stops at EOF deletes
    // it silently. That is the first thing this had to get right.
    val source = "val x = 1  // why\n// the end\n"
    val out = formatted(source)
    out should include("// why")
    out should include("// the end")

  test("leaves brackets alone in both directions, because every firing on real code was damage"):
    // Space just inside a bracket is a right-aligned column: `new Employee( 1, ...)` above
    // `new Employee(10, ...)` in run/HRSystem.on. Space before one is the same thing from
    // the other side: an enum in run/InsuranceClaims.on pads its cases so their argument
    // lists line up, with five spaces on one line and a single space on the next -- so not
    // even "collapse a lone space" separates the typo from the intent.
    formatted("f( a )\n") shouldBe "f( a )\n"
    formatted("g(new E( 1, x))\ng(new E(10, x))\n") shouldBe "g(new E( 1, x))\ng(new E(10, x))\n"
    formatted("AUTO     (\"Auto\"),\nPROPERTY (\"Property\")\n") shouldBe
      "AUTO     (\"Auto\"),\nPROPERTY (\"Property\")\n"

  test("leaves a square bracket alone too, since nothing lexical says which kind it is"):
    // `a[i]` indexes, `= [1, 2]` is a list literal, `List[Int]` is a type argument -- and
    // `in` is a soft keyword lexed as an identifier, so `foreach x: T in [xs]` looks
    // exactly like an index. Closing that up produced `in[xs]`.
    formatted("foreach x: Int in [1, 2] { }\n") shouldBe "foreach x: Int in [1, 2] { }\n"
    formatted("val n = a[i]\n") shouldBe "val n = a[i]\n"

  test("binds member access to what it touches"):
    formatted("IO::println(\"=\" .rep(60))\n") shouldBe "IO::println(\"=\".rep(60))\n"

  test("does not space a sign away from its number"):
    formatted("f(- 1)\n") shouldBe "f(-1)\n"

  test("leaves a subtraction alone"):
    formatted("val n = a - b\n") shouldBe "val n = a - b\n"

  test("tightens punctuation without touching the rest of the line"):
    formatted("f(a , b)\n") shouldBe "f(a, b)\n"

  test("keeps deliberate alignment inside a line"):
    // Flattening runs of spaces would trade a real intent for a uniformity nobody asked
    // for, so anything without a rule keeps what the author wrote.
    formatted("val a   = 1\n") shouldBe "val a   = 1\n"

  test("reports whether it changed anything, which is what --check needs"):
    OnionFormatter.format("val a = 1\n").changed shouldBe false
    OnionFormatter.format("val  a = 1\n").changed shouldBe false // no rule applies
    OnionFormatter.format("f(a , b)\n").changed shouldBe true

  test("leaves a whitespace-only file untouched, since it has no tokens for a rule to apply to"):
    // The lexer produces no lexeme at all when a source has neither a real token nor a
    // comment, and the main loop is a no-op over an empty sequence -- which silently
    // collapsed the file to an empty string instead of leaving whitespace it has no rule
    // for exactly as written, the same "byte for byte" guarantee every other test here
    // checks for whitespace that does have a token around it.
    val source = "   \n\n  \t \n"
    OnionFormatter.format(source) shouldBe OnionFormatter.FormatResult(source, changed = false)
