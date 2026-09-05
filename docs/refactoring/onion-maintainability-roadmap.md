# Onion maintainability roadmap

This is an execution roadmap, not a promise to perform a big-bang rewrite.
Every phase ends in a green, independently revertible state.

## Phase 0: baseline, audit, and hygiene

Status: complete. The baseline, audit, six handoff documents, and hygiene-only
change landed before production compiler edits.

### Task 0.1: record the baseline

Files:

- Create `docs/refactoring/baseline.md`.
- Create `docs/refactoring/current-architecture.md`.
- Create `docs/refactoring/target-architecture.md`.
- Create this roadmap, `decision-log.md`, and `feature-recipes.md`.

Verification:

```text
sbt --server -Duser.language=en testFull
sbt --server -Duser.language=ja testFull
sbt --server compile
git diff --check
```

### Task 0.2: add a repeatable audit

Files:

- Create `scripts/maintainability/audit.py`.
- Create `scripts/maintainability/test_audit.py`.

The audit reads tracked Scala and Java main sources, emits deterministic JSON
and Markdown, and reports LOC, definition and branch proxies, package fan-in and
fan-out, recent commit count, churn, and co-change pairs.

Verification:

```text
python3 scripts/maintainability/test_audit.py
scripts/maintainability/audit.py --output-dir target/maintainability
```

### Task 0.3: remove editor debris

Files:

- Delete tracked `project/.build.properties.un~`.
- Add `*.un~` to `.gitignore`.

Verification:

```text
git ls-files | rg '\.un~$'
sbt --server compile
git diff --check
```

Commit this phase without production compiler changes.

## Phase 1: focused safety rails

Status: complete for the parser-hint slice. The direct classifier suite was
observed failing on the missing API before implementation. Disabling the
extension-declaration priority branch then failed its focused collision case,
which demonstrates mutation sensitivity.

### Task 1.1: characterize syntax-hint classification

Files:

- Add `src/test/scala/onion/compiler/parser/SyntaxHintClassifierSpec.scala`.
- Do not change `Parsing.scala` until the new test has failed for the expected
  missing classifier API.

Use a literal table covering priority collisions and parameter capture:

- extension declaration before primitive static-call advice;
- foreach-parentheses before generic old-for-in advice;
- destructuring-for before generic C-style-for advice;
- method before constructor where both prefixes are close;
- reserved-word and missing-block fallbacks;
- no match returns `None`.

Mutation check: reversing each documented priority pair or changing one message
key must fail a focused case.

### Task 1.2: keep end-to-end diagnostic oracles

Run the existing parser-hint and i18n suites before extraction. Record the
exact focused suite list in the phase commit message. No golden output is
rewritten during a pure move.

## Phase 2: first production extraction

Status: complete on this branch. `Parsing` now delegates locale-independent
hint selection to `parser.SyntaxHintClassifier` and retains source context,
expected-token rendering, localization, recovery, and final error assembly.

### Task 2.1: extract the pure classifier

Files:

- Add `src/main/scala/onion/compiler/parser/SyntaxHintClassifier.scala`.
- Update `src/main/scala/onion/compiler/Parsing.scala`.
- Update `src/test/scala/onion/compiler/parser/SyntaxHintClassifierSpec.scala`.

Implementation:

- Move syntax-hint patterns and ordered classification out of `Parsing`.
- Return `Option[SyntaxHint]`, containing the message key and zero to two
  literal arguments.
- Keep localized `Message` rendering and base parse-error assembly in
  `Parsing`.
- Preserve match order exactly.
- Do not change regexes, message keys, locations, expected-token formatting,
  or parser recovery in this task.

Verification:

```text
sbt --server 'testOnly onion.compiler.parser.SyntaxHintClassifierSpec'
sbt --server 'testOnly onion.compiler.tools.*HintSpec onion.compiler.*HintI18nSpec'
sbt --server -Duser.language=en testFull
sbt --server -Duser.language=ja testFull
git diff --check
```

Rollback: revert the extraction commit; no grammar, resource bundle, or public
API changes are involved.

### Landed evidence

- The audit moved `Parsing.scala` from 744 LOC / 158 branch proxies to 263 / 39.
  The cohesive classifier is 170 LOC / 78 branch proxies.
- The old and new pattern-name sets and message-key multisets match exactly;
  no resource, grammar production, or generated parser file changed. The
  grammar file has only a comment updated to name the extracted classifier.
- The direct suite covers eight cases, including the documented priority
  collisions, captured arguments, method/constructor distinction, fallbacks,
  and no-match behavior. Existing hint and i18n suites add 196 end-to-end
  cases.
- Fresh English and Japanese full runs each completed with 4,140 passed,
  0 failed, and the same single opt-in distribution cancellation as baseline.
- Two default 25-iteration readiness pairs and one 25-warmup/25-measurement
  pair showed no systematic material regression. A reverse-order warm pair
  put the 20-file corpus at +4.15%, process-cold Hello at +1.63%, and the
  initially noisy Todo workload at +9.08%. Small in-process scenarios moved in
  both directions as JIT state changed; generated-class counts stayed equal.
- Base and extracted compilers emitted byte-identical `TodoManager.on` output:
  seven class files and 10,953 bytes in both trees.

Raw benchmark and full-suite reports remain under ignored `target/` paths and
are not repository artifacts.

## Phase 3: parser diagnostics follow-ups

Only start if the Phase 2 audit still ranks parser diagnostics as active debt.

