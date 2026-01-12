# Onion Language for VS Code

Language support for the [Onion programming language](https://github.com/kmizu/onion).

## Features

- **Syntax Highlighting** - Full syntax highlighting for Onion source files (`.on`)
- **Error Diagnostics** - Real-time compile error detection
- **Code Completion** - Auto-completion for keywords, types, and standard library
- **Hover Information** - Documentation on hover for keywords, types, and modules
- **Document Outline** - Navigate through classes, interfaces, methods, and enums

## Requirements

- **Java 17+** - Required to run the Onion language server
- **Onion** - The Onion language distribution must be installed

### Setting up ONION_HOME

Set the `ONION_HOME` environment variable to point to your Onion installation directory:

```bash
# Unix/Linux/macOS
export ONION_HOME=/path/to/onion

# Windows
set ONION_HOME=C:\path\to\onion
```

Alternatively, configure `onion.serverPath` in VS Code settings to point directly to the `onion-lsp` executable.

## Extension Settings

This extension contributes the following settings:

- `onion.serverPath`: Path to the Onion language server (`onion-lsp`). Leave empty to use `ONION_HOME/bin/onion-lsp`.
- `onion.trace.server`: Traces the communication between VS Code and the language server (`off`, `messages`, `verbose`).

## Commands

- **Onion: Restart Language Server** - Restarts the language server

## Supported Language Features

| Feature | Status |
|---------|--------|
| Syntax Highlighting | Supported |
| Error Diagnostics | Supported |
| Code Completion | Supported |
| Hover Documentation | Supported |
| Document Symbols | Supported |
| Go to Definition | Basic |

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

BSD-3-Clause - See the main Onion repository for full license details.
