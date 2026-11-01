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
| Proved | The VC is unsatisfiable and the answer passed an independent check (§8) | A guard for a postcondition or invariant may be dropped (§7.3); a `requires` keeps its guard |
| Counterexample | A static check or a proof shows the contract broken, and for a proof the run of §7.4 reproduces it | Error |
| Sampled | Not proved. N inputs were tried and none broke it | Silent. `--contracts-report` lists them |
| Not proved | No proof, and nothing was found against it: the solver gave up, the construct is not modelled, the type cannot be generated, or a model could not be reached by a run | A note in `--contracts-report`; and `W0021` once proofs exist (stage 6). See "One rule for Not proved" in §6 |

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
protected:
  var n: Int                            // protected: a subclass may write it (section 4.1)
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

- A write to a field or array element, or a call to something that does one, carries `mutate`.
  This is deliberately conservative: it does not try to tell a write to an object the expression
  created itself from a write to anything else. A clause must have no `mutate` at all; the build
  is more lenient about what it runs (§6).
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
2. A field named by an `invariant` or `history` is written **only through `this`**, in its own
   class **or in a subclass**. `o.n = 0` with `o` another instance is an error. A write through
   `this` is allowed because it can be checked: that is exactly what the history of §3.3 and
   §4.1 is for. The field must not be visible outside the class and its subclasses (`private`,
   `protected` or `val`), so outside code cannot change it without a call that is checked.
3. **Rules 1 and 2 apply to everything a contract reads, transitively.** A contract that calls a
   pure method `p()` is read as if `p`'s body were written in the clause: a field `p` reads
   must follow rules 1 and 2 too. Reading through a method is not a way around them.
4. A class with an `invariant` or `history` may not extend a Java class other than `Object`,
   because a Java superclass can have mutators the compiler can neither prove nor wrap.
5. `requires` and `ensures` may mention parameters of any type, through pure methods.
6. A call is **atomic**: no callback re-enters the object halfway, and no other thread
   interleaves. This is an **assumption**, not a check, and every `Proved` is scoped to it: a
   proof says "if calls are atomic, then...". Invariants and histories are promised only at call
   boundaries.

Rules 1 to 4 are checks: breaking one is a diagnostic (§9). Rule 6 is the one thing a proof
assumes without proving.

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
- **A clause expression ends at the `{` or the `=` that opens the body, and is never an
  assignment.** It follows the same rule as the condition of `if c {`: `ensures result == n { return n }`
  is not read as a trailing lambda on `n`, and `def f(x: Int): Int requires x > 0 = x + 1` is a
  precondition `x > 0` followed by the body `= x + 1`, not the assignment `0 = x + 1`. To pass a
  lambda inside a clause, parenthesize the call. The parser already has the first context
  (`conditionContexts`), and reads a clause below the assignment level for the second.
  (An earlier draft of this document missed the `=` case; the panel reproduced it, and it is
  fixed and tested in both parsers.)

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
  def reset(): void { this.n = 0 }
}
```

Every inherited pre/postcondition still holds, and `reset` has no contract of its own. A tool
that checks only pre/postconditions accepts this class. The compiler reports it (full text in §9).

This example was run, not only written. On the compiler as it is today:

| `Counter.n` is | `reset()` writes `n = 0` | Result |
|---|---|---|
| `var n` (private) | in the subclass | **E0014**, the field is not accessible. The subclass cannot write it, so there is nothing to catch |
| `protected var n` | in the subclass | compiles. This is the case a history exists for |

So the field is `protected`. That is also what §3.2 rule 2 says: a write through `this`, in a
subclass, is allowed, and it is **checked** against the history instead of being forbidden. What
is forbidden is `other.n = 0` on a different object, which no call to `other` would ever check.

The check needs no solver. `reset` assigns the constant `0` to a field whose history says
`n >= old(n)`, and `Counter.inc` raises `n`, so `n` can be above `0` when `reset` runs. That is
a fact about the syntax of the two methods, not a guess about a run. §7.1 gives the full rule,
including what is *not* reported (a class whose only write is `n = 0` breaks nothing). Anything
harder waits for the prover.

### 4.2 Interfaces

An interface has no fields. Its contracts use pure observer methods, which must be public:

```onion
interface Point {
  def x(): Int
  history x() == old(x())               // immutable
}

