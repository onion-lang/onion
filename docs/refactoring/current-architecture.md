# Current compiler architecture

This document describes the implementation at
`78973f25228d7f3009e210fb27e5b96054f7b4ac`. Source code and executable tests
take precedence when older design documents disagree.

## End-to-end flow

```text
InputSource
  -> SourceHandle adapter
  -> ParsingPhase
  -> RewritingPhase
  -> TypingPhase
  -> TailCallOptimization
  -> MutualRecursionOptimization
  -> BytecodeGenerationPhase
  -> LawCheckPhase
  -> CompilationResult
```

`OnionCompiler` is a thin public facade. It converts legacy `InputSource`
values, builds the default phases, and delegates to `PipelineRunner`.
`PipelineRunner` owns ordering, timing, short-circuiting, diagnostic capture,
warning promotion, and internal-error conversion. `PhaseContext` accumulates
diagnostics and optional parsed, rewritten, and typed debug artifacts.

## Phase boundaries

| Boundary | Adapter | Current implementation |
|---|---|---|
| Source | `source.SourceHandle` / `InputSourceAdapter` | Legacy `InputSource` compatibility |
| Parsing | `parser.ParsingPhase` | Root `Parsing` processor and generated JavaCC parser |
| Rewriting | `rewrite.RewritingPhase` | Root `Rewriting` processor |
| Typing | `typing.TypingPhase` | Root `Typing` facade plus `typing/*` passes and session state |
| Bytecode | `backend.BytecodeGenerationPhase` | `TypedAstCodeGeneration` -> `AsmBackend` -> ASM implementation |
| Verification | `verification.LawCheckPhase` | Generated-class law/example execution |

The adapters give the pipeline stable names, but parsing and rewriting still
delegate into root-package processors whose internal responsibilities are
large.

## Parsing

`grammar/JJOnionParser.jj` is the syntax authority and generates parser sources
under `target/`. `Parsing.scala` currently owns all of these concerns:

- source reading and shebang removal;
- JavaCC construction and recovery configuration;
- conversion of collected and terminal parse errors;
- source offset, line, and context extraction;
- expected-token rendering;
- more than thirty regular-expression-based friendly syntax hints;
- localization lookups for those hints.

The friendly hints dominate recent edits to the file. They are deterministic
classification policy and do not need parser lifecycle access, which makes them
a clean extraction boundary.

## Rewriting

`Rewriting.scala` is the largest behavior-heavy hotspot. It contains many AST
normalizations and language-feature lowerings in one processor. Its recent
co-changes with `AST.scala`, `SemanticError*`, and `TypingOutlinePass.scala`
show that some transformations cross semantic boundaries. It should be split
by explicit pass contracts only after characterization tests identify each
pass's input/output invariants.

## Typing

`Typing.scala` is now primarily a facade over four passes:

1. `TypingHeaderPass`
2. `TypingOutlinePass`
3. `TypingBodyPass`
4. `TypingDuplicationPass`

State is grouped into `TypingSession`, `TypingGlobalState`, and
`TypingUnitContext`, and helpers under `typing/` handle name resolution,
overloads, construction, calls, control expressions, and diagnostics. This is
a substantial improvement over one monolith, but many helpers still depend on
the concrete `Typing` facade and mutable session accessors. The next boundary
work here is interface narrowing, not another broad physical move.

## Typed AST and backend

`TypedAST.scala` is the largest source file, but the history audit shows only
two recent commits and low churn. It is primarily a definition hub, so its size
alone does not justify the first refactor.

The backend already has a useful public direction:

```text
pipeline -> BytecodeGenerationPhase -> TypedAstCodeGeneration
         -> AsmBackend -> backend.asm.AsmCodeGeneration
```

The ASM implementation and visitor remain large, but a stable facade makes
incremental emitter extraction possible without changing pipeline clients.

## Diagnostics and tools

Parser errors still use legacy `CompileError` values while pipeline diagnostics
are collected into `DiagnosticBag`. `DiagnosticRenderer` centralizes human
rendering, AST dumps, and typed-AST summaries. Compiler CLI, script runner,
REPL, project CLI, formatter, documentation tool, and LSP consume the compiler
through a mixture of `compileDetailed` and narrower helpers.

The diagnostic path is behaviorally well covered, especially for English and
Japanese parser hints. That coverage is a valuable refactoring oracle.

## Tests and CI

- `testFull` currently executes 4,132 tests across 567 suites.
- The default phase order and failure short-circuit are covered by
  `PipelineRunnerSpec`.
- Parser hint behavior is covered by focused end-to-end and localization
  suites, but the classifier has no direct pure unit-test seam yet.
- `Test / parallelExecution := false` protects global output-capture behavior.
- `.github/workflows/scala.yml` runs `sbt -v +test`, not the documented English
  and Japanese `testFull` quality gate.
- The release workflow runs `sbt test`, assembly, and distribution tasks on
  JDK 21.

## Documentation contradictions

- `docs/design/typed-boundaries.md` still calls major features proposed or not
  implemented, while its later sections and the current compiler show that
  tools, effects, Shapes, and typed boundaries shipped.
- `docs/parser-refactoring.md` describes a desired parser/AST-builder split as
  if it were the complete current architecture; `Parsing.scala` still owns
  error policy and hint classification directly.
- compiler architecture docs mention `StatementTyping.scala`, which no longer
  exists after expression-oriented block lowering.
- contributor docs recommend `sbt test`, while the quality bar defines fresh
  English and Japanese `testFull` runs.
- CI and the quality-bar command are therefore not yet the same contract.

These files are historical evidence, not higher authority than current source
and tests. Contradictions should be corrected in small documentation commits as
the target architecture lands.
