# Contracts — static verification for Onion

Status: **proposed.** Nothing here is implemented. RFC: [#2557](https://github.com/onion-lang/onion/issues/2557).
Onion is in `consolidation` mode and has one known user, so this design breaks compatibility
without a transition period (see §10).

Code blocks show **proposed** syntax. `docs/design/` is outside the scope of
`DocExamplesCompileSpec`, so nothing here is compile-checked.

---

## 1. Positioning

> **A method says what it needs and what it gives back. A class says what stays true.
> The compiler proves it, or says it could not.**

Onion today has one way to state a property: `law` and `example`. Both are **build-time
tests**. The compiler runs the generated code on sampled inputs and reports a failure. It
never proves anything.

The evidence from `run/`:

- 111 of 612 programs use them: 307 clauses, of which 299 are `example` and 8 are `law`.
- About 255 of the `example` clauses are closed boolean checks such as
  `example { new X(1).f() == 2 }`. They are unit tests, not properties.
- No clause says what a *method* promises or what a *class* keeps true.
- Nothing can say how an object may change over time. That is exactly the gap Liskov and
  Wing (1994) call a *history property*.

This document replaces `law` and `example` with **contracts that are checked statically**.

### Design constraints this document holds itself to

| Constraint | Source |
|---|---|
| One mechanism for stating a property, not two | PRINCIPLES 2 |
| A reported error can always be reproduced by running the program | PRINCIPLES 1 |
| Stage the feature; each stage is useful on its own | PRINCIPLES 6, 9 |
| Every diagnostic has a code, a location and a fix hint, in en and ja | PRINCIPLES 7 |
| The formatter, LSP and highlighting learn the new syntax in the same change | PRINCIPLES 8 |
| `onion.jar` stays self-contained and small | CLAUDE.md |

---

## 2. The core, in three concepts

1. **Contract clauses**: `requires`, `ensures`, `invariant`, `history`.
2. **Verification condition (VC)**: a logical formula that is valid exactly when a contract holds.
3. **Verdict**: one of *proved*, *counterexample*, *not proved*.

| Verdict | Meaning | What happens |
|---|---|---|
| Proved | The VC is unsatisfiable and the answer passed an independent check (§8) | The runtime check may be dropped |
| Counterexample | The VC has a model **and running the program on it breaks the contract** | Error |
| Not proved | Timeout, unsupported construct, or a model that does not reproduce | Warning `W0021`; the runtime check stays |

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
  def inc(): void ensures n == old(n) + 1 { n = n + 1 }
  def get(): Int ensures result == n { return n }
}
```

| Clause | Where | Meaning |
|---|---|---|
| `requires e` | after the signature, before the body | `e` holds on entry. The caller must establish it. |
| `ensures e` | same | `e` holds on normal return. `result` is the return value; `old(x)` is `x` on entry. |
| `invariant e` | class or record body | `e` holds after every constructor, and on entry to and exit from every public method. |
| `invariant e` | before a loop body | `e` holds before each iteration. Used by the prover only. |
| `history e` | class or record body | A relation between two states of one object. `old(x)` is the earlier state, a bare `x` the later one. |

Several clauses of one kind combine by conjunction.

### 3.1 Contract expressions are pure

A contract may use: integer and boolean operators, comparisons, `null` checks, the object's
own fields, parameters, `result`, `old(...)`, and calls to methods whose **inferred effect set
is empty**. The effect inference that backs E0077 decides this (`effects/EffectInference.scala`),
so no new notion of purity is added. Anything else is an error that names the offending call.

There are no quantifiers in the first version. See §12.

### 3.2 `history`

`history e` says: for any two states `s` and `t` of one object, where `t` is reachable from
`s` by method calls, `e` holds with `old(x)` read in `s` and `x` read in `t`.

It is checked **one call at a time**: it behaves as an implicit `ensures` on every public
method. That is sound when the relation is a preorder.

> **Lemma.** If `φ(a, a)` (reflexive) and `φ(a, b) ∧ φ(b, c) ⇒ φ(a, c)` (transitive), and every
> call from state `a` to state `b` satisfies `φ(a, b)`, then `φ(s, t)` holds for every reachable
> pair. Proof: induction on the number of calls between `s` and `t`.

The compiler checks reflexivity and transitivity as VCs. `n >= old(n)` passes.
`n == old(n) + 1` is reported: it is not transitive, so it cannot describe a whole history.

### 3.3 Soundness conditions

These are stated in the language guide and enforced where they can be.

- A call is **atomic**: no callback re-enters the object halfway, and no other thread
  interleaves. Invariants and histories are only promised at call boundaries.
- A field named by an `invariant` or `history` must not be assignable from outside the class.
  Otherwise outside code could break the contract with no call to check. This is an error.
- A contract speaks about `this`, the parameters and the result. Another object is seen only
  through pure methods.
- A Java call that is not in the effect table may change anything, so a proof that crosses it is
  *not proved*.

---

## 4. Subtyping: Liskov and Wing

Liskov and Wing, *A Behavioral Notion of Subtyping* (ACM TOPLAS 16(6), 1994), define when a
subtype may stand in for its supertype. Their rules, and what this design does with each:

| Rule | In Onion | Checked by |
|---|---|---|
| Signature | The existing override check | `typing/DuplicationChecks.scala` |
| Methods: preconditions weaken, postconditions strengthen | **By construction**, below | The override's body is verified against the effective contract |
| Invariant: the subtype preserves the supertype's invariant | `inv_eff = inv_sub ∧ inv_super` | VC |
| History (their "constraint"): the subtype preserves the supertype's history, **including through methods the supertype does not have** | `hist_eff = hist_sub ∧ hist_super`, applied to every public method | VC |

**Effective contract of an override**, in the style of Eiffel's `require else` / `ensure then`
(Meyer):

```
pre_eff  = pre_super  ∨ pre_sub
post_eff = (old(pre_super) ⇒ post_super) ∧ (old(pre_sub) ⇒ post_sub)
```

So `pre_super ⇒ pre_eff` and `post_eff ⇒ post_super` hold without any proof, and a programmer
cannot write an override that is unsound by the methods rule. An override without its own
clauses simply inherits.

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
that checks only pre/postconditions accepts this class. With the history above the compiler
reports:

```
ResettableCounter.reset breaks history of Counter: n >= old(n)
  state before: n = 3
  state after:  n = 0
