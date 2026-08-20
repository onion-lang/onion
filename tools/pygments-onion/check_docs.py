#!/usr/bin/env python3
"""Lex every ```onion fence in the documentation and fail on anything the lexer cannot read.

`SyntaxHighlightingDriftSpec` proves the lexer knows every keyword the parser knows. It
cannot prove the lexer can actually read the docs: a missing rule for some *non-keyword*
form shows up as a Pygments Error token, which renders as unstyled text with no warning
from mkdocs at all. That is how the shebang gap was found -- seven blocks across five
pages quietly emitted `<span class="err">#</span>`.

So this walks the fences directly. Run it locally, and in the docs workflow before deploy:

    python tools/pygments-onion/check_docs.py docs

Exit code is 1 when any fence produces an Error token, and the report names the file, the
line inside the document, and the offending text.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

from pygments.lexers import get_lexer_by_name
from pygments.token import Error

FENCE = re.compile(r"^```onion[^\n]*\n(.*?)^```", re.S | re.M)


def fences(text: str):
    """Yield (line_number_of_first_code_line, code) for each ```onion block."""
    for match in FENCE.finditer(text):
        start_line = text.count("\n", 0, match.start(1)) + 1
        yield start_line, match.group(1)


def check(paths: list[pathlib.Path]) -> int:
    lexer = get_lexer_by_name("onion")
    problems: list[str] = []
    blocks = 0

    for root in paths:
        markdown = sorted(root.rglob("*.md")) if root.is_dir() else [root]
        for doc in markdown:
            text = doc.read_text(encoding="utf-8", errors="replace")
            for start_line, code in fences(text):
                blocks += 1
                line = start_line
                for token, value in lexer.get_tokens(code):
                    if token is Error:
                        problems.append(
                            f"{doc}:{line}: lexer cannot read {value!r}"
                        )
                    line += value.count("\n")

    if blocks == 0:
        print("no ```onion fences found -- the scan has rotted", file=sys.stderr)
        return 1

    if problems:
        print(f"{len(problems)} unlexable fragment(s) in {blocks} onion block(s):",
              file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1

    print(f"ok: {blocks} onion code blocks lex cleanly")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", default=["docs"],
                        help="files or directories to scan (default: docs)")
    args = parser.parse_args()
    return check([pathlib.Path(p) for p in args.paths])


if __name__ == "__main__":
    sys.exit(main())
