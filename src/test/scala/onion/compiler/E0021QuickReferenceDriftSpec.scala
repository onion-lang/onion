package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * The E0005/E0021 missing-article fix (see `E0005E0021MissingArticleSpec`) updated
 * `error.semantic.constructorNotFound` to read "a constructor applicable for X(Y) is
 * not found." and updated E0005's row in the quick-reference table of both
 * `docs/reference/error-codes.md` and `docs/ja/reference/error-codes.md` to match --
 * but missed E0021's row, which still reads the pre-fix paraphrase "constructor not
 * found" (it never even carried the "constructor applicable for ..." wording the
 * property file had before that fix). This pins the row to the actual message text
 * so the quick-reference table can't drift from the message bundle again.
 */
class E0021QuickReferenceDriftSpec extends AnyFunSuite with Matchers:
  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private val expectedRow = "| `E0021` | a constructor applicable for …(…) is not found |"

  test("the English quick-reference table's E0021 row matches the actual message"):
    read("docs/reference/error-codes.md") should include(expectedRow)

  test("the Japanese quick-reference table's E0021 row matches the actual message"):
    read("docs/ja/reference/error-codes.md") should include(expectedRow)
