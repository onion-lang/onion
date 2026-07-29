# Roadmap — v0.6 to v0.8

Status: **shipped.** Every issue below (#346-#364) is closed, and the current release
(v0.10.9) already implements `Shape[T]`, `Outcome`/`Defect`, `tool` declarations with
effect inference and `--plan`, and lossless shapes with `edit`/`render` — see
[CLAUDE.md](../../CLAUDE.md) ("Tools, capabilities and effects") and
[docs/reference/stdlib.md](../reference/stdlib.md) for the syntax as it exists today.
This page is kept as a historical record of the plan and its rationale; a few line
items shipped with different scope than originally planned (e.g. #351's `zip` combinator
was not deferred after all — see its issue for what changed).

The roadmap itself used to live on GitHub, updated as work landed; this page exists so a
reader not looking at the issue tracker can see the shape of the plan and follow it to
the detail.

- Why: [onion-v0.5-audit.md](onion-v0.5-audit.md) — what v0.5 is, with citations
- What: [typed-boundaries.md](typed-boundaries.md) — the design thesis
- When: the milestones below

| Milestone | Theme | Issues |
|---|---|---|
| [v0.6](https://github.com/onion-lang/onion/milestone/17) | One boundary, one failure | 10 |
| [v0.7](https://github.com/onion-lang/onion/milestone/18) | Contracts and capabilities | 6 |
| [v0.8](https://github.com/onion-lang/onion/milestone/19) | Reversible | 3 |

Each issue carries the same fields: user value, differentiation, dependencies, affected
modules, complexity, migration risk, test strategy, docs impact, demo.

---

## v0.6 — One boundary, one failure

The boring foundation. Onion has four boundary mechanisms that share nothing and five
ways to report the same failure (audit §2, §3); this milestone replaces them with one
declarative data boundary and one failure channel that knows where things came from.

Verification is fixed **first**, deliberately. The headline claim is that
`parse ∘ print == id` is machine-checked at build time — but today a law whose parameter
type has no generator is skipped with no diagnostic and no test coverage
(`LawCheckPhase.scala:80-81`). A verification that can silently not run produces unearned
confidence, so it is a prerequisite rather than a cleanup.

| # | Item |
|---|---|
| [#346](https://github.com/onion-lang/onion/issues/346) | law/example verification must never silently skip |
| [#347](https://github.com/onion-lang/onion/issues/347) | `Origin` — a runtime type for where a value came from |
| [#348](https://github.com/onion-lang/onion/issues/348) | `Outcome[T]` and `Defect` — one failure channel, with accumulation |
| [#349](https://github.com/onion-lang/onion/issues/349) | one scalar conversion registry (collapse five duplicated switches) |
| [#350](https://github.com/onion-lang/onion/issues/350) | `Shape[T]` and named `shape` declarations |
| [#351](https://github.com/onion-lang/onion/issues/351) | Shape combinators — `lines`, `sepBy`, `xmap`, `orElse` (`zip` deliberately deferred — see below) |
| [#352](https://github.com/onion-lang/onion/issues/352) | json/yaml shapes, retiring `derive!` (`csv` deliberately deferred — see below) |
| [#353](https://github.com/onion-lang/onion/issues/353) | resource literals take a shape; `Cli` stops calling `System.exit` |
| [#354](https://github.com/onion-lang/onion/issues/354) | fix the positioning — README, EN/JA parity, quality-bar baseline |
| [#355](https://github.com/onion-lang/onion/issues/355) | broken-log demo, guides, examples, crash corpus |

Rough order: #346 → #347 → #348 → #349 → #350 → #351 → {#352, #353} → {#354, #355}.

Both #351 and #352 shipped narrower than this table originally described, by deliberate
choice made while implementing them, not by oversight: a shape-level `zip` over two *text*
shapes has no rule for where one component's text ends and the next begins without a
delimiter (`sepBy` is that rule; see typed-boundaries.md §5.3), and a `csv` shape is a
*row* shape needing a decision about the header row that `json`/`yaml` do not, so it is
better designed against a concrete demo than guessed at in isolation (PR #379). Neither
has an open tracking issue as of this writing.

**Demo:** 1,000 access-log lines, 5 malformed. You get 995 typed values *and* 5 defects
with line numbers. Today `parseAll` returns 995 values and no trace the other 5 existed.

## v0.7 — Contracts and capabilities

The differentiator. `Shape` types the data crossing the boundary; capabilities type the
effects crossing it. One `tool` declaration derives its CLI, a machine-readable contract,
a statically checked capability set, and a `--plan` dry run.

| # | Item |
|---|---|
| [#356](https://github.com/onion-lang/onion/issues/356) | effect table and `Method`-level effect access |
| [#357](https://github.com/onion-lang/onion/issues/357) | `tool` declaration, effect inference, capability checking |
| [#358](https://github.com/onion-lang/onion/issues/358) | machine-readable contract, and derive the CLI from it |
| [#359](https://github.com/onion-lang/onion/issues/359) | `--plan`, a dry run derived from the effect set |
| [#360](https://github.com/onion-lang/onion/issues/360) | narrow the default static import set |
| [#361](https://github.com/onion-lang/onion/issues/361) | agent-callable tool demo |

#360 is independent of the rest and is the highest-blast-radius change in the roadmap;
it is kept separate for that reason.

**Demo:** an agent reads a tool's contract, runs `--plan` to see it would write two files
and contact one host, and only then executes. Today the CLI "contract" is a
comma-separated string with the types erased, and there is no dry run at all.

## v0.8 — Reversible

The moonshot, scheduled last because `Origin` (#347) makes it affordable and attempting
it first would mean building the entire foundation plus the hardest feature at once.

| # | Item |
|---|---|
| [#362](https://github.com/onion-lang/onion/issues/362) | lossless shapes and residue |
| [#363](https://github.com/onion-lang/onion/issues/363) | lens — edit parsed data and write it back intact |
| [#364](https://github.com/onion-lang/onion/issues/364) | open `Shape` to user-written instances |

**Demo:** read a commented config, change one field, write it back — comments and
formatting intact, `diff` shows one line.

---

## Breaking changes

Onion has one user today, so v0.6 and v0.7 break compatibility where the alternative is
carrying a bad design forward. The full list with rationale is in
[typed-boundaries.md §8](typed-boundaries.md). The largest are: `parse` returning
`Outcome` instead of `T?`, `derive!` and `from re"…"` being replaced by named `shape`
declarations, `Cli` no longer calling `System.exit`, and (v0.7) the default static import
set narrowing.

---

## Feasibility: the first slice

Before committing to the milestones above, two structural risks were checked against the
implementation. Both came back clear.

### Risk 1 — is `processAssignable` a bottleneck? **It is not on the path.**

`AssignabilitySupport.processAssignable` (`typing/AssignabilitySupport.scala:60-190`) is
the single choke point every "does this value fit this type" decision passes through — 38
references, one implementation. It looked like the natural home for any new subsumption
rule, but it is already a ~130-line method carrying seven orthogonal concerns
(empty-collection retyping, bottom types, constant narrowing, boxing, the
null-to-non-nullable warning, primitive/null rejection, platform unboxing, structural
type-variable assignability), so adding an eighth would be the riskiest edit in the plan.

It turns out not to be needed:

- **`Shape[T]` needs nothing there.** A shape is an ordinary generic class. "A lossless
  shape is usable where an L1 shape is expected" is plain subtyping, already handled.
- **Effects need nothing there either.** Effects are declared and checked *only* at `tool`
  boundaries ([typed-boundaries.md §6.5](typed-boundaries.md)), which is a whole-program
  question about a call graph, not a pairwise type-compatibility question. It belongs in
  its own pass.

That pass has direct precedent. `CompilerPhase` is a four-line trait
(`pipeline/CompilerPhase.scala`) and `CompilationPhases` already holds **two** phases of
exactly the shape an effect check needs — `CompilerPhase[Seq[TypedAST.ClassDefinition],
Seq[TypedAST.ClassDefinition]]` (`pipeline/PipelineRunner.scala:19-20`, the TCO and
mutual-recursion passes). Adding a third is a local change.

One caveat: `PipelineRunnerSpec.scala:22-31` pins the phase-name sequence, so a new phase
means updating that spec deliberately rather than by accident. That is a feature.

### Risk 2 — does the slice need compiler changes at all? **Barely.**

`Origin`, `Defect` and `Outcome[T]` are ordinary classes in `src/main/java/onion/`. The
`zip`/`bind` law split is a library concern. A first `Shape[T]` with `parse`/`print` is
likewise a library type. **The compiler is not involved until the `shape` declaration form
and the non-invertible-`print` diagnostic**, which means the risky, high-blast-radius work (grammar, `Rewriting.scala`'s
already-overloaded 1,416 lines, `TypingOutlinePass`) can be deferred until the runtime
design has been validated against real programs.

That is the opposite of the usual order and it is the right one here: the semantics are
the uncertain part, not the syntax.

### Recommended starting order

1. **#346 first, and alone.** It is the only item that changes behaviour people already
   depend on, it is self-contained, and everything downstream leans on laws actually
   running. It also has the sharpest first failing test.
2. **#347 → #348 as pure library work**, verified by ordinary specs.
3. **#349 (the scalar registry) as the first compiler edit**, because it is a
   consolidation of code that already exists — five switches becoming one — rather than a
   new concept. It is the cheapest way to find out how much `Rewriting.scala` resists.
4. **#350 only then.**

### The first failing test

For #346, in a file that does not exist yet
(`src/test/scala/onion/compiler/verification/ArgGeneratorSpec.scala` — note there is no
`verification` test directory at all today):

> A record carrying `law l(xs: List) { true }` compiles today and reports nothing,
> because `LawCheckPhase.scala:80-81` returns silently when a parameter type has no
> generator. The test asserts the compilation fails with **`E0074`**.

It fails today for the right reason — the compile succeeds — and it pins the single
behaviour that makes the rest of the roadmap honest.
