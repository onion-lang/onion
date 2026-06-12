# Onion Language for VS Code

Language support for the [Onion programming language](https://github.com/kmizu/onion).

## Features

- **Syntax Highlighting** — Full TextMate-based highlighting for Onion source
  files (`.on`): keywords, types, strings with `#{}` interpolation, numbers,
  operators (including `as`, `?.`, `?:`, `!!`, `<:`, ranges), `select`/`case
  is`/`when`, `do`/`ret` notation, records, enums, and more.
- **Editor Configuration** — Comment toggling, bracket matching, auto-closing
  pairs, and indentation rules.

Syntax highlighting and editor configuration work out of the box with no
external dependencies — just install the extension and open a `.on` file.

### Optional: Language Server

The extension can also talk to an Onion language server (`onion-lsp`) for
richer features (diagnostics, completion, hover, outline) **if one is
installed**. The language server is not bundled and is still in development; the
extension detects its absence and runs in highlighting-only mode without
errors. When an `onion-lsp` executable is available it is picked up
automatically (see configuration below).

## Configuration

- `onion.serverPath`: Path to the Onion language server (`onion-lsp`). Leave
  empty to auto-discover via `ONION_HOME/bin/onion-lsp` or common install
  locations.
- `onion.trace.server`: Trace VS Code ↔ server communication (`off`,
  `messages`, `verbose`).

To enable the optional server, set `ONION_HOME` (so `$ONION_HOME/bin/onion-lsp`
resolves) or point `onion.serverPath` at the executable directly.

## Commands

- **Onion: Restart Language Server** — Restarts the language server (or reports
  that none was found).

## Feature Status

| Feature | Status |
|---------|--------|
| Syntax Highlighting | ✅ Supported |
| Editor Configuration (brackets, comments, indent) | ✅ Supported |
| Error Diagnostics | ⏳ Requires `onion-lsp` (in development) |
| Code Completion | ⏳ Requires `onion-lsp` (in development) |
| Hover Documentation | ⏳ Requires `onion-lsp` (in development) |
| Document Symbols / Outline | ⏳ Requires `onion-lsp` (in development) |

## Development

### Building the Extension

```bash
cd vscode-onion
npm install
npm run compile
```

### Running in Development Mode

1. Open the `vscode-onion` folder in VS Code
2. Press F5 to launch the Extension Development Host
3. Open a `.on` file to test the extension

### Packaging

```bash
npm run package
```

This creates a `.vsix` file that can be installed in VS Code.

## License

BSD-3-Clause — See the main Onion repository for full license details.
