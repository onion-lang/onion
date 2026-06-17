# Onion Practical-Quality Bar

"Practical quality" is intentionally vague, so this file pins it to a set of
**objectively measurable indicators**. Each row has a measurement you can run
and a threshold; the language has reached the bar when *every* row passes.

Baseline figures are the ground-truth values as of 2026-06-17 (develop @ f4ad44b).

| # | Dimension | How to measure | Current (2026-06-17) | Pass threshold |
|---|-----------|----------------|----------------------|----------------|
| 1 | Test suite | `SBT_OPTS="-Xmx2g" sbt -batch test` | 1175 pass / 0 fail | 0 failed, 0 skipped |
| 2 | Sample health | `SampleProgramsSpec` (compiles every `run/*.on`) | 31 / 31 compile | all compile, no rot |
| 3 | Large programs | count of `run/*.on` ≥ 100 lines that run end-to-end as-is | 2 (OrderReport 118, ExprEval 98) | ≥ 5 |
| 4 | Feature coverage | checklist below demonstrated inside the large samples | partial | every item ✓ |
| 5 | Known usability bugs | implemented-but-unreachable / broken features still open | 2 | 0 |
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
- [ ] generics used non-trivially in a large sample
- [ ] extension methods used in a large sample

## Row 5 — currently open usability bugs (tracked)

1. **Primitive-type extensions** — `extension Int { def double(): Int = self * 2 }`
   then `(5).double()` raises E0005; `extension String` works. Root cause not yet
   confirmed (the boxed-FQCN-registration hypothesis was empirically falsified).
2. **Top-level function called from a class method** — `helper(21)` inside a class
   method raises E0005. The clean fix is to make top-level `val`/`var` and
   functions all static (the static field/method TypedAST nodes already exist);
   the remaining blocker is that a bare identifier in a static function body does
   not fall back to a static field.

When both reach 0 and rows 3/4 are met, the bar is cleared.
