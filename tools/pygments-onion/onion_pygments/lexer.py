"""Pygments lexer for the Onion programming language.

The docs site is built with MkDocs, which highlights through Pygments. Pygments cannot
read a TextMate grammar, so the ``vscode-onion`` grammar cannot be reused here and this
is a second implementation of the same lexical surface.

That duplication is only safe because ``SyntaxHighlightingDriftSpec`` derives the keyword
set from ``grammar/JJOnionParser.jj`` -- the compiler's own token definitions -- and fails
the build if either highlighter falls behind it. Add a keyword to the parser and this file
has to follow, or ``sbt test`` goes red.
"""

from pygments.lexer import RegexLexer, bygroups, include, words
from pygments.token import (
    Comment,
    Keyword,
    Name,
    Number,
    Operator,
    Punctuation,
    String,
    Whitespace,
)

__all__ = ["OnionLexer"]


#: Types that are keywords in the grammar rather than library classes.
TYPE_KEYWORDS = (
    "Boolean", "Byte", "Char", "Double", "Float", "Int", "Long", "Short", "Unit", "void",
)

NAMESPACE_KEYWORDS = ("import", "module")

DECLARATION_KEYWORDS = (
    "class", "interface", "trait", "instance", "enum", "record", "extension", "type",
    "def", "val", "var",
)

MODIFIER_KEYWORDS = (
    "abstract", "const", "final", "internal", "override", "private", "protected",
    "public", "sealed", "static", "synchronized", "volatile",
)

CONTROL_KEYWORDS = (
    "break", "case", "catch", "continue", "do", "else", "finally", "for", "foreach",
    "goto", "if", "in", "is", "ret", "return", "select", "throw", "throws", "try",
    "when", "while",
)

OTHER_KEYWORDS = ("and", "as", "conforms", "extends", "forward", "new", "or")

CONSTANT_KEYWORDS = ("false", "null", "true")

PSEUDO_KEYWORDS = ("self", "super", "this")

#: Classes reachable without an import, worth marking as library rather than user code.
BUILTIN_CLASSES = (
    "Archive", "Args", "Assert", "Cli", "Codec", "Colls", "Config", "Csv", "DateTime",
    "Defect", "Files", "Format", "Future", "Hash", "Http", "IO", "Iterables", "Json",
    "Maps", "Option", "Outcome", "Proc", "Rand", "Range", "Regex", "Residue", "Result",
    "Sets", "Shape", "Stats", "Strings", "Text", "Timing", "View", "Yaml",
)


