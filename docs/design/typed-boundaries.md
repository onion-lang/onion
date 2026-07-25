# Typed Boundaries — Onion's design thesis

Status: **proposed.** Nothing here is implemented. This document defines what Onion is
*for* from v0.6 onward and the small semantic core it derives from. The evidence it
argues from is in [onion-v0.5-audit.md](onion-v0.5-audit.md); the schedule is in
[roadmap.md](roadmap.md).

Code blocks show **proposed** syntax. `docs/design/` is deliberately outside the scope of
`DocExamplesCompileSpec` (`src/test/scala/onion/compiler/tools/DocExamplesCompileSpec.scala:36`),
so nothing here is compile-checked.

---

## 1. Positioning

> **Onion — typed tools for a messy world.**
> A statically typed language for turning messy external data and operations into
> checked, reversible tools.

Today Onion introduces itself as "an object-oriented and statically typed programming
language" that "compiles into JVM class files" (`README.md:3-4`). Every word of that is
shared with Kotlin, Scala and Java. It is a category, not a reason.

The reason is at the **boundary**: the place where untrusted text becomes a typed value,
and where a program reaches out and changes the world. That is where real programs
actually fail, and it is the one place mainstream languages still hand you a `String` and
wish you luck. Onion's claim is:

- **Describe the boundary once, declaratively.** Both directions, the failure channel and
  the tooling are *derived*, not hand-written.
- **A failure at the boundary is a value that knows where it came from.** Not `null`, not
  a silently dropped line, not `System.exit(1)`.
- **The effects a tool may perform are part of its type**, so a contract can be published
  and a dry run can be trusted.

Onion v0.5 already has four half-built boundary mechanisms (audit §2). This design does
not add a fifth. It names the abstraction they were all approximating and rebuilds them
on it.

### Design constraints this document holds itself to

1. No feature grab-bag — four new concepts, and every existing mechanism is re-derived
   from them or deleted.
2. Not a small version of an existing language — the weirdness must be somewhere no
   mainstream language goes.
3. Failure visibility over convenience — where the two conflict, visibility wins, even at
   the cost of ergonomics.
4. Derive from a small semantic core — concepts must compose, not accumulate.
5. Weirdness must be semantic, not syntactic — cute syntax is not a moat.

---

## 2. The core, in four concepts

Two of these are *boring* — they are what any language doing this seriously would need,
and they are where most of the v0.6 work goes. Two are the *moat*.

| | Concept | Role |
|---|---|---|
| Core 1 | `Origin` | where a runtime value came from |
| Core 2 | `Outcome[T]` / `Defect` | the one failure channel |
| **Core 3** | **`Shape[T]`** | **the data boundary — the boring foundation** |
| **Core 4** | **`tool` / `Capability` / `Plan`** | **the operation boundary — the moat** |

---

## 3. Core 1 — `Origin`

```onion
record Origin(source: String, line: Int, column: Int, span: Int)
```

The runtime counterpart to the compiler's `onion.compiler.Location`. Onion has nothing
like it today: `grep -rniE "provenance|sourceSpan|valueOrigin" src/main src/test` returns
zero matches (audit §5), the two exceptions that *do* carry a position
(`Json.JsonParseException.position`, `Yaml.YamlParseException.line`) have it discarded by
the generated code, and even the compile-time `Location`'s span fields are never set in
production, so every diagnostic caret is a single `^`.

`Origin` is listed first because everything else degrades to hand-waving without it. A
`Defect` with no `Origin` is just a string; a Lens (§9) has nothing to attach to.

`source` is deliberately a free-form string, not a `File` — it names a file path, a URL,
`"<stdin>"` or `"<literal>"` depending on where the text entered the program.

**Companion change (compile-time).** `Location`'s dead `endLine`/`endColumn` span
(`Location.scala:21-22`) gets populated by the parser so `spanLength` stops always
returning 1 and `DiagnosticRenderer.underlineAt` (`:129-138`) can underline a real range.
A language selling diagnostics quality cannot ship one-character carets.

---

## 4. Core 2 — `Outcome[T]` and `Defect`

```onion
record Defect(origin: Origin?, path: String, expected: String, actual: String)

enum Outcome[T] {
  case Ok(value: T)
  case Bad(defects: List[Defect])
}
```

