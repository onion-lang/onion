# Syntax Highlighting

Onion describes its own lexical surface in three places. Only one of them is authoritative.

| Where | File | Used by |
|---|---|---|
| **Source of truth** | `grammar/JJOnionParser.jj` | the compiler |
| TextMate grammar | `vscode-onion/syntaxes/onion.tmLanguage.json` | VS Code, and any TextMate consumer |
| Pygments lexer | `tools/pygments-onion/` | this documentation site |

## Why there are two highlighters

The docs site is built with MkDocs, which highlights through Pygments. Pygments cannot read
a TextMate grammar, and Material for MkDocs has no supported path to a TextMate-based
highlighter that keeps the pages statically generated. So the same lexical surface is
described twice, in two languages, for two consumers.

That is a drift risk, and it had already happened: before the Pygments lexer existed the
TextMate grammar had rotted to roughly 70% coverage. It was missing `trait`, `instance`, and
every soft keyword — `tool`, `requires`, `shape`, `law`, `example`, `from`, `derive` — plus
the scheme-prefixed raw literals (`re"…"`, `file"…"`, `http"…"`), the `|>` pipeline operator,
and backtick-quoted identifiers. The most distinctive parts of the language were exactly the
unhighlighted ones, and nothing failed.

## The drift guard

`src/test/scala/onion/compiler/tools/SyntaxHighlightingDriftSpec.scala` derives the keyword
set mechanically from the parser and holds both highlighters to it:

- **hard keywords** come from the `TOKEN` block's `<K_NAME: "kw">` entries;
- **soft keywords** come from the two spellings of semantic lookahead the grammar uses,
  `getToken(n).image.equals("x")` and the `la("x")` / `la2("x")` helpers. Both must be
  scanned — `in` only ever appears as `la("in")`.

It checks both directions. Forward catches a keyword added to the parser and forgotten in a
highlighter; reverse catches a keyword removed from the parser and left behind as a stale
entry. It also asserts the extraction itself still finds the language's differentiators, so
the guard cannot pass vacuously after the grammar file is reorganised.

## Adding a keyword

1. Add it to `grammar/JJOnionParser.jj`.
2. Run `sbt 'testOnly *SyntaxHighlightingDriftSpec'`. It will fail, naming the keyword.
3. Add it to `vscode-onion/syntaxes/onion.tmLanguage.json` and to
   `tools/pygments-onion/onion_pygments/lexer.py`.

For a **soft** keyword, pin the rule to the one shape the parser's lookahead accepts. A soft
keyword is a legal identifier everywhere else, so `val shape = 1` must stay uncoloured; the
existing rules use lookaheads such as `\btool\b(?=\s+[a-zA-Z_]\w*\s*\()` for that.

Keyword rules deliberately precede the scheme-literal rules in both highlighters. The lexer
in `JJOnionParser.jj` pushes a keyword prefix back out of a would-be scheme token, so
`return"x"` is a keyword followed by a string rather than a call to `return`; matching
keywords first reproduces that.

## Building the docs locally

```bash
pip install mkdocs-material mkdocs-glightbox ./tools/pygments-onion
mkdocs serve
```

Installing `./tools/pygments-onion` is not optional. Without it Pygments has no `onion`
lexer, every ` ```onion ` fence renders as plain text, and **mkdocs does not warn** — the
page just looks wrong.

## Checking the fences

A missing rule for a non-keyword form does not show up as a keyword gap. It shows up as a
Pygments `Error` token, which renders as unstyled text with no build failure. So the fences
are lexed directly:

```bash
python tools/pygments-onion/check_docs.py docs README.md
```

This runs in `.github/workflows/docs.yml` before deploy, and on pull requests. It is how the
shebang gap was found: seven blocks across five pages were quietly emitting an error token
for the `#` of `#!/usr/bin/env onion`.

## GitHub

Fenced ` ```onion ` blocks stay **unhighlighted on github.com**, and that cannot be fixed
from this repository. GitHub resolves a fence's info string against
[github-linguist](https://github.com/github-linguist/linguist)'s language list, and adding a
language there requires the extension to have at least 2000 files indexed across a spread of
repositories. Onion is nowhere near that. A `.gitattributes` `linguist-language` override
does not help either: it changes how a *file* is classified, not how a fence inside a
Markdown file is highlighted.

What `.gitattributes` does buy is colour for `.on` files themselves, which matters for the
sample programs under `run/`. They are mapped to Scala for display and excluded from the
repository language statistics:

```
*.on linguist-language=Scala linguist-detectable=false
```

Shared constructs — `def`, `val`, `var`, `class`, `extends`, `case`, `enum`, strings,
comments, numbers, and even `re"…"` because Scala has interpolated strings — come out right.
Onion-only keywords such as `conforms`, `foreach`, `select` and `tool` do not. That is the
honest cost of borrowing another language's grammar, and it is why the fences are left
tagged `onion` rather than relabelled: `DocExamplesCompileSpec` and the readiness extractor
both find Onion code by that tag, and retagging would silently switch off their compile
checking.
