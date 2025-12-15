# Repository Guidelines

## Project Structure & Module Organization
- Compiler pipeline and IR live in `src/main/scala/onion/compiler` (`Parsing` → `Rewriting` → `Typing` → `TypedGenerating`), returning `CompilationOutcome` and reporting via `CompilationReporter`. Typing is split into pass modules under `src/main/scala/onion/compiler/typing` (e.g., `TypingOutlinePass`, `TypingBodyPass`, `TypingDuplicationPass`).
- CLI/tools are in `src/main/scala/onion/tools` (`CompilerFrontend`, `ScriptRunner`, `Shell`); Java helpers for JVM interop are in `src/main/java/onion`.
- Parser grammar is JavaCC: edit `grammar/JJOnionParser.jj` (not generated sources). Generated parser code lands under `target/scala-*/src_managed/main/java/onion/compiler/parser`.
- Samples are in `run/` (`*.on`), and user-facing docs are in `docs/` (MkDocs config: `mkdocs.yml`).
- Tests are ScalaTest and live in `src/test/scala`; script-style fixtures live in `src/test/run`.

## Build, Test, and Development Commands
- `sbt compile` – build the compiler; also regenerates the JavaCC parser if `grammar/JJOnionParser.jj` is newer.
- `sbt test` – run the full ScalaTest suite.
- `sbt "testOnly onion.compiler.tools.FunctionTypeSpec"` – run a focused spec while iterating.
- `sbt run` – run `onion.tools.CompilerFrontend` (main entrypoint).
- `sbt runScript -- run/Hello.on [args…]` – compile+run a script via `ScriptRunner`.
- `sbt dist` – build a distributable zip under `target/dist` (includes `bin/`, `run/`, and `onion.jar`).

## Coding Style & Naming Conventions
- Use 2-space indentation and the existing braced style; keep names `camelCase` (methods/vals) and `PascalCase` (types).
- Prefer Scala 3 idioms (`given`/`using`, enums/ADTs). Avoid introducing new `null` where feasible; prefer `Option`/sealed ADTs for new code, but keep consistency in legacy areas.
- Keep codegen changes localized to the ASM backend (`AsmCodeGeneration*.scala`) and reuse the local-slot/capture utilities there.
- Lint/refactor tooling is available via sbt-scalafix: use `sbt scalafix` if/when a `.scalafix.conf` is present for the change.

## Testing Guidelines
- Add/extend specs under `src/test/scala` using the existing `*Spec` naming pattern.
- For end-to-end behavior, prefer running `Shell`/`ScriptRunner` against small programs in `src/test/run` or `run/`.
- When changing parsing/typing/codegen, include at least one positive case and one failure case (assert via `CompilationOutcome.Failure` diagnostics).

## Commit & Pull Request Guidelines
- Follow existing commit subjects: `<Area>: <action>` (examples: `Parser: …`, `Typing: …`, `Docs: …`), one topic per commit.
- Include a short “how verified” note in the commit body (e.g., `sbt test`, or the specific `testOnly` you ran).
- PRs should summarize behavior changes, link issues/design notes when relevant, and mention updated docs/samples if syntax changes are involved.