`path` locates the failure *inside* the value (`"status"`, `"items[3].price"`);
`origin` locates it in the *text*. Both are needed: one answers "which field", the other
"which byte".

### 4.1 Why not `Result[T, List[Defect]]`

Because the two have different composition laws, and the difference is the point.

`Result` is monadic: `bind` short-circuits, so the first bad field hides the rest.
`Outcome` additionally offers an **accumulating** product:

```
Ok(f)      zip  Ok(x)       =  Ok((f, x))
Bad(d₁)    zip  Ok(_)       =  Bad(d₁)
Ok(_)      zip  Bad(d₂)     =  Bad(d₂)
Bad(d₁)    zip  Bad(d₂)     =  Bad(d₁ ++ d₂)          ← the reason this type exists
```

This is the standard Validation-vs-Either split, and it is exactly what a boundary needs:
a record with three malformed fields should report three defects in one pass, not one
defect three times. `Result` has no `zip` or accumulating combinator at all today
(audit §6), so this cannot be expressed by reusing it.

`Outcome` still gets `successful`/`bind` so `do[Outcome]` works with the existing
machinery — `bind` short-circuits (it must; the second computation may depend on the
first), `zip` accumulates. Both are available; the derivation in §5 uses `zip`.

### 4.2 What this replaces

Audit §3 lists five conventions for one concept. All five collapse:

| Today | v0.6 |
|---|---|
| `null` on regex no-match (`Rewriting.scala:660`) | `Bad([Defect(origin, "", "<pattern>", line)])` |
| `null` on conversion failure, indistinguishable (`:663-665`) | `Bad` with `expected: "Int"`, `actual: "abc"` — **distinguishable** |
| silently dropped `parseAll` lines (`:683-688`) | every drop is a `Defect` with its line's `Origin` |
| `null` from `fromJson`, position discarded (`:832-834`) | the parser's position becomes an `Origin` |
| `System.exit(1)` from `Cli` (`Cli.java:186-189`) | `Outcome`, returned to the caller |

And two silent-corruption holes close:

- **A missing String key** currently constructs a record with a `null` non-nullable field
  and calls it success (audit §3.2). It becomes `Bad(path: "name", expected: "String",
  actual: "absent")`.
- **`"maybe"` currently parses as `false`** via `Boolean.parseBoolean`
  (`Rewriting.scala:453`, audit §3.3). Boolean conversion becomes strict; anything outside
  the shape's accepted spellings is a `Defect`.

Both are breaking changes and both are the point: constraint 3 says visibility beats
convenience.

---

## 5. Core 3 — `Shape[T]`, the data boundary

A `Shape[T]` is a first-class value describing a **partial, potentially bidirectional**
correspondence between external text and a typed value.

```
Shape[T] = {
  parse : (Text, Origin) -> Outcome[T]
  print : T -> Text                       // present only when derivable
}
```

### 5.1 Semi-formal laws

Write `dom(S) = { t | S.parse(t, _) is Ok }`.

**(L1) Round-trip — print then parse.** For every shape that has `print`:

```
∀ v : T.   S.parse(S.print(v), _)  ==  Ok(v)
```

This is the law worth having, and it is the one a shape must guarantee.

**(L2) Normalization — parse then print.** *Not* guaranteed, and deliberately so:

```
∀ t ∈ dom(S).  S.print(S.parse(t, _).value)  ==  t        -- FALSE in general
```

`"007"` parses to `7` and prints as `"7"`. `{ "a":1 }` and `{"a": 1}` parse alike. A shape
satisfying L2 as well is called **lossless**, and losslessness is rare, valuable, and the
foundation of §9's Lens. Most shapes are L1-only.

Keeping L1 and L2 distinct is the substance behind "reversible". Today's synthesized
`format` conflates them: the comment at `Rewriting.scala:697-703` claims
`parse(format(x)) == x` "for data that doesn't collide with the literals" — an L1 claim
with an unchecked side condition, and nothing verifies it (audit §1, §3).

**(L3) Failure carries position.** `S.parse(t, o)` returning `Bad(ds)` implies every
`d ∈ ds` has `d.origin` derived from `o` — a defect always points somewhere.

### 5.2 Shapes are named values, not record attachments

