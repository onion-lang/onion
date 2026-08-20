package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for every syntax-highlighting grammar Onion ships.
 *
 * Onion deliberately maintains the same lexical surface in three places:
 *
 *   - `grammar/JJOnionParser.jj`                     — the compiler. The single source of truth.
 *   - `vscode-onion/syntaxes/onion.tmLanguage.json`  — VS Code (and any TextMate consumer).
 *   - `tools/pygments-onion/onion_pygments/…`        — the docs site (MkDocs uses Pygments,
 *                                                      which cannot read a TextMate grammar).
 *
 * Keeping two extra grammars is only defensible because this spec mechanically derives the
 * keyword set from the parser and holds the other two to it. Without that, they rot: the
 * TextMate grammar had drifted to roughly 70% coverage — it was missing `trait`, `instance`,
 * every soft keyword (`tool`, `requires`, `shape`, `law`, `from`, `derive`, `example`), the
 * scheme-prefixed raw literals (`re"…"`, `file"…"`, `http"…"`, which appear 225 times in the
 * docs), the `|>` pipeline operator, and backtick-quoted identifiers. The most distinctive
 * parts of the language were the unhighlighted ones.
 *
 * Both directions are checked. Forward catches a keyword added to the parser and forgotten in
 * the highlighters; reverse catches a keyword removed from the parser and left behind as a
 * stale entry.
 */
class SyntaxHighlightingDriftSpec extends AnyFunSpec {

  private val GrammarPath   = "grammar/JJOnionParser.jj"
  private val TmLanguagePath = "vscode-onion/syntaxes/onion.tmLanguage.json"
  private val PygmentsPath  = "tools/pygments-onion/onion_pygments/lexer.py"

  private def read(p: String): String =
    val path = java.nio.file.Path.of(p)
    assert(java.nio.file.Files.exists(path), s"$p does not exist")
    java.nio.file.Files.readString(path)

  // ----------------------------------------------------------------- the source of truth

  private lazy val grammar: String = read(GrammarPath)