```

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

The first entry is `onion.Shape`: its `print` and `parse` must round-trip. Every class that
conforms to `Shape` inherits that contract and is verified against it. This is what E0080
(`a user-written Shape needs a law`) was approximating. With the contract on the interface,
the rule is no longer needed.

---

## 6. Replacing `law` and `example`

| Today | After |
|---|---|
| `law name(p: T) { e }` | A pure predicate with a contract (below) |
| `example [name] { e }` | The same, with no parameters |
| E0080: a user `Shape` needs a law | The `Shape` contract (§5) |
| `--no-check-laws`, `--law-seed`, `--law-samples` | `--contracts <off\|runtime\|verify>`, `--contract-timeout <s>` |

```onion
static def roundTrip(p: Point): Boolean
  ensures result
  = Point::parse(Point::format(p)) == p
```

A predicate with parameters is a property over all inputs: the prover tries to prove it, and if
it cannot, sampled inputs look for a counterexample (reusing `ArgGenerator`). A predicate with
**no** parameters is closed. A closed predicate usually calls other methods, which a prover
cannot unfold, so it is decided by **running it at build time**, as `example` is today.

No new keyword is added for this. A `check` sugar would be `law` under another name.

---

## 7. How a contract is verified

1. After typing and before tail-call optimization, a new phase reads each method's effective
   contract. Running before TCO matters: the body is still a loop, not a rewritten state machine.
2. **Verification condition generation.** Weakest precondition over the typed tree:
   assignments to locals and fields, `if`, `while` with its `invariant`, `return`, and operators.
   A call is handled **modularly**: assert the callee's effective precondition, forget what the
   callee may modify, assume its postcondition. `modifies` is inferred from the body.
3. **Encoding.** `Int` and `Long` are bit-vectors of 32 and 64 bits, so wrap-around matches the
   JVM exactly. Booleans are booleans. A pure method with an expression body is inlined to a
   fixed depth; otherwise it is an uninterpreted function.
4. The solver (§8) answers. A model is turned into concrete arguments and **replayed**: the
   instrumented program runs, and only a real violation becomes an error.
5. What was not proved keeps a runtime check (`--contracts runtime`): entry and exit checks, with
   `old` values saved into hidden locals.

Not modelled, so *not proved*: `Double` and `Float`, strings beyond equality and length, and
reflection.

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
  it. If the check fails, the verdict is *not proved*.
- **SAT is certified.** The model is substituted into the formula, then replayed on the real
  program (§7.4).
- **A fuzz spec** builds random formulas over 4–6 bit vectors and compares the solver with
  exhaustive search. It needs no external tool. An optional CI job compares against z3.

What the checkers cannot see is a bug in VC generation or in the bit-blaster. The fuzz spec and
the replay are the guard there.

The solver sits behind a `Solver` interface, so an external z3 or cvc5 can be added later as
another backend.

---

## 9. Error model

New diagnostics (codes are assigned when each stage lands):

| Diagnostic | Notes |
|---|---|
| Precondition may not hold at a call | Names the callee and the argument values of the counterexample |
| Postcondition does not hold | Shows `old` and `result` |
| Invariant not preserved by a method or constructor | |
| **History broken by a method** | Names the method, the inherited history, and the states before and after |
| History is not reflexive / not transitive | Shows the failing triple |
| A contract is not pure, or `old`/`result` is out of place | |
| A field named by a contract is writable from outside | |
| `W0021`: contract not proved | A warning; the runtime check stays |

Each has an en and ja message and a hint, and is added to `docs/reference/error-codes.md` and
its ja mirror.

---

## 10. Compatibility — the breaking change

One known user. No transition period: the removal and the migration land in one PR.

**Removed**
- `law` and `example`, in a record body and at the top level.
- `--no-check-laws`, `--law-seed`, `--law-samples`.
- E0064, E0065, E0074, E0075, E0080. These numbers are **not reused**.
- The compiler pieces behind them: `LawCheckPhase`, `CheckMethodIndex`, `CounterexampleText`,
  `ShapeInstanceLawPass`, the grammar and AST nodes, the `Rewriting` synthesis.
  `ArgGenerator` stays: it feeds counterexample search.

**Kept from the RUNBOOK §5.3 package**
- A `### Breaking` fragment in `changelog.d/`.
- A section in `docs/migration/pre-1.0.md` and its ja mirror.
- A hint at the old spelling, pointing to the replacement and to `onion migrate`.
- An `onion migrate` rule (token-level, applied only when the result compiles).

