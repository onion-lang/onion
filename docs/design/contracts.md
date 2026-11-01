# Contracts — static verification for Onion

Status: **proposed.** Nothing here is implemented. RFC: [#2557](https://github.com/onion-lang/onion/issues/2557).
Onion is in `consolidation` mode and has one known user, so the removal of `law`/`example`
needs no transition period. It still lands **after** its replacement works (§11).

Code blocks show **proposed** syntax. `docs/design/` is outside the scope of
`DocExamplesCompileSpec`, so nothing here is compile-checked.

---

## 1. Positioning

> **A method says what it needs and what it gives back. A class says what stays true.
> The compiler proves it, tests it, or says it could not.**

Onion today has one way to state a property: `law` and `example`. Both are **build-time
tests**. The compiler runs the generated code on sampled inputs and reports a failure. It
never proves anything.

The evidence from `run/` (610 programs):

- 111 use them: 307 clauses, of which 299 are `example` and 8 are `law`.
- About 255 of the `example` clauses are closed boolean checks such as
  `example { new X(1).f() == 2 }`. They are unit tests, not properties.
- No clause says what a *method* promises or what a *class* keeps true.
- Nothing can say how an object may change over time. That is the gap Liskov and Wing (1994)
  call a *history property*.

This document replaces `law` and `example` with **contracts**. They are tested first and
proved later, and the removal waits until the test tier works.

### 1.1 Why now

Checking a program against its contracts statically is old (Hoare logic; Eiffel's Design by
Contract, 1986; Liskov and Wing, 1994; later JML, Spec#, Dafny, SPARK). It has lived in research
and in a few niches, and it has not become a normal feature of a general-purpose language.
Five things made it impractical. Each one has changed, or is changed by this design.

| What kept it impractical | What is different here |
|---|---|
| **The annotations cost more than the code.** A person wrote a contract, often a loop invariant too, by hand. | A model writes the code and its contract together. Onion's own development is run by a loop of AI agents (`docs/evolution/`), so the writers and the users of contracts are the same. |
| **A failure was opaque.** "Postcondition might not hold", with a solver term to decode. | An error is a run that reproduces. A broken history shows the call sequence that breaks it (§9). |
| **All or nothing.** A contract was either proved, or worth nothing. | Four verdicts (§2). Tests come first, so a contract is useful before any prover exists. |
| **The toolchain was heavy.** An external solver, in a version that drifts. | An in-house solver in `onion.jar`, with checkable answers (§8). It is the last tier, and optional. |
| **It was bolted onto languages that did not fit.** Free aliasing, unspecified overflow. | The JVM integer is exact, and Onion is young and pre-1.0. It can restrict what a contract may name (§3.2) before habits form, and it can break compatibility to do so. |

One reason is new, and it is the main one. **Code is now written faster than it is reviewed.**
A short contract is a smaller thing to review than the body it governs, for a person or for a
model, and the compiler holds the rest of the code to it. Reading a method's promise is cheaper
than reading the method.

What this does not claim: that verification is now easy, or that a contract is always right.
A weak contract proves nothing useful, and a model can write one. So the verdicts say which
contracts were proved and which were only sampled, and a counterexample often exposes the hole
in the contract itself (the `Counter` in §3 needs `requires n < 2147483647`).

### Design constraints this document holds itself to

| Constraint | Source |
|---|---|
| One mechanism for stating a property, not two (once the replacement works) | PRINCIPLES 2 |
| A reported error can always be reproduced by running the program | PRINCIPLES 1 |
| Stage the feature; each stage is useful on its own | PRINCIPLES 6, 9 |
| Every diagnostic has a code, a location and a fix hint, in en and ja | PRINCIPLES 7 |
| The formatter, LSP and highlighting learn the new syntax in the same change | PRINCIPLES 8 |
| A stable feature is never replaced by a preview one | PRINCIPLES 3, 6 |
| `onion.jar` stays self-contained and small | CLAUDE.md |

---

## 2. The core, in three concepts

1. **Contract clauses**: `requires`, `ensures`, `invariant`, `history`.
2. **Verification condition (VC)**: a formula that is valid exactly when a contract holds.
3. **Verdict**: one of four.

| Verdict | Meaning | What happens |
|---|---|---|
| Proved | The VC is unsatisfiable and the answer passed an independent check (§8) | The runtime check may be dropped |
| Counterexample | Running the program breaks the contract (§7) | Error |
| Sampled | Not proved. N inputs were tried and none broke it | Silent. `--contracts-report` lists them |
| Not proved | No proof, and the contract cannot be sampled (see §7) | Warning `W0021` |

`Sampled` is what `law` gave. It stays a first-class verdict, because `Double`, strings and
anything the prover does not model will never reach `Proved`.

The promise: **every error can be reproduced by running the program, and every "proved" has
passed an independent check.** A tool that reports a false error is ignored. A tool that
reports a false proof is dangerous. This design prefers silence to either.

---

## 3. Syntax and meaning

`requires`, `ensures`, `invariant`, `history`, `old` and `result` are **soft keywords**: they
mean something only in the positions below, and stay ordinary names everywhere else.

```onion
class Counter {
  var n: Int
  invariant n >= 0
  history n >= old(n)                   // once raised, never lowered
public:
  def this { n = 0 }
  def inc(): void
    requires n < 2147483647             // Int wraps on the JVM; ask for headroom
    ensures n == old(n) + 1
  { n = n + 1 }
  def get(): Int ensures result == n { return n }
}
```

| Clause | Where | Meaning |
|---|---|---|
| `requires e` | after the signature, before the body | `e` holds on entry. The caller must establish it. |
| `ensures e` | same | `e` holds on normal return. `result` is the return value; `old(e)` is `e` evaluated on entry. |
| `invariant e` | class or record body | `e` holds after every constructor, and on entry to and exit from every public method. |
| `invariant e` | before a loop body | `e` holds before each iteration. Used by the prover only. |
| `history e` | class or record body | A relation between two states of one object. `old(x)` is the earlier state, a bare `x` the later one. |

Several clauses of one kind combine by conjunction.

**Integers wrap.** `Int` and `Long` are 32 and 64 bits and wrap as on the JVM, in programs and
in contracts alike. A contract is not about mathematical integers. Without `requires n <
2147483647`, `inc()` at the maximum makes `n` negative and breaks both `invariant` and `history`.
The compiler finds exactly that (§7).

**`old(e)`** evaluates `e` on entry and keeps the value. If `e` is a reference, the value is
the reference, not a copy of the object it points to. `old(xs.size())` keeps a number;
`old(xs)` keeps nothing useful.

### 3.1 Contract expressions are pure — and "pure" gets stricter

A contract may use: integer and boolean operators, comparisons, `null` checks, parameters,
`result`, `old(...)`, the object's own fields, and calls to methods that are **pure**.

Today an empty effect set means *no I/O*. It does not mean *no mutation*: the effect table marks
`List`, `ArrayList`, `HashMap` and `StringBuilder` as `#*=pure`, so `ensures xs.add(1)` would
pass. This design adds one effect, **`mutate`**:

- A write to a field or array element of an object that is not created inside the expression,
  or a call to something that does one, carries `mutate`.
- Library mutators are re-marked in the effect table (`List.add`, `StringBuilder.append`, ...).
- A contract expression must have **no effect at all, including `mutate`**.
- `onionc --effects` prints the new effect. `modifies`, which the prover needs for framing
  (§7), is read from the same inference.

This is a prerequisite of stage 1, because a contract that mutates is not a contract.

### 3.2 What a contract may talk about (framing)

Aliasing is the usual way "one call at a time" breaks. These rules close it in the first version.

1. `invariant` and `history` may name only fields of `this` whose type is a **value type**:
   a primitive, `String`, an enum, or a record whose components are value types.
   A field that holds a mutable object (`List`, an array, another class) cannot be named.
2. A field named by an `invariant` or `history` is **written only through `this`**, inside its own
   class. `o.n = 0` with `o` another instance is an error. The field must not be assignable from
   outside the class (`private`, or `val`).
3. A class with an `invariant` or `history` may not extend a Java class other than `Object`,
   because a Java superclass can have mutators the compiler can neither prove nor wrap.
4. `requires` and `ensures` may mention parameters of any type, through pure methods.
5. A call is **atomic**: no callback re-enters the object halfway, and no other thread
   interleaves. Invariants and histories are promised only at call boundaries.

These are checks, not hopes: breaking one is a diagnostic (§9).

### 3.3 `history`

`history e` says: for any two states `s` and `t` of one object, where `t` is reachable from
`s` by method calls, `e` holds with `old(x)` read in `s` and `x` read in `t`.

It is checked **one call at a time**: it behaves as an implicit `ensures` on every public
method. That is sound when the relation is a preorder.

> **Lemma.** If `φ(a, a)` (reflexive) and `φ(a, b) ∧ φ(b, c) ⇒ φ(a, c)` (transitive), and every
> call from state `a` to state `b` satisfies `φ(a, b)`, then `φ(s, t)` holds for every reachable
> pair. Proof: induction on the number of calls between `s` and `t`.

The compiler checks reflexivity and transitivity. `n >= old(n)` passes. `n == old(n) + 1`
is reported: it is not transitive, so it cannot describe a whole history.

### 3.4 Where the clauses may appear, and how they parse

- **Not on a `tool`.** A `tool` keeps `requires { cap }` for capabilities and takes no
  contract clauses in the first version; a contract on a tool is an error that suggests a
  helper method. So `requires` never means two things on one declaration.
- **A clause expression ends at the `{` that opens the body.** It follows the same rule as the
  condition of `if c {`: `ensures result == n { return n }` is not read as a trailing lambda on
  `n`. To pass a lambda inside a clause, parenthesize the call. The parser already has this
  context (`conditionContexts`).

---

## 4. Subtyping: Liskov and Wing

Liskov and Wing, *A Behavioral Notion of Subtyping* (ACM TOPLAS 16(6), 1994), define when a
subtype may stand in for its supertype. Their rules, and what this design does with each:

| Rule | In Onion | Checked by |
|---|---|---|
| Signature | The existing override check | `typing/DuplicationChecks.scala` |
| Methods: preconditions weaken, postconditions strengthen | **By construction**, below | The override's body is verified against the effective contract |
| Invariant: the subtype preserves the supertype's invariant | `inv_eff = inv_sub ∧ inv_super` | Tests / VC |
| History (their "constraint"): the subtype preserves the supertype's history, **including through methods the supertype does not have** | `hist_eff = hist_sub ∧ hist_super`, applied to every public method | Tests / VC |

**Effective contract of an override**, in the style of Eiffel's `require else` / `ensure then`
(Meyer):

```
pre_eff  = pre_super  ∨ pre_sub
post_eff = (old(pre_super) ⇒ post_super) ∧ (old(pre_sub) ⇒ post_sub)
```

So `pre_super ⇒ pre_eff` holds without proof, and `post_eff ⇒ post_super` holds whenever
`pre_super` held on entry, which is what a caller of the supertype guarantees. A programmer
cannot write an override that is unsound by the methods rule. An override without clauses of
its own simply inherits.

A method the subclass **inherits without overriding** is verified again against `inv_sub` and
`hist_sub`, in the subclass's context: the callees it reaches through overridable methods use
the effective contracts there.

This design does **not** reproduce the paper's abstraction functions. They are needed when a
subtype represents its state differently. A subclass in Onion shares its superclass's fields,
and an interface states its contract through pure observer methods, so the identity mapping
is enough.

### 4.1 The case only a history catches

```onion
class ResettableCounter extends Counter {
public:
  def reset(): void { n = 0 }
}
```

Every inherited pre/postcondition still holds, and `reset` has no contract of its own. A tool
that checks only pre/postconditions accepts this class. The compiler reports it (full text in §9).

### 4.2 Interfaces

An interface has no fields. Its contracts use pure observer methods:

```onion
interface Point {
  def x(): Int
  history x() == old(x())               // immutable
}

class MovablePoint conforms Point {     // error on move(): breaks Point's history
  var px: Int
  def x(): Int = px
public:
  def move(dx: Int): void { px = px + dx }
}
```

---

## 5. Library types: `ContractTable`

Java classes cannot carry these clauses. Contracts for them live in a **contract table**, built
the same way as the effect table: a built-in table, plus an optional table shipped inside a
library jar. A malformed table is a warning, not an error.

### 5.1 The `Shape` contract

`onion.Shape` documents two laws and says one of them is false in general (`Shape.java`):

| Law | Statement | Contract? |
|---|---|---|
| L1 round-trip | `parse(print(v)) == Ok(v)`, wherever `print` exists | **Yes** |
| L2 normalization | `print(parse(t)) == t` | **No.** It is false in general, so it is never asserted |

```onion
// ContractTable entry for onion.Shape[T]
def print(v: T): String
  requires canPrint()
  ensures parse(result) is Ok(v)
```

- Every class that conforms to `Shape` inherits it and is checked against it. A user-written
  shape no longer needs a `law` to satisfy E0080.
- A **non-invertible** shape has `canPrint() == false`. Its `print` precondition is never met,
  so it owes nothing. This is why the contract has a precondition rather than being absolute.
- The usual verdict is `Sampled` (the body is string work the prover does not model); a shape
  over fixed-width integers can be `Proved`.

E0080 is removed only after this entry works (§11).

---

## 6. Where contracts run

| Context | Behaviour |
|---|---|
| `onion script.on`, REPL | `runtime`: entry and exit checks on, closed predicates run. No prover. |
| `onion build` | Closed predicates run, parameterized predicates are sampled. `--contracts verify` adds the prover. |
| `onion test` | `runtime`, plus closed predicates and sampling. |
| LSP | Typing, purity, framing and effective-contract diagnostics only. **It never runs user code**, matching the current `checkLaws = false` (#346). No prover until it fits an editor's latency budget. |

**`--contracts <off|runtime|verify>`.**
- While in preview the default is `off`: contract clauses are a syntax error with a hint to
  enable the flag.
- At stage 4, when `law`/`example` are removed, the `runtime` tier **graduates** and becomes the
  default. The migrated `run/` programs then need no flag.
- `verify` stays opt-in until the prover is proven in use (stage 7).

**`W0021` (contract not proved)** is off by default. It turns on under `--contracts verify`,
because before the prover exists every contract is "not proved" and the warning would be noise.

**Flags that replace the old ones:** `--contract-samples <n>` and `--contract-seed <n>`
(sampling), `--contract-timeout <seconds>` (prover), `--contracts-report` (list `Sampled`).

---

## 7. How a contract is checked

### 7.1 Closed and parameterized predicates (the `law` and `example` replacement)

```onion
static def roundTrip(p: Point): Boolean
  ensures result
  = Point::parse(Point::format(p)) == p
```

- A predicate with **parameters** is a property over all inputs. The prover tries first
  (stage 6). Until then, and for what the prover does not model, sampled inputs look for a
  counterexample (`ArgGenerator`) and the verdict is `Sampled`.
- A predicate with **no** parameters is closed. It usually calls other methods, which a prover
  cannot unfold, so it is decided by running it at build time, as `example` is today.

No new keyword is added. A `check` sugar would be `law` under another name.

### 7.2 Methods and classes

1. After typing and before tail-call optimization, a new phase reads each method's effective
   contract. Running before TCO matters: the body is still a loop.
2. **Tests first (stage 2).** Objects are built by constructors, public methods are called in
   random sequences, and `invariant`/`history` are checked after every call. This is the
   `Sampled` and `Counterexample` source until the prover exists.
3. **Verification conditions (stages 6–7).** Weakest precondition over the typed tree.
   A call is handled modularly: assert the callee's effective precondition, forget what it may
   `modify` (§3.1), assume its postcondition.
4. **Encoding.** `Int` and `Long` are 32/64-bit vectors, so wrap-around matches the JVM. A pure
   method with an expression body is inlined to a fixed depth, or is an uninterpreted function.
5. What is not proved keeps a runtime check.

### 7.3 Replay: what makes a counterexample real

A prover's model gives field values and arguments. To keep "every error can be reproduced":

1. **Reachable first.** Build the object with a constructor and a bounded call sequence that
   steers towards the model, then call the method.
2. **If no sequence is found,** write the model's field values directly (reflection) and run the
   method. This is valid because the model satisfies the invariant. It is reported as an error
   with the note *"from a state that satisfies the invariant; reachability not shown"*. It means
   the invariant is not strong enough to be preserved, which is the programmer's to fix.
3. If the run does not break the contract, the verdict is `Not proved`, not an error.

The `Counter` without headroom shows both: `n = 2147483647` satisfies `n >= 0`, and `inc()` makes
it `-2147483648`.

Not modelled, so never `Proved`: `Double` and `Float`, strings beyond equality and length,
reflection, and anything behind a Java call that is not in the effect table.

---

## 8. The solver

The solver is written in-house and bundled in `onion.jar`. "As strong as z3 or cvc5" is a
direction, not the first milestone. The first milestone is the fragment that VCs need,
solved exactly and with answers that can be checked.

| Stage | Content | Size |
|---|---|---|
| S1 | CDCL SAT: two watched literals, VSIDS, restarts, 1-UIP learning, Tseitin transform | 2–3 k lines |
| S2 | Bit-blasting of 32/64-bit vectors and booleans onto S1 | about 2 k lines |
| S3 | Uninterpreted functions (Ackermann reduction) and arrays for fields | about 1 k lines |
| S4 | Mathematical integers (simplex, branch and bound) | only if S2 proves too slow |

**Why a hand-written solver can be trusted:**

- **UNSAT is certified.** S1 emits a DRAT proof; a separate checker of about 200 lines verifies
  it. If the check fails, the verdict is `Not proved`.
- **SAT is certified.** The model is substituted into the formula, then replayed (§7.3).
- **A fuzz spec** builds random formulas over 4–6 bit vectors and compares the solver with
  exhaustive search. It needs no external tool. An optional CI job compares against z3.

What the checkers cannot see is a bug in VC generation or in the bit-blaster. The fuzz spec and
the replay are the guard there.

**The case for building it (PRINCIPLES 9).** Tests (§7.2) already give counterexamples and
`Sampled`. Only a proof can (a) drop a runtime check, (b) cover `2^32` inputs rather than 40,
and (c) establish a history for **all** call sequences rather than the ones that were tried.
The cost is bounded by staging: stages 1–4 are the "runtime contracts plus sampling, no solver"
option in full. If the solver stages are cut, that option is what remains, and it is already
more than `law`/`example` gave.

The solver sits behind a `Solver` interface, so an external z3 or cvc5 can be added later as
another backend.

---

## 9. Error model

Codes are **provisional** and assigned when each stage lands (the highest code today is E0104).

| Code | Diagnostic |
|---|---|
| E0105 | Precondition may not hold at a call (names the callee and the arguments found) |
| E0106 | Postcondition does not hold (shows `old` and `result`) |
| E0107 | Invariant not preserved by a method or constructor |
| **E0108** | **History broken by a method** (below) |
| E0109 | History is not reflexive / not transitive (shows the failing triple) |
| E0110 | A contract is not pure, or `old`/`result` is out of place |
| E0111 | A field named by a contract is written from outside `this`, or is not a value type |
| E0112 | Contract not allowed here (on a `tool`, or on a class that extends a Java class) |
| W0021 | Contract not proved |

The history error, with the call sequence that reproduces it and a next step:

```
reset.on:12:5: [E0108] ResettableCounter.reset() breaks the history of Counter: n >= old(n)
  reproduced by:  new ResettableCounter(); inc(); inc(); inc(); reset()
  n before: 3     n after: 0
  hint: a subclass may add methods, but each must keep every history of its superclass.
        Remove the write to `n`, or relax the history in Counter.
```

```
reset.on:12:5: [E0108] ResettableCounter.reset() は Counter の history `n >= old(n)` を破ります
  再現手順:  new ResettableCounter(); inc(); inc(); inc(); reset()
  n: 3 -> 0
  ヒント: サブクラスはメソッドを足せますが、スーパークラスの history はすべて保たなければ
          なりません。`n` への代入を外すか、Counter の history を緩めてください。
```

Each has an en and ja message and a hint, and is added to `docs/reference/error-codes.md` and
its ja mirror.

---

## 10. Compatibility — the breaking change

One known user. No transition period. The removal lands in **one PR, at stage 4**, after
every replacement exists (§11).

**Removed**
- `law` and `example`, in a record body and at the top level.
- `--no-check-laws`, `--law-seed`, `--law-samples`, including their parsing in `DaemonClient`.
- E0064, E0065, E0074, E0075, E0080. These numbers are **not reused**.
- The compiler pieces behind them: `LawCheckPhase`, `CheckMethodIndex`, `CounterexampleText`,
  `ShapeInstanceLawPass`, the grammar and AST nodes, the `Rewriting` synthesis.
  `ArgGenerator` stays: it feeds sampling.

**Must change in the same PR**
- `docs/quality-bar.md` and its ja mirror: removing five codes makes `declared` smaller than the
  recorded figure, which `QualityBarSpec` row 7 rejects (`recorded <= declared`).
- `ProjectBuilder` and `ProjectTestRunner`, which force laws on today (§6 gives the new modes).
- Everything that relied on E0080: `run/FixedWidthDemo.on`, `UserShapeInstanceSpec`,
  `docs/guide/shapes.md` (en/ja), `docs/getting-started/quick-start.md`.

**The RUNBOOK §5.3 package**
- A `### Breaking` fragment in `changelog.d/`.
- A section in `docs/migration/pre-1.0.md` and its ja mirror.
- A hint at the old spelling, pointing to the replacement and to `onion migrate`.
- An `onion migrate` rule (token-level, applied only when the result compiles). It is also run
  once over the 111 files in `run/` and the result committed.

**Also updated:** the VS Code grammar, the Pygments lexer, `CLAUDE.md` and `CLAUDE_ja.md`, about
15 docs pages, 7 specs deleted and about 18 edited.

---

## 11. Staging and reversibility

The order is **additive first**: nothing is removed until what replaces it has a defined verdict.
Each stage is its own PR and is useful alone.

| Stage | Content | `law`/`example` |
|---|---|---|
| 1 | `mutate` effect; contract syntax and typing; framing rules; entry/exit checks; closed-predicate run. Behind `--contracts` | kept |
| 2 | Effective contracts for overrides; invariant and history **tests** (random call sequences); sampling of parameterized predicates; the `Sampled` verdict | kept |
| 3 | `ContractTable` and the `Shape` contract | kept (E0080 still on) |
| 4 | **The breaking PR:** migration rule, `run/` migrated, `law`/`example`/E0080 removed, docs and flags. The `runtime` tier graduates | **removed** |
| 5 | Solver S1 and S2, with the DRAT checker and the fuzz spec | — |
| 6 | VCs for straight-line code and loop invariants. `verify` mode, `W0021` on | — |
| 7 | History VCs, replay of counterexamples, guide and sample (en/ja). `verify` stays opt-in | — |
| 8 | Solver S3 | — |

If stages 5 to 8 are never finished, stages 1 to 4 still give contracts, inheritance of
contracts, history checking by test, and runtime checking. That is more than `law`/`example` gave.

---

## 12. Open questions

- A runtime `ensures` wrapper is not a tail call, so it defeats tail-call optimization. Route
  self-calls inside the wrapper, or document the limit?
- Quantifiers. `forall` over `Int` ranges, or pure recursive helper predicates? Collections need one.
- `ensures` on an exceptional exit.
- Mutable aggregates in `invariant`/`history` (`items.size() <= 10`) need an ownership rule. Not in v1.

---

## 13. Rejected alternatives

| Alternative | Why not |
|---|---|
| Keep `law`/`example` and add contracts, for good | Two mechanisms for one purpose (PRINCIPLES 2). They go at stage 4 |
| Remove `law`/`example` first | A stable feature would be replaced by a preview one (PRINCIPLES 3, 6) |
| A `check` keyword that replaces `law` | It is `law` renamed; no complexity removed (PRINCIPLES 9) |
| **Runtime contracts plus sampling, no solver** | Not rejected: it is stages 1–4. The solver is the extra, and §8 gives the case for it |
| Proofs by an external z3/cvc5 only | Nothing is proved where no solver is installed |
| Runtime assertions only | No counterexample before a run, and no history across call sequences |
| String annotations `@Requires("...")` | The expression is not typed; no editor support; `Annotation` has no arguments |
| Refinement types | A far larger change to the type system |
| Liskov–Wing abstraction functions | Needed only when a subtype changes its representation (§4) |

---

## 14. What this buys, concretely

- A subtype that breaks its supertype's history is rejected, with the call sequence that shows
  it (§4.1, §9). Pre/postcondition checkers miss this.
- A contract that is proved needs no runtime check.
- A broken contract comes with a reproducible counterexample, not a statistic.
- The `Shape` round-trip rule is stated once, on the interface, instead of in every file that
  implements it, and the false rule (L2) is never asserted.
- About 300 build-time test clauses move to one mechanism, after that mechanism works.