class OnionLexer(RegexLexer):
    """Lexer for Onion source (``.on``)."""

    name = "Onion"
    aliases = ["onion"]
    filenames = ["*.on"]
    mimetypes = ["text/x-onion"]
    url = "https://onion-lang.org"

    tokens = {
        "root": [
            # `#!` is only a shebang on the very first line, which is where Parsing.scala
            # strips it; anywhere else `#` is the start of `#{` interpolation or a `#<>`
            # class name. `\A` (not `^`) is what pins it to line one, since Pygments
            # compiles these patterns with re.MULTILINE.
            (r"\A#!.*", Comment.Hashbang),
            (r"\s+", Whitespace),
            include("comments"),

            # Backticks escape a reserved word so it can be used as a name; matching them
            # first stops the keyword rules from colouring the word inside.
            (r"`[^`]+`", Name.Variable),

            # Keywords come before the scheme literals on purpose. The lexer in
            # JJOnionParser.jj pushes a keyword prefix back out of a would-be scheme token,
            # so `return"x"` is a keyword followed by a string, not a call to `return`.
            # Matching keywords first reproduces that.
            include("keywords"),

            include("scheme-literals"),
            include("strings"),
            include("numbers"),

            (r"#<[\w.]*>", Name.Class),          # fully qualified class name
            (r"@[a-zA-Z_]\w*", Name.Decorator),  # annotation

            include("operators"),
            include("names"),
        ],

        "comments": [
            (r"//[^\n]*", Comment.Single),
            (r"/\*", Comment.Multiline, "block-comment"),
        ],
        "block-comment": [
            (r"[^*]+", Comment.Multiline),
            (r"\*/", Comment.Multiline, "#pop"),
            (r"\*", Comment.Multiline),
        ],

        "keywords": [
            # Soft keywords first. Each is a legal identifier everywhere else, so each rule
            # is pinned to the single shape the parser's semantic lookahead accepts -- a
            # variable named `shape` or `tool` has to stay uncoloured.
            (r"tool\b(?=\s+[a-zA-Z_]\w*\s*\()", Keyword.Declaration),
            (r"shape\b(?=\s+[a-zA-Z_]\w*\s*=)", Keyword.Declaration),
            (r"requires\b(?=\s*\{)", Keyword),
            (r"derive\b(?=!)", Keyword),
            (r"law\b(?=\s+[a-zA-Z_]\w*\s*[({])", Keyword),
            (r"example\b(?=\s*(\{|[a-zA-Z_]\w*\s*\{))", Keyword),
            (r"from\b(?=\s+[a-zA-Z_]\w*\")", Keyword),

            (words(NAMESPACE_KEYWORDS, suffix=r"\b"), Keyword.Namespace),
            (words(DECLARATION_KEYWORDS, suffix=r"\b"), Keyword.Declaration),
            (words(MODIFIER_KEYWORDS, suffix=r"\b"), Keyword.Declaration),
            (words(CONSTANT_KEYWORDS, suffix=r"\b"), Keyword.Constant),
            (words(PSEUDO_KEYWORDS, suffix=r"\b"), Keyword.Pseudo),
            (words(CONTROL_KEYWORDS, suffix=r"\b"), Keyword),
            (words(OTHER_KEYWORDS, suffix=r"\b"), Keyword),
            (words(TYPE_KEYWORDS, suffix=r"\b"), Keyword.Type),
        ],

        # Scheme-prefixed RAW string literals: `<prefix>"raw"` desugars to `<prefix>("raw")`.
        # Raw means a backslash is not an escape for the value, which is what lets a regex be
        # written without doubling; it still does not terminate the literal, so the body has
        # to consume `\<char>` as one unit either way.
        "scheme-literals": [
            (r'(re)(")', bygroups(Name.Function, String.Regex), "scheme-regex"),
            (r'([a-zA-Z_]\w*)(")', bygroups(Name.Function, String.Other), "scheme-raw"),
        ],
        "scheme-regex": [
            (r"\\.", String.Escape),
            (r'"', String.Regex, "#pop"),
            (r"[*+?]|\{\d+(,\d*)?\}", Operator),
            (r"[()|]", Operator),
            (r"[^\"\\*+?{}()|]+", String.Regex),
            (r".", String.Regex),
        ],
        "scheme-raw": [
            (r"\\.", String.Escape),
            (r'"', String.Other, "#pop"),
            (r'[^"\\]+', String.Other),
        ],

        "strings": [
            (r'"', String.Double, "double-string"),
            (r"'", String.Char, "char-literal"),
        ],
        "double-string": [
            (r"\\.", String.Escape),
            (r"#\{", String.Interpol, "interpolation"),
            (r'"', String.Double, "#pop"),
            (r'[^"\\#]+', String.Double),
            (r"#", String.Double),
        ],
        "interpolation": [
            (r"\}", String.Interpol, "#pop"),
            include("root"),
        ],
        "char-literal": [
            (r"\\.", String.Escape),
            (r"'", String.Char, "#pop"),
            (r"[^'\\]+", String.Char),
        ],

        "numbers": [
            (r"0[xX][0-9a-fA-F_]+[lL]?", Number.Hex),
            (r"0[bB][01_]+[lL]?", Number.Bin),
            (r"[0-9][0-9_]*\.[0-9][0-9_]*([eE][+-]?[0-9_]+)?[fFdD]?", Number.Float),
            (r"[0-9][0-9_]*[lLfFdD]?", Number.Integer),
        ],

        "operators": [
            # `|>` has to beat both `||` and the bitwise `|`.
            (r"\|>", Operator),
            (r"\?\.|\?\[|\?:|!!", Operator),
            (r"===|!==|<<=|>>>=|>>=|==|!=|<=|>=", Operator),
            (r"&&|\|\||\.\.<|\.\.|->|<-|\+\+|--|::", Operator),
            (r"\+=|-=|\*=|/=|%=|&=|\|=|\^=", Operator),
            (r"[+\-*/%&|^~!<>=]", Operator),
            (r"[{}()\[\];,.:?]", Punctuation),
        ],

        "names": [
            (words(BUILTIN_CLASSES, suffix=r"\b"), Name.Builtin),
            (r"[A-Z]\w*", Name.Class),
            (r"[a-z_]\w*(?=\s*\()", Name.Function),
            (r"[a-z_]\w*", Name),
        ],
    }