The rule is also the quickest way to move the 111 files in `run/`: it is run once and the
result is committed, which is its first real test.

**Also updated:** the VS Code grammar, the Pygments lexer, `CLAUDE.md` and `CLAUDE_ja.md`, about
15 docs pages, 7 specs deleted and about 18 edited.

---

## 11. Staging and reversibility

Each stage lands as its own PR, behind `--contracts` until the last one, and is useful alone.

| Stage | Content |
|---|---|
| 1 | Syntax, typing, runtime checks, closed-predicate run, **and the removal with its migration** |
| 2 | Effective contracts for overrides; `ContractTable` and `Shape` |
| 3 | Solver S1 and S2, with the DRAT checker and the fuzz spec |
| 4 | VCs for straight-line code and loop invariants |
| 5 | History VCs, counterexample replay, guide and sample (en/ja) |
| 6 | Solver S3 |

If stages 3 to 6 are never finished, stages 1 and 2 still give contracts, inheritance of
contracts and runtime checking, which is more than `law`/`example` gave.

---

## 12. Open questions

- `requires` is already a soft word in `tool ... requires { cap }`. They are told apart by
  position (a brace set of capabilities, or an expression). Is that clear enough?
- A runtime `ensures` wrapper is not a tail call, so it defeats tail-call optimization. Route
  self-calls inside the wrapper, or document the limit?
- Quantifiers. `forall` over `Int` ranges, or pure recursive helper predicates? Collections need one.
- `ensures` on an exceptional exit.
- Which `Shape` contract exactly, given that not every shape is invertible?

---

## 13. Rejected alternatives

| Alternative | Why not |
|---|---|
| Keep `law`/`example` and add contracts | Two mechanisms for one purpose (PRINCIPLES 2) |
| A `check` keyword that replaces `law` | It is `law` renamed; no complexity removed (PRINCIPLES 9) |
| Proofs by an external z3/cvc5 only | Nothing is proved where no solver is installed |
| Runtime assertions only | Not static; no counterexample before a run |
| String annotations `@Requires("...")` | The expression is not typed; no editor support; `Annotation` has no arguments |
| Refinement types | A far larger change to the type system |
| Liskov–Wing abstraction functions | Needed only when a subtype changes its representation (§4) |

---

## 14. What this buys, concretely

- A subtype that breaks its supertype's history is rejected at compile time, with the states
  that show it (§4.1). Pre/postcondition checkers miss this.
- A contract that is proved needs no runtime check.
- A broken contract comes with a reproducible counterexample, not a statistic.
- The `Shape` round-trip rule is stated once, on the interface, instead of in every file that
  implements it.
- About 300 build-time test clauses move to one mechanism.
