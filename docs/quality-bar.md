# Onion Practical-Quality Bar

"Practical quality" is intentionally vague, so this file pins it to a set of
**objectively measurable indicators**. Each row has a measurement you can run
and a threshold; the language has reached the bar when *every* row passes.

Baseline figures are the ground-truth values as of 2026-06-26 (develop @ 6a2d0e4).

| # | Dimension | How to measure | Current (2026-06-26) | Pass threshold |
|---|-----------|----------------|----------------------|----------------|
| 1 | Test suite | `SBT_OPTS="-Xmx2g" sbt -batch test` | 1191 pass / 0 fail | 0 failed, 0 skipped |
| 2 | Sample health | `SampleProgramsSpec` (compiles every `run/*.on`) | 36 / 36 compile | all compile, no rot |
| 3 | Large programs | count of `run/*.on` ≥ 100 lines that run end-to-end as-is | 5 (OrderReport, StatsApp, TodoManager, ShapeProcessor, TextAnalyzer) | ≥ 5 |
| 4 | Feature coverage | checklist below demonstrated inside the large samples | complete | every item ✓ |
| 5 | Known usability bugs | implemented-but-unreachable / broken features still open | 0 | 0 |
| 6 | Docs parity | `docs/guide` vs `docs/ja/guide` count + every code block compiles | 12 / 12 | parity + all blocks verified |
| 7 | Diagnostics | distinct `E00xx` codes with EN+JA messages | ~24 | every common error has a dedicated code |

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

None. The two previously tracked issues have been resolved:

1. **Primitive-type extensions** — fixed. `extension Int { def double(): Int = self * 2 }` and `(5).double()` now work. Extension methods on primitive receivers are registered under the boxed class name and the call target is unboxed before invoking the backing static method.
2. **Top-level function called from a class method** — fixed. Top-level `val`/`var` and functions are emitted as static members of the synthetic top-level class, and bare identifiers / unqualified calls in class methods fall back to these static members.