  /**
   * Hard keywords: the `TOKEN` block's `<K_NAME: "kw">` entries. A few carry alternatives
   * (`<K_VOID: "void" | "Unit">`), so every quoted literal in the definition counts.
   */
  private lazy val hardKeywords: Set[String] =
    """<K_[A-Z_]+\s*:([^>]*)>""".r
      .findAllMatchIn(grammar)
      .flatMap(m => """"([a-zA-Z][a-zA-Z0-9_]*)"""".r.findAllMatchIn(m.group(1)).map(_.group(1)))
      .toSet

  /**
   * Soft keywords: identifiers the parser only treats specially in one position, recognised
   * through a semantic lookahead. There are two spellings of that lookahead in the grammar —
   * the explicit `getToken(n).image.equals("x")` and the `la("x")` / `la2("x")` helpers — and
   * both must be scanned, or `in` (only ever written as `la("in")`) goes missing.
   */
  private lazy val softKeywords: Set[String] =
    val explicit = """getToken\(\d+\)\.image\.equals\("([a-zA-Z_][a-zA-Z0-9_]*)"\)""".r
    val helper   = """\bla\d*\("([a-zA-Z_][a-zA-Z0-9_]*)"\)""".r
    val all =
      explicit.findAllMatchIn(grammar).map(_.group(1)).toSet ++
      helper.findAllMatchIn(grammar).map(_.group(1)).toSet
    // `_` is the wildcard pattern, not a keyword; the rest are already hard keywords being
    // re-inspected after the fact (`val` in a var/val declaration, for instance).
    all -- hardKeywords - "_"

  private lazy val allKeywords: Set[String] = hardKeywords ++ softKeywords

  // ------------------------------------------------------------------- TextMate grammar

  private lazy val tmLanguage: String = read(TmLanguagePath)

  /** `{ "name": "...", "match": "..." }` pairs, which is how every word list is written. */
  private lazy val tmRules: Seq[(String, String)] =
    """"name"\s*:\s*"([^"]+)"\s*,\s*"match"\s*:\s*"((?:[^"\\]|\\.)*)"""".r
      .findAllMatchIn(tmLanguage)
      .map(m => (m.group(1), m.group(2)))
      .toSeq

  /**
   * Every identifier-shaped alternative in a TextMate `match`.
   *
   * Two kinds of regex syntax are stripped first, because neither can ever spell a keyword
   * and both would otherwise poison the reverse check: escapes such as `\b` (which would
   * contribute a word `b`) and character classes such as `[a-zA-Z_]` (which would contribute
   * `a`, `zA` and `Z_`). Soft-keyword rules are pinned with lookaheads full of both.
   */
  private def wordsIn(pattern: String): Set[String] =
    val withoutEscapes = pattern.replaceAll("""\\\\[a-zA-Z]""", " ")
    val withoutClasses = withoutEscapes.replaceAll("""\[[^\]]*\]""", " ")
    """[a-zA-Z_][a-zA-Z0-9_]*""".r.findAllIn(withoutClasses).toSet

  private lazy val tmWords: Set[String] = tmRules.flatMap((_, p) => wordsIn(p)).toSet

  /** Rule-name prefixes whose `match` is a list of keywords rather than of types or symbols. */
  private val KeywordRulePrefixes =
    Seq("keyword.", "storage.", "constant.language.", "variable.language.")

  private lazy val tmKeywordRuleWords: Set[String] =
    tmRules.collect {
      case (name, pattern) if KeywordRulePrefixes.exists(name.startsWith) => wordsIn(pattern)
    }.flatten.toSet

  // --------------------------------------------------------------------- Pygments lexer

  private lazy val pygments: String = read(PygmentsPath)

  private lazy val pygmentsWords: Set[String] =
    // The lexer spells its keyword lists as `words((...))` / r'\b(a|b|c)\b' alternations; a
    // plain identifier scan over the whole module is enough to prove a keyword is present.
    """[a-zA-Z_][a-zA-Z0-9_]*""".r.findAllIn(pygments).toSet

  // ------------------------------------------------------------------------------ tests

  describe("the parser grammar") {
    it("yields a plausible keyword set (the scan itself has not rotted)") {
      assert(hardKeywords.size >= 60,
        s"only ${hardKeywords.size} hard keywords extracted from $GrammarPath — the TOKEN-block " +
        s"scan has rotted: ${hardKeywords.toSeq.sorted.mkString(", ")}")
      assert(softKeywords.nonEmpty,
        s"no soft keywords extracted from $GrammarPath — the lookahead scan has rotted")
      // These are the language's differentiators; if the extraction stops finding them the
      // guard would pass vacuously, which is worse than failing.
      for (expected <- Seq("tool", "requires", "shape", "conforms", "from", "law", "in"))
        assert(softKeywords.contains(expected),
          s"expected `$expected` among the extracted soft keywords, got: " +
          softKeywords.toSeq.sorted.mkString(", "))
    }
  }

  describe("the TextMate grammar") {
    it("has a rule for every keyword the parser knows") {
      assert(tmRules.nonEmpty, s"no name/match rule pairs found in $TmLanguagePath — scan rotted")
      val missing = (allKeywords -- tmWords).toSeq.sorted
      assert(missing.isEmpty,
        s"$TmLanguagePath does not highlight these parser keywords: ${missing.mkString(", ")}")
    }

    it("has no keyword rule the parser no longer knows") {
      val stale = (tmKeywordRuleWords -- allKeywords).toSeq.sorted
      assert(stale.isEmpty,
        s"$TmLanguagePath highlights these as keywords but $GrammarPath has no such keyword: " +
        stale.mkString(", "))
    }

    it("covers the lexical forms that are not keywords at all") {
      // Scheme-prefixed raw literals, the pipeline operator and backtick identifiers are
      // tokens, not words, so the keyword scan above cannot see them.
      val required = Map(
        "scheme-prefixed raw literal (re\"…\", file\"…\", any prefix\"…\")"
          -> "string.quoted.double.raw.onion",
        "|> pipeline operator"    -> "keyword.operator.pipeline.onion",
        "backtick identifier"     -> "variable.other.quoted.onion"
      )
      val absent = required.collect { case (what, scope) if !tmLanguage.contains(scope) => what }
      assert(absent.isEmpty,
        s"$TmLanguagePath is missing highlighting for: ${absent.toSeq.sorted.mkString("; ")}")
    }
  }

  describe("the Pygments lexer (used by the docs site)") {
    it("has a rule for every keyword the parser knows") {
      val missing = (allKeywords -- pygmentsWords).toSeq.sorted
      assert(missing.isEmpty,
        s"$PygmentsPath does not highlight these parser keywords: ${missing.mkString(", ")}")
    }

    it("declares itself as the `onion` lexer so ```onion fences resolve to it") {
      assert(pygments.contains("'onion'") || pygments.contains("\"onion\""),
        s"$PygmentsPath must list `onion` in its aliases, or MkDocs will not use it")
    }

    it("covers the lexical forms that are not keywords at all") {
      val required = Seq(
        "scheme-prefixed raw literal" -> """Scheme""",
        "|> pipeline operator"        -> """\|>""",
        "backtick identifier"         -> """`"""
      )
      val absent = required.collect { case (what, needle) if !pygments.contains(needle) => what }
      assert(absent.isEmpty,
        s"$PygmentsPath is missing highlighting for: ${absent.mkString("; ")}")
    }
  }
}
