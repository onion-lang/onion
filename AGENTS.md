# Repository Guidelines

## Project Structure & Module Organization
- Core compiler code lives in `src/main/scala/onion/compiler`; pipeline stages (`Parsing`, `Rewriting`, `Typing`, `TypedGenerating`) now report through `CompilationOutcome` and share diagnostics via `CompilationReporter`.
- Tooling entry points are under `src/main/scala/onion/tools` (`CompilerFrontend`, `ScriptRunner`, `Shell`); JVM interop helpers remain in `src/main/java/onion`.
- The bytecode backend (`AsmCodeGeneration.scala`, `AsmCodeGenerationVisitor.scala`) centralises local-slot management, including captured-variable helpers for closures, so new emitters should reuse those utilities.
- Resources such as localized error messages sit in `src/main/resources`; integration fixtures are in `src/test/run`.
- Tests use ScalaTest (`AnyFunSpec`, `Diagrams`) and are grouped by feature in `src/test/scala/onion/compiler/tools`.

## Build, Test, and Development Commands
- `sbt compile` – compile both Scala and Java sources with the Scala 3 toolchain.
- `sbt test` – execute the ScalaTest suite, including the new failure-handling spec for `Shell`.
- `sbt run` – launch `onion.tools.CompilerFrontend` with interactive flags.
- `sbt runScript -- <file.on> [args…]` – run scripts through `ScriptRunner`.

## Coding Style & Naming Conventions
- Scala sources use two-space indentation, braced blocks, and `camelCase` for methods/values, `PascalCase` for classes/objects.
- Prefer `given`/`using` idioms and sum types (`CompilationOutcome`) over `null`; rely on `scala.util.Using` for resource safety.
- Java helpers mirror functional interfaces (`Function0`…`Function10`); keep them minimal and annotate with `@FunctionalInterface` when extending.
- Localized strings come from `Message`/`Systems`; avoid hard-coded literals outside resource bundles.

## Testing Guidelines
- Place new behavioural tests alongside existing specs under `src/test/scala/onion/compiler/tools`; follow the `<Feature>Spec` naming pattern.
- Capture failure scenarios with the structured reporter (`CompilationReporter`) and silence `System.err` within specs when asserting failures.
- Aim to cover both happy path (`Shell.Success`) and failure path (`Shell.Failure`) for new pipeline features.

## Commit & Pull Request Guidelines
- Follow the imperative, 50-character-or-fewer summary style used in `git log` (e.g., `Refactor parser`).
- Describe multi-file changes in the body, referencing modules (`Shell`, `CompilationOutcome`) and linking related issues.
- Prepare PRs with a short checklist: scope summary, tests executed (`sbt test`), any follow-up tasks, and screenshots/logs only when UX changes are involved.