class MovablePoint conforms Point {     // error on move(): breaks Point's history
  var px: Int
public:
  def this { px = 0 }
  def x(): Int = px                     // public, as the interface requires (private is E0037)
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
| L1 round-trip | `parse(print(v)) == Ok(v)`, wherever `print` exists | **Yes, for the values the shape can print** |
| L2 normalization | `print(parse(t)) == t` | **No.** It is false in general, so it is never asserted |

**The law holds only on a domain, so the contract names the domain.** `run/FixedWidthDemo.on` is
the proof. Its `print` pads `name` to 10 columns and does not cut it, so
`print(Person("Christopher", 42))` is 17 columns, and `parse` answers `Bad` because it wants 16.
The shape is correct; its printable domain is names of at most 10 characters and amounts of
0 to 999999. A contract that asked for the round-trip on every `Person` would turn this file,
which builds today, into an error.

```onion
// ContractTable entry for onion.Shape[T]. printable(v) defaults to true.
def print(v: T): String
  requires canPrint() && printable(v)
  ensures parse(result).isOk() && parse(result).get() == v
```

`printable(v)` is a method a shape may override to state which values it can print. Its default
is `true`, which asks for the round-trip on every value.

```onion
class FixedWidth conforms Shape[Person] {
public:
  // ... parse, print and describe as before ...
  def printable(v: Person): Boolean =
    v.name().length() <= 10 && v.name() == v.name().trim()
      && v.amount() >= 0 && v.amount() <= 999999
}
```

**The `trim` condition is not decoration; it was measured.** `parse` trims the name, so a name
with a space at either end does not come back. Run against the real `FixedWidth` of
`run/FixedWidthDemo.on`, with ten names and an amount of 42:

| Domain | Result |
|---|---|
| length at most 10, amount 0 to 999999 | **4 fail**: `"  x  "`, `" AB"`, `"AB "`, `"\t"` |
| the same, and `name == name.trim()` | **0 fail** |

The first draft of this section had only the first row and was wrong. Every value the sampler
draws as a fixed string (`"  x  "`, `"\t"`) hits it, so the domain has to say so.

- **Not weaker than E0080.** E0080 made a shape assert its round-trip *somewhere*. Here, a shape
  that says nothing is held to the round-trip on **every** value, which is stricter. Stating the
  domain is how a correct shape avoids a false error, and the domain is written once, in the
  shape, not in each file that tests it.
- **When `T` cannot be generated** (an interface, an array, a class with several constructors), the
  contract cannot be sampled. The verdict is `Not proved` and the warning `W0022` is reported, on
  by default from stage 3, naming the shape and the type. It is never silently passed: E0080
  refused such a shape outright, and the shape is still held to the contract (it can be tested
  by a closed predicate, §7.2); only the automatic sampling is skipped.
- A **non-invertible** shape has `canPrint() == false`. Its `print` precondition is never met, so it
  owes nothing.
- The usual verdict is `Sampled`: the body is string work the prover does not model. A shape
  over fixed-width integers can be `Proved`.
- `Outcome` is read with `isOk()` and `get()`, the methods it has today.

E0080 is removed only after this entry works (§11).

**After migration, `run/FixedWidthDemo.on`** drops its `example fixedWidthL1`. The `FixedWidth`
class gains the `printable` method above, and nothing else changes. Its round-trip is then
checked against the `Shape` contract, for the values it can print.

What is established today, and what is not: the probe above shows that every value the
`printable` method admits round-trips through the real class. The compiler does not yet draw
those values by itself (that is stage 3), so "the migrated file passes sampling" is a claim
about stage 3 and is not made here.

---

## 6. Where contracts are checked

**The check is static.** A contract is read by the compiler, and the answer comes at compile
time. Running the program is the last resort, used to confirm a counterexample or to guard what
could not be proved (§7.4). It is never the way a contract is *enforced*.

| Context | Behaviour |
|---|---|
| `onion build`, `onionc`, `onion script.on` | The static checks of §7.1 and §7.2, with `--contracts check`; once proofs exist (stage 6), `--contracts verify` adds those of §7.3. **No user code runs for the check itself** (see "What the build runs" below). |
| LSP | The same static checks that are fast enough for an editor: typing, purity, framing and effective-contract diagnostics. Never runs user code, matching the current `checkLaws = false` (#346). No prover until it fits an editor's latency budget. |
| `onion test`, the REPL | Run programs; a contract kept as a guard (§7.4) is checked there. |

**`--contracts <off|check>`, and two reserved names.** A mode is named for what it does today
(this follows the panel's review of PR #2564, which found `runtime` accepted while it checked
nothing):

- `off` (default while this is a preview): a contract clause is E0105, with a hint to use `check`.
- `check`: **what exists.** The clauses are typed, and the static checks of §7.1 run. Nothing is
  enforced when the program runs.
- `verify`: reserved for the proofs of §7.3. The command line refuses it, with a message that says
  to use `check`, until proofs exist.
- `runtime`: reserved for the guards of §7.4, refused in the same way until they exist.

A reserved name is not accepted so that no mode promises a check it does not make. When a name
starts to do something, it is accepted, and it keeps the meaning given here. At the end of the
preview, `verify` becomes the default, because the design's answer is the static one.

**What the build runs.** The static checks run no user code. A **closed predicate** (a `static def`
with no parameters that returns `Boolean`, the `example` replacement) is run at build time, and
so is a **sampled** parameterized predicate. Two different questions decide whether one runs:

| Question | Applies to | Rule |
|---|---|---|
| May a **clause** call it? (§3.1) | `requires`, `ensures`, `invariant`, `history` | The effect set must be empty, `mutate` included: a contract states what holds, and may not write |
| May the **build run** it? | a closed or sampled predicate | The effect set may contain **`mutate`** and nothing from the outside world. The build's concern is I/O, not memory: a predicate that builds a list and reads it is the ordinary case, and it ran as an `example` before |

So a predicate that only writes to memory runs. One whose effects include `read`, `write`,
`net`, `exec`, `env`, `clock`, `rand` or `console` is **not run**, and says so: the warning
`W0023` names the predicate and the effect. One that calls something the effect table cannot
vouch for (`unknown`) gets the same warning. **Nothing stops running silently.** In
`run/`, 10 checks in 9 files (for example `ShoppingCart.on:86`, `GlobMatcher.on:24`,
`Mandelbrot.on:48`) have `mutate` through the methods they call, and they run under this rule;
under the stricter one of the first draft they would not have.

A run is bounded by `--contract-timeout`. A predicate that does not finish is `Not proved`,
with the note naming it, so a non-terminating one cannot hang the build.

A write to a **static** field by a predicate is not stopped by this rule and can reach another
predicate in the same build. Predicates are run one at a time, each in a fresh class loader, so
the static state starts clean for each.

**One rule for `Not proved`.** It is a verdict, and what is shown depends on the stage, not on
the section you read:

| Stage | What `Not proved` shows |
|---|---|
| 1 to 2 | Nothing applies yet: there is no proof and no sampling |
| 3 to 5 | A line in `--contracts-report`. **Exception: a `Shape` whose type cannot be generated** gets the warning `W0022`, on by default, because E0080 used to refuse that shape and the replacement must not be quieter than the error it replaces |
| 6 onward | The same report line, and `W0021` under `--contracts verify` |

So `W0021` is not what a user sees before proofs exist. §2, §6 and §7.4 all mean this table.

**The default per stage.** `--contracts` is `off` in stages 1 and 2, because the checks do not
exist yet. It becomes **`check` at the end of stage 3**, before `law` and `example` are removed
in stage 4: when they go, the form they migrate to is already accepted and checked by default,
so no migrated file needs a flag. `verify` becomes the default when proofs exist (stage 6).

**Flags that replace the old ones:** `--contract-samples <n>` and `--contract-seed <n>`
(sampling), `--contract-timeout <seconds>` (prover), `--contracts-report` (list `Sampled`).

---

## 7. How a contract is checked

Three layers, in the order they are built. Each one is useful before the next exists.

### 7.1 Static checks without a solver

These look at the syntax and the types of the program. They need no proof, and they catch real
mistakes.

| Check | What it decides |
|---|---|
| **Effective contract of an override** (§4) | `pre_eff = pre_super ∨ pre_sub`, `post_eff = (old(pre_super) ⇒ post_super) ∧ (old(pre_sub) ⇒ post_sub)`. An override that restates a precondition more strictly is read as a weakening, so the methods rule holds by construction |
| **A history is a preorder** (§3.3) | Reflexive and transitive, for the relations of the form `x >= old(x)`, `x <= old(x)`, `x == old(x)`, and `old(b) ==> b`. `n == old(n) + 1` is E0109 |
| **Framing** (§3.2) | Rules 1 to 4, transitively through pure methods |
| **A write that breaks a history** (§4.1) | E0108 only for what needs no reachability. See "When E0108 is reported" below |
| **Purity and placement** (§3.1) | E0106, E0107 |

**When E0108 is reported.** A write breaks a history only if the old value can be one it breaks
on, and whether a value is reachable is a question about the program, not about one line of it.
So E0108 is reported only when the syntax settles it. Take the history `n >= old(n)`:

| The write | Result | Why |
|---|---|---|
| `n = n - c`, with `c` a positive constant | **E0108** | `n - c >= n` is false for every `n` (apart from overflow, §3). No reachability is needed |
| `n = c`, and some method of the class or its supertypes **raises** `n` (`n = n + k`, `k` positive) | **E0108**, and the message names that method | `n` can exceed `c`, because that method raises it. The reason is a fact in the syntax |
| `n = c`, and nothing raises `n` | **nothing** | `n` never exceeds its start, so the write cannot lower it. A class whose only write is `n = 0` is fine |
| any other expression | `Not proved` | The prover decides (stage 7) |

The same reasoning applies to a history `n <= old(n)` with the directions swapped, and to
`x == old(x)`, where any write to `x` through a method that is not a constructor is reported.

For `ResettableCounter` (§4.1) the second row applies: `reset` writes `0`, and `Counter.inc`
raises `n`. The error names `inc` as the reason. This is the first thing a user can see work,
with no solver and no run.

### 7.2 Closed and parameterized predicates (the `law` and `example` replacement)

```onion
static def roundTrip(p: Point): Boolean
  ensures result
  = Point::parse(Point::format(p)) == p
```

- A predicate with **parameters** is a property over all inputs. The prover tries first (§7.3).
  Until then, and for what the prover does not model, sampled inputs look for a counterexample
  (`ArgGenerator`) and the verdict is `Sampled`.
- A predicate with **no** parameters is closed. It usually calls other methods, which a prover
  cannot unfold, so it is decided by running it at build time, as `example` is today, under the
  rule in §6.

No new keyword is added. A `check` sugar would be `law` under another name.

### 7.3 Proofs

1. After typing and before tail-call optimization, a new phase reads each method's effective
   contract. Running before TCO matters: the body is still a loop.
2. **Verification conditions.** Weakest precondition over the typed tree. A call is handled
   modularly: assert the callee's effective precondition, forget what it may `modify` (§3.1),
   assume its postcondition.
3. **Encoding.** `Int` and `Long` are 32/64-bit vectors, so wrap-around matches the JVM. A pure
   method with an expression body is inlined to a fixed depth, or is an uninterpreted function.
4. The solver (§8) answers.

A proved postcondition or invariant may drop its guard (§7.4). A proved `requires` may not: a
public method can be called from another compilation unit or from Java, where the caller was
never checked, so a precondition is the one thing the callee cannot assume away.

### 7.4 Running the program: confirming and guarding

Running is the last layer, and it has two jobs.

**Confirming a counterexample.** A prover's model gives field values and arguments. To keep
"every error can be reproduced":

1. **Reachable first.** Build the object with a constructor and a bounded call sequence that
   steers towards the model, then call the method.
2. **If no sequence is found, the model is not an error.** The state satisfies the invariant but
   nothing shows it can be reached. The verdict is `Not proved`, with a note that names the state
   (*"n = 2147483647 satisfies the invariant, and inc() would break it, but no call sequence
   reaching it was found"*). It is a hint to strengthen the invariant, not an accusation. This
   keeps §2's rule that the tool prefers silence to a false error.
3. If the run does not break the contract, the verdict is `Not proved`, not an error.

The `Counter` without headroom shows it: `n = 2147483647` satisfies `n >= 0`, and `inc()` makes
it `-2147483648`. A call sequence that gets there exists (2 billion calls to `inc`), and the
replay finds a shorter route by writing through the constructor of a test subclass only when the
field is reachable that way; otherwise it reports the note above.

**Guarding what was not proved (`--contracts runtime`).** The compiler may keep a guard for a
contract it could not prove. A violated guard throws `ContractViolation`, an `AssertionError`
subclass, with the clause, the method, the values, and the source line:

```
ContractViolation: Counter.inc() postcondition failed at Counter.on:12
  ensures n == old(n) + 1     n = 5, old(n) = 3
```

```
ContractViolation: Counter.inc() の事後条件が満たされませんでした（Counter.on:12）
  ensures n == old(n) + 1     n = 5, old(n) = 3
```

A guard is a plain `AssertionError` subclass, so a violation can be told from an ordinary
exception; it has no code of its own. A guard on a recursive method is not a tail call, so only
the guarded methods lose the tail-call optimization; the rest of the program is compiled as it was.

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

**The case for building it (PRINCIPLES 9).** The static checks of §7.1 already catch a write that
breaks a history, with no solver. They decide only what the syntax settles: a constant, or the
field plus or minus a constant. Only a proof can (a) settle a write the syntax cannot, (b) cover
`2^32` inputs rather than 40, (c) establish a history for **all** call sequences rather than the
ones that were tried, and (d) let a guard be dropped. The cost is bounded by staging: stages 1 to 4
are the static checks and sampling, with no solver. If the solver stages are cut, that is what
remains, and it is already more than `law`/`example` gave.

The solver sits behind a `Solver` interface, so an external z3 or cvc5 can be added later as
another backend.

---

## 9. Error model

E0105 to E0107 are **fixed**: they landed with the syntax (#2564). The rest are provisional and
are assigned when each stage lands.

| Code | Diagnostic | Stage |
|---|---|---|
| **E0105** | A contract clause while contracts are off (`--contracts off`) | 1, landed |
| **E0106** | A contract that is not pure (it does I/O, writes memory, or calls something unvouched for) | 1, landed |
| **E0107** | `old(...)` where there is no earlier state (anywhere but an `ensures` or a `history`) | 1, landed |
| **E0108** | **History broken by a method** (below) | 2 |
| E0109 | A history is not reflexive or not transitive (shows the failing triple) | 2 |
| E0110 | A field named by a contract is written through another object, or is not a value type (§3.2) | 2 |
| E0111 | Contract not allowed here (on a `tool`, or on a class that extends a Java class) | 2 |
| E0112 | Precondition may not hold at a call (names the callee and the arguments found) | 6 |
| E0113 | Postcondition does not hold (shows `old` and `result`) | 6 |
| E0114 | Invariant not preserved by a method or constructor | 6 |
| W0021 | Contract not proved | 6 |

The history error comes from the static check of §7.1, so it **names the write**, not a run:

```
reset.on:12:5: [E0108] ResettableCounter.reset() breaks the history of Counter: n >= old(n)
  `this.n = 0` sets n to 0, but the history says n never decreases.
  hint: a subclass may add methods, but each must keep every history of its superclass.
        Remove the write to `n`, or relax the history in Counter.
```

```
reset.on:12:5: [E0108] ResettableCounter.reset() は Counter の history `n >= old(n)` を破ります
  `this.n = 0` は n を 0 にしますが、history は n が減らないことを求めています。
  ヒント: サブクラスはメソッドを足せますが、スーパークラスの history はすべて保たなければ
          なりません。`n` への代入を外すか、Counter の history を緩めてください。
```

When a write is too hard for the static check (it is not a constant or the field plus or minus
a constant), the prover decides (stage 7), and a counterexample is confirmed by running the
program (§7.4); only then does the message add `reproduced by: new ResettableCounter(); inc();
inc(); inc(); reset()`.

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

**`onion migrate`, with one before and after for each place `law` and `example` appear**

A `law` in a record body becomes a pure predicate method of the record, and an `example` a
closed one. The method is `static`, `Boolean`, and its body is the old block's expression.

```onion
// before: in a record body
record Point(x: Int, y: Int) {
  law roundTrip(p: Point) { Point::parse(Point::format(p)) == p }
  example origin { new Point(0, 0).x() == 0 }
}

// after
record Point(x: Int, y: Int) {
public:
  static def roundTrip(p: Point): Boolean
    ensures result
    = Point::parse(Point::format(p)) == p
  static def origin(): Boolean
    ensures result
    = new Point(0, 0).x() == 0
}
```

```onion
// before: at the top level, unnamed
example { 1 + 1 == 2 }

// after: a top-level function, numbered
def example_1(): Boolean
  ensures result
  = 1 + 1 == 2
```

The `= body` form is why a clause must end at `=` (§3.4). A block with more than one statement
is not rewritten: the rule leaves it alone and says why, since only a file that compiles after
the rewrite is changed.

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

The order is **static checks first, and additive first**: nothing is removed until what replaces
it has a defined verdict, and the checks that need no solver come before the solver.
Each stage is its own PR and is useful alone.

| Stage | Content | `law`/`example` |
|---|---|---|
| 1 | `mutate` effect; `--contracts`; syntax and typing of `requires`, `ensures`, `invariant`, `history`; the purity check. A clause is E0105 while contracts are off. **Landed** (#2563, #2564) | kept |
| 2 | **Static checks without a solver** (§7.1): the framing rules (§3.2), the effective contract of an override, a history is a preorder, a write that breaks a history. `ResettableCounter.reset` is reported here | kept |
| 3 | `ContractTable`, the `Shape` contract and its `printable` domain (§5.1); closed and parameterized predicates (§7.2), with sampling and the `Sampled` verdict | kept (E0080 still on) |
| 4 | **The breaking PR:** migration rule, `run/` migrated, `law`/`example`/E0080 removed, docs and flags. Lands inside the 8-week consolidation window (RUNBOOK §5.4) | **removed** |
| 5 | Solver S1 and S2, with the DRAT checker and the fuzz spec | — |
| 6 | Proofs (§7.3): VCs for straight-line code and loop invariants. `--contracts verify` becomes the default, `W0021` on | — |
| 7 | History VCs; confirming a counterexample by running (§7.4); guide and sample (en/ja) | — |
| 8 | Guards for what was not proved (`--contracts runtime`, §7.4); solver S3 | — |

**The time box.** Stage 4 must land before the consolidation phase ends (RUNBOOK §5.4: about 8
weeks, every audit item closed, no open `breaking` RFC, two weekly releases without a Breaking
entry, dogfood green). After the switch to `mode: stable`, removing `law`/`example` would need a
panel-approved RFC and the `breaking` label for each change to the frozen corpus, which is the
heavier path. Stages 5 to 8 add features and are not tied to the window.

If stages 5 to 8 are never finished, stages 1 to 4 still give contracts, the static checks of
§7.1 (including the Liskov–Wing history check), inheritance of contracts, and sampling. That is
more than `law`/`example` gave.

---

## 12. Open questions

- A guard (§7.4) on a recursive method is not a tail call, so it defeats tail-call optimization on
  that method. Guard only the entry, or document the limit?
- Quantifiers. `forall` over `Int` ranges, or pure recursive helper predicates? Collections need one.
- `ensures` on an exceptional exit.
- Mutable aggregates in `invariant`/`history` (`items.size() <= 10`) need an ownership rule. Not in v1.
- `onion --effects`: it prints `mutate`, since the effect is real; a `tool`'s `--contract` and
  `--plan` output does not, since `mutate` is not a capability. Is that split what users expect?

---

## 13. Rejected alternatives

| Alternative | Why not |
|---|---|
| Keep `law`/`example` and add contracts, for good | Two mechanisms for one purpose (PRINCIPLES 2). They go at stage 4 |
| Remove `law`/`example` first | A stable feature would be replaced by a preview one (PRINCIPLES 3, 6) |
| A `check` keyword that replaces `law` | It is `law` renamed; no complexity removed (PRINCIPLES 9) |
| **Static checks plus sampling, no solver** | Not rejected: it is stages 1–4 (§11). The solver is the extra, and §8 gives the case for it |
| Proofs by an external z3/cvc5 only | Nothing is proved where no solver is installed |
| Runtime assertions as the check | The answer comes after a run, not at compile time, and a history across call sequences is never seen. Kept only as a guard for what was not proved (§7.4) |
| String annotations `@Requires("...")` | The expression is not typed; no editor support; `Annotation` has no arguments |
| Refinement types | A far larger change to the type system |
| Liskov–Wing abstraction functions | Needed only when a subtype changes its representation (§4) |

---

## 14. What this buys, concretely

- A subtype that breaks its supertype's history is rejected, with the call sequence that shows
  it (§4.1, §9). Pre/postcondition checkers miss this.
- A contract that is proved needs no guard (a `requires` excepted, §7.3).
- A broken contract comes with a reproducible counterexample, not a statistic.
- The `Shape` round-trip rule is stated once, on the interface, instead of in every file that
  implements it, and the false rule (L2) is never asserted.
- About 300 build-time test clauses move to one mechanism, after that mechanism works.
