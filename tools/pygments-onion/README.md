# onion-pygments

A [Pygments](https://pygments.org/) lexer for [Onion](https://onion-lang.org), used to
highlight ` ```onion ` fences on the documentation site.

## Why this exists separately from the TextMate grammar

Onion ships a TextMate grammar at `vscode-onion/syntaxes/onion.tmLanguage.json` for VS
Code. The docs site is MkDocs, which highlights through Pygments, and Pygments cannot read
a TextMate grammar — so the same lexical surface is described twice.

Keeping two grammars in step is a drift risk, and the TextMate one had already rotted to
roughly 70% coverage before this package existed. So neither is the source of truth:
`grammar/JJOnionParser.jj` is, and `SyntaxHighlightingDriftSpec` derives the keyword set
from the parser's own token definitions and fails the build if either highlighter falls
behind. Add a keyword to the parser and both highlighters must follow.

## Install

```bash
pip install ./tools/pygments-onion
```

The docs workflow (`.github/workflows/docs.yml`) does this alongside `mkdocs-material`.
Install it locally too, or `mkdocs serve` will render Onion snippets as plain text.

## Check it works

```bash
python -c "from pygments.lexers import get_lexer_by_name; print(get_lexer_by_name('onion'))"
```
