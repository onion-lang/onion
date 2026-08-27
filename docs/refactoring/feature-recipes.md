# Compiler feature recipes

These recipes describe the extension seams the maintainability program intends
to preserve. Commands use `sbt --server` on hosts where sbt's native client is
unavailable; ordinary environments may use `sbt` directly.

## Add a friendly parser hint

1. Identify the owning family. Unsupported `switch` / `when` / `match` /
   `default` / `elif` / `unless` / `except` forms belong to
   `ControlFlowSyntaxHints`; other rules remain in `SyntaxHintClassifier` until
   a cohesive family boundary is justified.
2. Add a failing literal case to the owning direct spec. Add a
   `SyntaxHintClassifierSpec` case when the rule can collide across families.
3. State which broader rule or family the new case must precede.
4. Add the message key to both `errorMessage.properties` bundles.
5. Add or update one end-to-end `*HintSpec` for the real malformed source.
6. Add an i18n test when the hint has new text or arguments.
7. Implement the smallest pattern and ordered branch in the owning family.
8. Mutate its key or priority locally and confirm the focused test fails.
9. Run focused tests and both locale `testFull` suites.

Do not edit generated JavaCC sources. Change
`grammar/JJOnionParser.jj` only when accepted syntax changes; a hint for rejected
foreign syntax usually needs no grammar change.

## Change accepted syntax

1. Add parser tests for success, rejection boundaries, locations, and recovery.
2. Edit `grammar/JJOnionParser.jj`.
3. Update `ASTBuilder` only if the grammar's semantic value changes.
4. Add rewriting or typing tests before downstream changes.
5. Run `sbt --server compile` to regenerate parser output under `target/`.
6. Confirm generated files are not tracked.
7. Run parser, recovery, samples, English, and Japanese suites.

## Add a rewriting transformation

1. Write the accepted input AST and promised output invariant.
2. Add a characterization test with hand-built or parsed input and literal
   expected AST behavior.
3. Add ordering tests for adjacent transformations.
4. Implement a named pass with no parser, typing, or tool dependency.
5. Wire it into `Rewriting` without changing other pass order.
6. Mutate or remove the transformation and confirm the test fails.
7. Run rewriting, typing, codegen, sample, and full locale suites.

## Add a typing rule

1. Identify the owning pass: header, outline, body, or duplication.
2. Add a focused compile-success or exact-diagnostic test first.
3. Reuse the narrowest helper capability; do not add a general accessor to
   `Typing` unless multiple consumers require the same coherent capability.
4. Cover generic, nullable, primitive/boxed, and error-cascade boundaries when
   relevant.
5. Run the focused suite, `PipelineRunnerSpec`, codegen execution tests, and
   both full locale suites.

## Add a diagnostic

1. Decide whether it is parse, semantic, warning, or internal.
2. Preserve source file, line, column, span, code, and locale as separate
   contract dimensions.
3. Add message resources in English and Japanese together.
4. Test rendering through `DiagnosticRenderer` when caret or span behavior is
   involved.
5. Ensure a failed speculative typing path does not leak duplicate diagnostics.

## Add a compiler phase

1. Define a narrow `CompilerPhase[In, Out]` adapter.
2. Add a `PipelineRunnerSpec` case for order, timing, short-circuiting, and
   diagnostic behavior.
3. Extend `CompilationPhases` and `defaultPhases` in one commit.
4. Decide which debug artifact, if any, belongs in `PhaseContext`.
5. Update compile-profile and architecture docs.
6. Run the full compiler and tool suites.

## Change ASM code generation

1. Keep pipeline code dependent on `AsmBackend`, never the implementation.
2. Add a source-level program that exercises the bytecode behavior.
3. Assert runtime output or JVM verification, not only byte-array existence.
4. Cover bridge, closure capture, stack control flow, and debug metadata when
   the change touches them.
5. Run `CodegenCorrectnessSpec`, relevant focused suites, samples, and both
   locale full suites.

## Before committing any compiler refactor

- Focused RED was observed for the intended missing behavior or seam.
- Focused GREEN and mutation sensitivity were observed.
- Public behavior and diagnostics remain unchanged unless explicitly scoped.
- `python3 scripts/maintainability/test_audit.py` passes.
- `git diff --check` passes.
- No generated or machine-local files are tracked.
- The decision log and current architecture are updated when a boundary changes.
