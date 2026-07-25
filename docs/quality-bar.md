# Onion Practical-Quality Bar

"Practical quality" is intentionally vague, so this file pins it to a set of
**objectively measurable indicators**. Each row has a measurement you can run
and a threshold; the language has reached the bar when *every* row passes.

Baseline figures are the ground-truth values as of 2026-07-26 (develop @ f6cf5e83),
**re-measured** rather than carried forward — the previous baseline (2026-06-26 @ 6a2d0e4)
had drifted badly enough to be misleading: it recorded 1193 tests against an actual 2445,
36 samples against 59, and ~24 diagnostic codes against 74.

| # | Dimension | How to measure | Current (2026-07-26) | Pass threshold |
|---|-----------|----------------|----------------------|----------------|
| 1 | Test suite | `sbt -batch -Duser.language=en test` | 2445 pass / 0 fail / 1 cancelled | 0 failed, 0 skipped |
| 2 | Sample health | `SampleCompilesSpec` / `SampleProgramsSpec` (both compile every `run/*.on`) | 59 / 59 compile | all compile, no rot |
| 3 | Large programs | count of `run/*.on` ≥ 100 lines that run end-to-end as-is | 5 (OrderReport, ShapeProcessor, StatsApp, TextAnalyzer, TodoManager) | ≥ 5 |
| 4 | Feature coverage | checklist below demonstrated inside the large samples | complete | every item ✓ |
| 5 | Known usability bugs | implemented-but-unreachable / broken features still open | 1 ([#374](https://github.com/onion-lang/onion/issues/374)) | 0 |
| 6 | Docs parity | `docs/guide` vs `docs/ja/guide` count + every code block compiles | 13 / 13 | parity + all blocks verified |
| 7 | Diagnostics | distinct `E00xx` codes with EN+JA messages | 74 | every common error has a dedicated code |

**Do not set `SBT_OPTS`.** The previous version of this file recommended
`SBT_OPTS="-Xmx2g"`, which now *lowers* the heap below the project's own default of 4g and
makes the suite die with an `OutOfMemoryError` partway through. The default is correct.

`-Duser.language=en` matters: error messages are bilingual and resolved from the JVM
default locale, so a test asserting on message text passes in a Japanese locale and fails
in release CI, which runs in English. Assert on error **codes**, not localized text.

The one cancelled test is the distribution smoke test, gated behind `-Donion.dist.path`.

**Practical quality is reached when rows 1–7 all pass.** This turns the open-ended
goal "reach practical quality" into a checkable state.

## Row 4 — feature coverage checklist

A feature counts as covered once it runs inside at least one large sample
(`run/`), not just a micro-test:

- [x] records (plain) and data-carrying enums
- [x] plain enums
- [x] classes with constructors and methods
- [x] interfaces + polymorphic dispatch (ExprEval)
- [x] top-level `def` with block and expression bodies
- [x] recursion (incl. tail position)
- [x] collection pipelines: map / filter / fold / reduce / sortedBy / groupBy / find / distinct / partition / zip / flatten
- [x] `select` / pattern matching
- [x] `if` / `else if` expressions
- [x] `while`, `foreach` over ranges and over `Map` (k, v)
- [x] nullable types with null checks
- [x] closures stored in vals
- [x] string interpolation `#{}`
- [x] try / catch
- [x] generics used non-trivially in a large sample (`StatsApp` — `SafeBox[T]`, `Pair[A,B]`, generic `countMatches`)
- [x] extension methods used in a large sample (`StatsApp`, `ShapeProcessor`, `TextAnalyzer`, `TodoManager`)

## Row 5 — currently open usability bugs (tracked)

1. **Constant narrowing does not reach constructor arguments.** `val b: Byte = 100`
   narrows, but `new R(..., -3)` against a `Short` component is `E0021`
   ("constructor applicable for R(..., Int) is not found") and needs `(-3 as Short)`.
   Found while writing `ScalarConversionSpec`; it will be hit again by anything that
   builds a record out of parsed components. ([#374](https://github.com/onion-lang/onion/issues/374))

Previously tracked and resolved:

1. **Primitive-type extensions** — fixed. `extension Int { def double(): Int = self * 2 }` and `(5).double()` now work. Extension methods on primitive receivers are registered under the boxed class name and the call target is unboxed before invoking the backing static method.
2. **Top-level function called from a class method** — fixed. Top-level `val`/`var` and functions are emitted as static members of the synthetic top-level class, and bare identifiers / unqualified calls in class methods fall back to these static members.
