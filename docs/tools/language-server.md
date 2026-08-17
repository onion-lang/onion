# Language Server (`onion-lsp`)

The Onion Language Server provides IDE features for `.on` files. It is used
by the VSCode extension (`vscode-onion/`) and can be integrated with any
editor that supports the Language Server Protocol.

## Starting the server

```bash
onion-lsp
```

The server communicates over stdin/stdout using LSP.

## Supported features

| Feature | Description |
|---------|-------------|
| Syntax diagnostics | Compilation errors reported as you type |
| Hover | Documentation for keywords, built-in types, and user-defined symbols |
| Go to definition | Jump to the definition of a user-defined symbol |
| Code completion | Keywords, built-ins, modules, and user-defined symbols |
| Document symbols | Outline view of classes, interfaces, methods, enums, records, and fields |
| Workspace symbols | Search symbols across all open documents |
| Signature help | Method signatures while typing a call |
| Rename symbol | Rename occurrences of the symbol at the cursor in the current file |
| Formatting | Normalises spacing, sharing its implementation with `onion fmt` |

### What rename does, and does not, do

Rename rewrites every **code** occurrence of the identifier in the open document. It skips
comments and string literals — renaming `count` no longer rewrites `"count of items"` or
`// count starts at zero` — while a `#{ … }` interpolation counts as code, so the `count`
in `"n=#{count}"` is renamed with the rest. Renaming from a position inside a comment or a
literal is refused: the word there is not a reference to anything.

It is **not scope-aware and not cross-file**. Two unrelated locals that happen to share a
name are renamed together, a local and a field of the same name are renamed together, and
references in other files are not touched at all. Scope-aware rename needs the typed AST
with real source ranges, and the parser currently produces spans only a token wide; that is
the work this depends on. Until then, review the edits your editor previews before applying
them.

### Formatting

The formatting provider calls the same code as `onion fmt`, so an editor and a CI check can
never disagree about what formatted means — a difference there shows up as a file that
changes every time it crosses between the two.

It tightens punctuation and converts leading tabs to spaces. It does not reindent, does not
move a line break, and does not collapse runs of spaces used for alignment; the reasoning
is in [the `onion fmt` section of the project CLI page](project-cli.md#onion-fmt).

The result is one edit replacing the whole document. A minimal diff would preserve editor
decorations better, but computing one from two strings risks producing an edit that does not
reconstruct the formatted text, and a formatter that corrupts a buffer on save is worse than
one that scrolls it. Before any edit is offered the formatted text is re-lexed and compared
token for token against the original; if they differ, no edit is offered at all.

## Symbol support

The language server indexes the following symbols from open documents:

- Classes, interfaces, enums, and records
- Methods (`def`)
- Class-level fields (`val` / `var`)
- Method arguments and local variables

Symbol information is used for completion, hover, go-to-definition, and both
document and workspace symbol requests.

## VSCode integration

Install the `vscode-onion` extension. It will automatically start
`onion-lsp` when you open an `.on` file and connect it to the editor.
