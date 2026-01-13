# Repository Guidelines

## Project Structure & Module Organization
- `src/main/scala/onion/compiler/` holds the compiler pipeline (Parsing, Rewriting, Typing, ASM codegen).
- `src/main/java/onion/` provides runtime support (`IO`, `FunctionN`, collections helpers).
- `grammar/JJOnionParser.jj` is the JavaCC grammar; generated sources land under `target/`.
- `src/main/resources/` contains compiler resources (default static imports, messages).
- `src/test/scala/` contains ScalaTest/JUnit suites; `src/test/run/` has sample programs used by tests.
- `run/` hosts manual examples, `docs/` holds language/reference docs, `bin/` ships CLI scripts.

## Build, Test, and Development Commands
- `sbt compile` builds the compiler; regenerates the parser when the grammar changes.
- `sbt test` runs the full test suite.
- `sbt 'testOnly <SuiteName>'` runs a single spec (e.g., `GenericsInterfaceBridgeSpec`).
- `sbt repl` starts the Onion REPL; `sbt 'runScript path/to/app.on [args]'` runs a script.
- `bin/onionc` compiles to class files; `bin/onion` compiles and runs in-memory.
- `sbt dist` produces a distribution ZIP under `target/`.

## Coding Style & Naming Conventions
- Scala uses Scala 3 indentation style (2 spaces, no tabs, minimal braces); match surrounding style.
- Java files use 4-space indentation and standard brace placement.
- Types/classes use UpperCamelCase; methods/values use lowerCamelCase.
- Test suites end with `Spec` (e.g., `TypingSpec.scala`).

## Testing Guidelines
- ScalaTest is the primary framework; a few JUnit tests exist alongside it.
- Add regression tests for compiler behavior and update `run/` samples when syntax changes.
- Prefer targeted suites for quick checks, then run `sbt test` before submitting.

## Commit & Pull Request Guidelines
- Commit messages are short, imperative summaries (e.g., “Add …”, “Fix …”, “Refactor …”); phase tags appear occasionally.
- PRs should include a clear purpose, tests run, and doc/sample updates for user-facing changes.

## Notes for Contributors
- Do not edit generated parser sources under `target/`; update `grammar/JJOnionParser.jj` instead.
- See `CLAUDE.md` and `docs/` for architecture details and deeper design notes.