```onion
record Access(time: String, method: String, path: String, status: Int)

shape AccessLine : Access = re"(\S+) (\w+) (\S+) (\d+)"
shape AccessJson : Access = json
shape AccessCsv  : Access = csv
```

Today a record may have **one** `from re"..."` and a fixed marker set
`derive!(Json, Yaml)`, which bolts nine statics onto the record (`parse`, `parseAll`,
`format`, `toMap`, `fromMap`, `toJson`, `fromJson`, `toYaml`, `fromYaml`). That does not
scale past two formats, and the names collide the moment you want two regex shapes for
the same type — a v1 log line and a v2 log line, say.

Making shapes named values fixes all of it and buys composition:

```onion
val r: Outcome[Access] = AccessLine.parse(line)
val s: String          = AccessLine.print(rec)

// many shapes for one type; pick at the call site
val rows: Outcome[List[Access]] = file"access.log".as(AccessLine.lines)
val body: Outcome[Access]       = http"…/latest".as(AccessJson)
```

### 5.3 Combinators

Shapes compose, which is what makes them an abstraction rather than a fourth macro:

| Combinator | Type | Meaning |
|---|---|---|
| `S.lines` | `Shape[T] -> Shape[List[T]]` | one `T` per line; **every failed line is a `Defect`**, none dropped |
| `S.sepBy(sep)` | `Shape[T] -> Shape[List[T]]` | repetition |
| `S.xmap(f, g)` | `Shape[T] -> (T->U, U->T) -> Shape[U]` | isomorphism; preserves L1 |
| `S.zip(R)` | `Shape[A] -> Shape[B] -> Shape[(A,B)]` | accumulating product (§4.1) |
| `S.orElse(R)` | `Shape[T] -> Shape[T] -> Shape[T]` | first success; on total failure, defects from both |

`xmap` takes a *pair* of functions rather than a single `map` precisely because a shape is
bidirectional: a one-way `map` would silently destroy `print`. That asymmetry is the type
telling the truth.

`S.lines` is where audit §3.1 dies. Its result is `Outcome[List[T]]`, and a run over 1,000
lines with 5 malformed ones yields the 995 values *and* 5 defects with line numbers —
today's `parseAll` returns 995 values and no evidence the other 5 existed.

### 5.4 One conversion registry

The eight-scalar switch that exists five times over (audit §2.1) becomes one registry
mapping a component type to a `Shape` for that scalar. Adding `java.time.LocalDate` means
one entry, not six edits across two languages.

The registry is also the natural place to make Boolean strict (§4.2) and to give each
scalar an *L1-checked* print, since a scalar shape is exactly a tiny bidirectional pair.

### 5.5 Typing rules

Shape formation from a regex, for a record `τ` with components `c₁:τ₁ … cₙ:τₙ`:

```
  Γ ⊢ τ record with components c₁:τ₁ … cₙ:τₙ
  ∀i. τᵢ ∈ ScalarRegistry
  p compiles;  groups(p) = n
─────────────────────────────────────────────── (S-Regex)
  Γ ⊢ shape S : τ = re"p"    ⇒    S : Shape[τ]
```

Failing premises map onto the existing codes, which is deliberate — `E0059` (bad pattern)
and `E0060` (group/component mismatch) already exist and already fire for
`from re"..."` because the desugaring routes through the regex select-pattern
(`typing/SelectExpressionTyping.scala:467-479`, audit §1). `E0061`'s role (unsupported
component type) is taken over by "no entry in the registry".

Print derivability is a *judgement about the shape*, not a silent fork:

```
  segments(p) = literal/slot decomposition
  every slot is flat, top-level and unquantified
──────────────────────────────────────────────── (S-Print)
  Γ ⊢ S has print,  and L1 holds for S
```

When the premise fails, `S` is parse-only and `S.print` is a **compile error naming the
construct that made it non-invertible** — `E0074` below. Today `formatSegments`
(`Rewriting.scala:862-900`) just returns `None` and no `format` appears, so the user gets
"method not found" for a method they were never told would be missing.

---

## 6. Core 4 — `tool`, capabilities, and plans (the moat)

`Shape` types the **data** crossing the boundary. `Capability` types the **effects**
crossing it. Same principle, other axis — which is why this is a completion of the thesis
rather than a second feature.