1. [x] Split source-context calculation into a pure `SourceContext` helper.
2. [x] Split terminal expected-token rendering into a pure
   `ExpectedTokenFormatter` helper.
3. [ ] Group syntax hints by language-family mistake only if classifier tests keep
   priority visible across groups.
   - [x] Re-audit after active hint development and extract the contiguous
     unsupported control-flow family into `ControlFlowSyntaxHints`.
   - [ ] Extract another family only when churn and a closed priority interval
     justify it; do not create one-rule files or reorder the global chain.
4. [x] Update parser architecture docs for each extracted diagnostic boundary.

Each item uses RED/GREEN/refactor and is a separate commit.

## Phase 4: rewriting pass decomposition

1. Generate a call and co-change map for `Rewriting.scala`.
2. Select one transformation with a closed input/output invariant.
3. Add characterization tests comparing full rewritten AST output.
4. Extract one named pass without changing order.
5. Prove a mutation in the pass is caught.
6. Repeat only while fan-out or churn falls measurably.

Candidate files are determined after Phase 2; no speculative class names are
committed before the call graph is read.

### First slice: ADT enum lowering

Base: `c9e77b96`. The 200-commit audit reports `Rewriting.scala` at 1,688
lines / 392 branch proxies, with two recent edits (126 lines of churn).
Those two edits touch only this file, so they provide no recent co-change
evidence for separating stateful body rewriting. Parser hint development is
still active (44 edits / 522 lines of churn); further hint grouping remains
optional, not a prerequisite for this independent Phase 4 slice.

The inspected call path is `rewrite -> desugarAdtEnum ->
rewriteToplevelDeclaration -> rewriteInterfaceDeclaration / rewriteRecordDeclaration`.
The lowering itself reads only its enum argument, constructs AST nodes, and
can throw a `CompilationException`. It does not read dictionary scope,
body-rewrite flags, counters, configuration, or compiler services.

1. Characterize complete compilation-unit ASTs: product/singleton order,
   generic application and bounds, metadata and positions, method filtering,
   generated-body rewriting, homogeneous passthrough, and error provenance.
2. Remove the sealed bit temporarily and confirm the characterization fails;
   restore it before extraction.
3. Move only the lowering into `rewrite.AdtEnumLowering`; retain dispatch,
   subsequent rewriting, and source-file diagnostic enrichment in `Rewriting`.
4. Run focused enum/rewriting tests and fresh English/Japanese `testFull`.
5. Review the diff and update boundary documentation. This move adds no
   traversal and does not modify the existing body-rewrite fast path.

Rollback is a revert of this slice; no grammar, runtime, or public API changes.

Verification (2026-09-05): the first slice is complete. The five full-AST
characterizations passed before extraction; removing the sealed bit failed
three, and all passed again after restoration and extraction. The focused
suite passed 75 tests. Fresh English and Japanese JVMs each passed 5,230 tests
in 714 suites (zero failures; one opt-in distribution smoke cancellation).
`run/AdtExpr.on` emitted eight byte-identical class files totaling 10,545 bytes
before/after, and the generated program returned `-15`. The audit test,
`git diff --check`, strict MkDocs build, and independent review passed.

`Rewriting.scala` moved from 1,688 lines / 392 branch proxies to 1,610 / 371;
the new boundary is 87 / 21. These are responsibility-localization metrics,
not evidence of less total complexity: package fan-out for the root compiler
package rises from 11 to 12 because it now explicitly imports `rewrite`.
No runtime speedup or measured performance improvement is claimed. Further
extractions still require their own call-map and invariant evidence.

## Phase 5: typing dependency narrowing

1. Inventory every helper that receives concrete `Typing`.
2. Group required capabilities by name resolution, diagnostics, type mapping,
   or session state.
3. Introduce the smallest capability trait under `typing/` with a consumer
   test first.
4. Migrate one helper family at a time.
5. Reject new broad `Typing` accessors in review and architecture tests.

Semantic typing behavior remains covered by focused suites plus both full
locale runs.

## Phase 6: backend emitter boundaries

1. Measure `AsmCodeGeneration` and visitor responsibilities.
2. Characterize bytecode through verifier and runtime behavior.
3. Extract one emitter family behind `AsmBackend`.
4. Run codegen correctness, closure, bridge, control-flow, and full suites.
5. Compare representative class dumps or byte arrays where deterministic.

## Phase 7: warnings, CI, and performance

1. Fix baseline Scala, Java, and test warnings in narrow commits.
2. Decide whether JavaCC choice warnings are removable or explicitly accepted.
3. Align `.github/workflows/scala.yml` with fresh English and Japanese
   `testFull` runs on the supported JDK.
4. Exercise the opt-in distribution journey in an appropriate workflow stage.
5. Add the maintainability audit test to CI without checking in volatile audit
   output.
6. Record before/after compile and representative compiler-profile data using
   the existing readiness protocol.

## Phase 8: documentation convergence

1. Update compiler architecture docs to the landed phase boundaries.
2. Mark historical design documents explicitly historical or update their
   status blocks.
3. Make contributor test commands match CI and the quality bar.
4. Update `feature-recipes.md` after every new extension seam proves usable.

## Commit and review policy

- One concern per commit.
- Test-first evidence in the commit or PR description.
- No generated parser sources or local audit output are tracked.
- Every phase reports focused tests, English/Japanese full suites, invariants,
  and rollback.
- Push and PR creation occur only after the current bounded phase is verified.