```onion
tool ingest(src: File, dst: File, dry: Boolean = false)
  requires { read(src), write(dst) }
{
  val rows = file(src).as(AccessLine.lines)
  ...
}
```

One declaration derives four things:

1. **The CLI** — flags, positionals, usage, `--help`, shell completion.
2. **A machine-readable contract** — emitted as a resource and printed by `--contract`.
3. **A capability set** — checked statically against the body.
4. **A plan** — `--plan` reports what the run *would* do, without doing it.

### 6.1 Why this is a moat

Python + argparse, Go's `flag`, Rust's clap and every `#[derive(Parser)]` in between
derive a CLI from a signature. **None of them derive a trustworthy dry run**, because
none of them know what the body does. Effect systems (Koka, Eff, Unison's abilities) know
what the body does but are whole-language type systems with a correspondingly steep price,
and they do not emit tool contracts.

Onion's bet is the narrow intersection: **effects tracked only at the tool boundary,
which is exactly enough to publish a contract and derive a plan.** In an era where the
caller of a script is increasingly an agent rather than a person, "here is a machine-
readable contract, and here is a dry run you can trust" is a concrete, checkable value
proposition. It is semantic weirdness — effects in the type — not syntactic.

### 6.2 Effects and the effect table

```
ε ::= read(p) | write(p) | net(h) | exec(c) | env | clock | rand | console | unknown
```

Audit §7.1 is the good news: only 12 of 45 stdlib classes are effectful, ~110 entry
points, already lexically segregated, and `Resources.java:17-35` is already shaped like a
capability factory.

Audit §7.4 is the bad news: **effects cannot ride on annotations.** The `Method` trait has
no `annotations` member (`TypedAST.scala:1371-1395`), `AsmRefs`/`ReflectionRefs` never
read annotations so nothing on a Java method reaches the type checker, nothing is emitted
to bytecode (`visitAnnotation` → 0 matches), and `MutualRecursionOptimization.scala:321`
drops the field it does have. So:

- effects live in an **out-of-band effect table**, keyed by class + method signature,
  shipped as a resource — the same shape as `default-static-imports.txt`;
- the `Method` trait gains a way to report its effects, defaulting to `unknown` for
  anything the table does not name.

### 6.3 The unknown-effect decision

A Java method not in the table has unknown effects. The two obvious policies are both wrong:

- *unknown = forbidden* kills interop instantly — Onion's whole stdlib story is Java
  interop, and no table will ever cover the JDK plus every jar on the classpath;
- *unknown = harmless* makes the guarantee a lie, which is worse than having no guarantee.

**The proposal is: `unknown` is a real effect that propagates.** It costs nothing inside
ordinary code, but it must be explicitly admitted at a tool boundary:

```onion
tool scrape(url: String) requires { net(url), unknown } { ... }
```

`--plan` then honestly reports `unknown` for the parts it cannot characterize, instead of
claiming a clean bill of health. The user can shrink the surface by adding table entries.
This keeps constraint 3 (visibility over convenience) without making interop unusable.

### 6.4 Ambient effects — the hardest problem

`println`, `readText`, `get` and `System::exit` are callable unqualified from anywhere,
because `onion.IO`, `onion.Files`, `onion.Http`, `onion.DateTime`, `onion.Resources` and
`java.lang.System` are default static imports (audit §7.2).

Tracking their effects still works — resolution knows which method it picked. What breaks
is *legibility*: a reader cannot see that a line is effectful, so `requires` clauses will
look arbitrary. Narrowing the default static import set is the fix, and it is a large
breaking change in its own right, orthogonal to everything else here. It is therefore
scheduled as **its own roadmap item**, not folded into the capability work.

`console` is the pragmatic escape valve: it is the one effect that is likely to be
implicitly permitted, because a `println` in a tool is not what anyone is trying to guard
against.

### 6.5 Typing rules

Effects ride alongside the type as `τ ! ε`:

```
  m ∈ EffectTable,  EffectTable(m) = ε_m
────────────────────────────────────────────── (E-Call)
  Γ ⊢ m(ē) : τ ! ε_m ∪ effects(ē)

  m ∉ EffectTable
────────────────────────────────────────────── (E-Unknown)
  Γ ⊢ m(ē) : τ ! {unknown} ∪ effects(ē)

  Γ, x̄:τ̄ ⊢ body : τ ! ε        ε ⊆ δ
────────────────────────────────────────────── (T-Tool)
  Γ ⊢ tool f(x̄:τ̄) requires δ { body }   ok
```

`ε ⊄ δ` is `E0076`, and the message names both the missing capability *and* the call site
that introduced it — the diagnostic is useless otherwise.

Effects propagate through ordinary functions by inference (no annotation burden), and are
**only declared and checked at `tool` boundaries**. There is no effect polymorphism, no
effect variables in ordinary signatures, no handlers. That restraint is what keeps this
from being a small version of Koka (constraint 2).

### 6.6 Erasure — a hard feasibility constraint

Audit §9: there is **no IR between the typed AST and codegen**. `TypedAST` is the type
checker's output, the optimizers' mutable working representation and the ASM backend's
input all at once, so there is nowhere to put a lowered, effect-annotated form.

Therefore **effects must be completely erased inside `Typing`**. A `tool` lowers to:

- an ordinary static method with the declared parameters,
- a synthesized contract constant (the machine-readable descriptor),
- a synthesized entry point that parses argv via the parameters' shapes and dispatches.

The ASM backend learns nothing new. This is not an optimization — it is the condition
under which the feature is buildable at all without first introducing an IR.

---

## 7. Error model

New codes continue from the current maximum, `E0073` (`SemanticError.scala:141`):

| Code | Meaning |
|---|---|
| `E0074` | `print` used on a shape that is not invertible — message names the construct responsible |
| `E0075` | no scalar-registry entry for a component type (replaces `E0061`/`E0062`'s role) |
| `E0076` | a tool's body performs an effect its `requires` clause does not admit |
| `E0077` | a `requires` clause admits a capability the body cannot perform (dead capability) |
| `E0078` | a law's parameter type has no generator — **an error, not a silent skip** |

Every one gets an entry in **both** `errorMessage.properties` and
`errorMessage_ja.properties`. That is worth stating because `E0064`/`E0065` are today the
only codes that bypass the i18n table entirely, with English hard-coded at
`LawCheckPhase.scala:69,72,90,95` (audit §4).

`E0078` deserves emphasis. Onion's headline claim is that `parse ∘ print == id` is
machine-checked at build time. Today a law whose parameter type has no generator is
skipped with `return` and no diagnostic (`LawCheckPhase.scala:80-81`), and there is no
test covering it. **A verification that can silently not run is worse than no
verification**, because it produces unearned confidence. Turning that skip into an error
is a prerequisite for the whole Shape story, not a nicety — which is why it lands in v0.6
*before* `Shape` (see roadmap).

---

## 8. Compatibility — the breaking changes

Onion has one user, so these are affordable. Listing them explicitly is the point.

| # | Change | Rationale |
|---|---|---|
| 1 | `R::parse` returns `Outcome[R]`, not `R?` | no-match and conversion-failure become distinguishable (audit §3) |
| 2 | `parseAll` → `S.lines`, returning `Outcome[List[R]]` | dropped lines become visible defects (audit §3.1) |
| 3 | `fromJson` returns `Outcome[R]`; a missing `String` key is a defect | closes the silent-corruption hole (audit §3.2) |
| 4 | Boolean conversion is strict | `"maybe"` is a defect, not `false` (audit §3.3) |
| 5 | `onion.Cli` no longer calls `System.exit` | makes CLI failures catchable and testable (audit §3) |
| 6 | `record … from re"…"` / `derive!(…)` replaced by named `shape` declarations | one type may have many shapes; removes nine bolted-on statics (§5.2) |
| 7 | `FileResource`/`HttpResource`'s fixed method menu replaced by `.as(shape)` | a fixed menu cannot be extended by a user (audit §2) |
| 8 | law checking is off by default in the LSP; `--check-laws` controls it elsewhere | the language server currently executes user code on every validation (audit §4) |
| 9 | *(v0.7)* the default static import set narrows | ambient effects are unreadable (§6.4) |

Changes 1–5 are mechanical at the call site. Change 6 is a rewrite of every shape
declaration. Change 9 is the largest and is scheduled separately.

---

## 9. Reversibility (v0.8, sketched)

A **lossless** shape satisfies L2 as well as L1 (§5.1): parsing and reprinting is the
identity on the text, comments and whitespace included. That makes a *lens* possible —
read a config, change one field, write it back, and the diff is one line:

```onion
val cfg = file"app.conf".as(ConfShape)     // lossless
val out = ConfShape.print(cfg.value.with(port = 9090))
```

This is worth flagging as **the payoff that motivates `Origin`**. Provenance is not
decoration: it is what lets the printer put untouched text back exactly where it was.

Deliberately scheduled last. Attempted first, it would require building `Origin`,
`Outcome` and `Shape` anyway, plus a residue representation — with none of the earlier
pieces available to test against.

---

## 10. Rejected alternatives

**Lossless Lens as the v0.6 headline.** This was the original nomination for the moat.
Rejected as the *first* move: there is no runtime provenance type to attach a lens to
(audit §5 — zero matches for `provenance`/`sourceSpan`/`valueOrigin`), so it would mean
building the entire foundation *and* the hardest feature simultaneously, with nothing to
validate the foundation against. It becomes cheap once `Origin` exists, so it is v0.8 (§9).

**A general effect system (algebraic effects, handlers, effect polymorphism).** Rejected
under constraint 2: it makes Onion a small version of Koka, costs a whole-language type
system change, and is not what produces the value. The value is the *contract at the
boundary*; effects on every function are the expensive part with the worst
ergonomics-to-payoff ratio. Effects are declared and checked **only at `tool` boundaries**.

**Carrying effects into codegen.** Rejected on feasibility: there is no IR between
`TypedAST` and ASM emission (audit §9), so this would mean teaching a 2,905-line backend
about a new type-level concept, or building an IR first. Effects erase inside `Typing` (§6.6).

**Keeping `from re"…"` syntax and swapping only the internals.** Rejected because
breaking changes are affordable here and the syntax should name the concept. The
attachment form also structurally caps a type at one regex shape and a fixed marker list
(§5.2) — the limitation is in the syntax, not the implementation.

**`Shape` as an open type class over arbitrary user types.** Deferred, not rejected
outright. Onion has type classes (`docs/design/type-classes.md`), so `Shape[T]` as a
class with user-written instances is tempting. But the first version needs a *closed*,
derivable set with an explicit escape hatch, so that "the compiler knows whether `print`
exists and why" (§5.5) stays decidable. Opening it is a natural v0.8+ step once the
closed core has proven its laws.

**Refinement or dependent types for validation.** Rejected under constraint 1 and cost:
`status: Int where 100 <= status < 600` is attractive, but it is a second, much larger type
system serving a narrow slice of the boundary story, and `Shape` + `Outcome` already
express the same constraint as a parse-time defect with a position — which is strictly
more useful at a boundary, because it can say *where* in the input the bad value was.

**Making `Outcome` an alias for `Result[T, List[Defect]]`.** Rejected: the accumulating
`zip` (§4.1) has different laws from `Result`'s short-circuiting `bind`, and conflating
them is how "report all three bad fields" quietly becomes "report the first bad field".

**Fixing the four mechanisms in place instead of unifying them.** Rejected as the
non-solution it is: it would mean five copies of the scalar table instead of five, five
failure conventions instead of five, and no answer to "what *is* Onion for".

---

## 11. What this buys, concretely

Three demos, one per milestone, each impossible or silently wrong today.

**Repair a broken log (v0.6).** 1,000 access-log lines, 5 malformed. `AccessLine.lines`
returns 995 typed values *and* 5 defects with line numbers and reasons. Today `parseAll`
returns 995 values and no trace of the other 5 (audit §3.1).

**A tool an agent can call (v0.7).** One `tool` declaration yields a CLI, `--help`, a
machine-readable contract and `--plan`. An agent reads the contract, runs `--plan` to see
that it would write two files and hit one host, and only then executes. Today the CLI
"contract" is a comma-separated string with the types erased
(`Rewriting.scala:371-376` → `Cli.java:26-42`) and there is no dry run at all.

**Edit a config without destroying it (v0.8).** Read a commented config, change one
field, write it back with comments and formatting intact.
