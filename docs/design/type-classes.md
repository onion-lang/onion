# Type Classes for Onion (design)

Status: **design proposal, awaiting designer sign-off.** Rust-trait style, chosen
by the language designer. This document integrates a five-aspect design pass
(syntax/parser, typing/resolution, dictionary-passing codegen, coherence, and
stdlib/derive/roadmap), each grounded in the current compiler, into one plan with
recommended decisions and a staged, independently-shippable roadmap.

## Motivation

Onion has erasure-based generics with no way to express "T is numeric", so
`[1,2,3].sum()` cannot be written as a single generic method (`min`/`max` work
only because `Comparable` is an interface Java already provides). Type classes
give ad-hoc polymorphism: `Numeric`, `Eq`, `Ord`, `Show`, `Monoid`, and turn the
existing `derive!` macro from a serialization one-off into real instance
derivation.

```onion
trait Numeric[T] {
  def zero(): T
  def plus(a: T, b: T): T
}
instance Numeric[Int] {
  def zero(): Int = 0
  def plus(a: Int, b: Int): Int = a + b
}
def sum[T: Numeric](xs: List[T]): T {
  var acc = Numeric[T].zero()
  foreach x: T in xs { acc = Numeric[T].plus(acc, x) }
  return acc
}
```

## Surface syntax

- **`trait Name[T, …] { <sigs + optional default bodies> }`** — a near-clone of
  `interface_decl`, reusing `interface_method_decl` (which already parses both
  abstract signatures and default-method bodies). Super-traits via `<:`
  (`trait Ord[T] <: Eq[T]`), matching interface supertype syntax.
- **`instance TraitName[ConcreteType, …] { <method defs> }`** — body is a flat
  list of `method_decl` like `extension`; the head type list uses
  `type_arguments()` (so `instance Numeric[Int]`, `instance Show[List[String]]`
  parse — `Int` etc. are keyword tokens that expression indexing cannot accept).
- **Context bound `[T: Numeric]`**, multi `[T: Numeric, U: Ord]`, and
  `T: A + B` — `type_params()` gains an optional `:` constraint list joined by
  `+`. Orthogonal to and after the existing `[T extends B]` upper bound.
- **`Numeric[T].method(args)`** — explicit dictionary access, the only call form
  in the MVP (UFCS `acc.plus(x)` is a later stage).

`trait` and `instance` become **reserved tokens** (like `type`/`extension`/
`record`): otherwise the top-level `LOOKAHEAD(2) block_element()` at
JJOnionParser.jj:1076 would grab `trait Foo` as an identifier-expression
statement. The backtick escape (`` `trait` ``) keeps them usable as identifiers.
Add both to `isDeclarationStart` for panic-mode resync.

## Semantics

### Registration
`trait` is registered by the header/outline passes as a nominal type (it erases
to a JVM interface — see codegen). `instance` declarations populate a global
**InstanceRegistry** keyed by `(traitFqcn, erasedHeadTypeKey)`
(`InstanceKey`), living in the per-run typing global state (alongside the F-bound
work in `TypingTypeSupport`). Coherence is checked at registration:
`OVERLAPPING_INSTANCE` on a duplicate key.

### Constraints
`AST.TypeParameter` gains `constraints: List[TypeNode]`; `TypedAST.TypeParameter`
and `NameResolution.TypeParam` gain `constraints`/`traitBounds`. A constraint
`[T: Numeric]` means "an instance `Numeric[T]` must be resolvable"; the
constrained method receives one synthesized **dictionary parameter** per
`(typeParam, constraint)`.

### Instance resolution & inference ordering (critical)
1. First **infer** the method's type arguments from the actual value arguments
   (existing `GenericMethodTypeArguments`). A context bound must **not** satisfy
   inference by itself — `T` must be pinned by a real argument, or `defaultToBound`
   would silently pick the wrong (or no) instance.
2. Then, for each `(typeParam, constraint)`, look up the instance for the now-known
   concrete `T`: `MISSING_INSTANCE` if none. When `T` is itself an abstract type
   parameter of the *caller* (also constrained), forward the caller's dictionary
   instead of looking one up.
3. Thread the resolved dictionary as a trailing hidden argument.

`Numeric[T].method(...)` inside a constrained body resolves to: the passed
dictionary when `T` is abstract, or the concrete instance singleton when `T` is a
ground type.

## Codegen (dictionary passing on the JVM)

- **Trait → interface** `onion.dict.Numeric` with erased method descriptors
  (`zero()Ljava/lang/Object;`, `plus(...)`), lowered from an interface
  `ClassDefinition`. Default trait methods become **JVM default methods**
  (reusing the existing interface default-method support from the #136–156 batch).
- **Instance → synthetic singleton** `onion.dict.Numeric$$Int`: a `final` class
  implementing the interface with a `public static final INSTANCE`, private ctor,
  concrete primitive-typed methods plus auto-generated `ACC_BRIDGE` erased
  methods. Coherence-key name mangling `<traitFqn>$$<erasedTypeKey>`.
- **Constrained method** gets **trailing** hidden params
  `$dict$Numeric$T : onion.dict.Numeric`, one per `(typeParam, constraint)` in a
  deterministic order (trailing minimizes disruption to arg emission and value-param
  indices). Call sites append `RefStaticField(singleton.INSTANCE)` for concrete
  `T`, or `RefLocal(dictSlot)` to forward an abstract `T`.
- **Boxing**: `Numeric[Int]` operates on `Integer` at the interface boundary;
  unbox at the value use sites (same pattern as the `!!`-nullable-primitive fix).
- **Verification**: interface-call results are erased to `Object`; typing must
  insert `AsInstanceOf` when the result is used at a more specific static type
  (the codegen aspect confirmed `visitCall`/`visitCallStatic` do not auto-checkcast).
- **TCO**: exclude trailing dictionary params from `loopVarMapping` so they are
  not copied/rewritten, and include the dict arg in self-recursive calls so
  `isSelfCall` arity still matches (else TCO silently stops firing).
- **ClassWriter**: the main-class writer uses `COMPUTE_FRAMES`; add a
  `getCommonSuperClass` override backed by the in-compilation `ClassTable` so a
  dict/singleton merged at a control-flow join is not classloaded at compile time.

## Coherence & orphan rules

- **One instance per `(trait, erased head-type key)`.** v1 keys by the erased head
  constructor, so `Numeric[List[Int]]` and `Numeric[List[String]]` collide
  (documented limitation until parametric instances land). Normalize primitive vs
  boxed so `Numeric[Int]` and `Numeric[Integer]` are one key.
- **Orphan rule (v1):** an `instance` is allowed only if the trait **or** the head
  type is declared in the same compilation batch, plus the builtin prelude. Cross-
  jar coherence is **not** enforced (Onion has no persisted cross-compilation
  symbol store) — documented, not silently wrong.
- Every consumer of `Method.arguments` (overload specificity in
  `CallOverloadSupport`, vararg/named-arg handling, `TailCallOptimization`,
  `TypingDuplicationPass` erasure-collision, auto-CLI `main` synthesis) must
  **skip trailing dictionary params** so they do not corrupt resolution, arity, or
  usage text.

## Standard library & `derive!`

- **Prelude traits** (Stage 1–2): `Numeric[T]` (zero/plus/times/…), `Eq[T]`,
  `Ord[T]`, `Show[T]`, `Monoid[T]`.
- **Builtin instances** shipped as hand-written Java dictionary classes for the
  primitives (`Numeric[Int/Long/Double]`, `Ord`/`Show` for common types), seeded
  via a `default-instances.txt` manifest (mirrors `default-static-imports.txt`).
  This avoids a prelude-bootstrap dependency; dogfooding via `prelude.on` is a
  later option.
- **`sum`/`product`** are rewritten in terms of `Numeric`; `min`/`max`/`sort` can
  be reframed on `Ord` in a later stage.
- **`derive!` evolves** from a serialization macro into instance derivation
  (`derive!(Eq, Ord, Show)` for records/enums), reusing the existing structural
  `toMap`/fold core.

## Decisions

**Resolved (recommended):**
1. `trait`/`instance` reserved tokens; backtick escape retained.
2. Dedicated `AST.TraitMethodCall` node for `Numeric[T].method(...)` — **not**
   reusing `StaticMethodCall` (safer; keeps trait dispatch separate from static
   resolution).
3. Dictionary params are **trailing**, hidden, one per `(typeParam, constraint)`.
4. Coherence key = `(trait, erased head type)`, v1; overlap/missing are E-codes.
5. Inference pins `T` from arguments first; the context bound never satisfies
   inference; dict lookup follows.
6. Default trait methods = JVM default methods on the dict interface.
7. Builtin instances via Java dict classes + a manifest; not a prelude.on (v1).
8. v1 scope: **single-parameter traits, ground instances only**; UFCS deferred.

**Open (need the designer's call):**
- **A. Dictionary-access syntax.** `Numeric[T].method()` reads naturally but
  `Numeric[T]` is lexically an indexing expression, and it uses `.` while other
  static dispatch uses `::`. Recommendation: keep `Numeric[T].method()` with a
  `dictReferenceFollows()` peeking guard (modeled on `newBracketsAreTypeArgs`) plus
  a typer-side fallback (reinterpret as indexing when the head resolves to a
  value). Alternative: a `::`-based form for consistency. **Which?**
- **B. Orphan-rule strictness.** v1 proposes "trait or head type local to the
  batch" + conflict detection; cross-jar coherence unenforced. Accept, or require
  a stricter/looser rule?
- **C. UFCS timing.** Ship explicit-only (`Numeric[T].plus(a,b)`) in v1 and add
  method-style `acc.plus(x)` in Stage 2 — confirm, since it fixes trait-method /
  extension-method dispatch precedence.

## Staged roadmap (each stage independently shippable, no-crash/no-miscompile bar)

- **Stage 1 — MVP.** Parse `trait`/`instance`/`[T: Numeric]`; register + coherence;
  inference→resolution→trailing-dict codegen with singleton instances;
  `Numeric[Int/Long/Double]` Java dicts; `Numeric[T].method()` access; ship
  `sum`/`product`. Fuzzer + crash-corpus + codegen-correctness coverage.
- **Stage 2 — `Eq`/`Ord`/`Show`.** Traits + instances; reframe `min`/`max`/`sort`
  on `Ord`; UFCS method-style calls for trait methods.
- **Stage 3 — deriving.** `derive!(Eq, Ord, Show)` reusing the structural fold.
- **Stage 4 — advanced.** Trait inheritance (`trait Ord[T] <: Eq[T]`), parametric/
  conditional instances (`instance [T: Numeric] Numeric[List[T]]`), full
  applied-type coherence keys, multi-parameter traits.

## Top risks (from the design pass)

- `COMPUTE_FRAMES` classloading of `onion.dict.*` at compile time → needs the
  `getCommonSuperClass` override.
- Dictionary-param arity leaking into overload resolution / TCO / duplication /
  auto-CLI → every `Method.arguments` consumer must skip trailing dict slots.
- Missing return-value `checkcast` on erased interface calls → typing must insert
  `AsInstanceOf` for specific-typed results.
- Erasure collision `Numeric[Int]` vs `Numeric[Integer]` → normalize the key.
- Bridge-method duplication for specialized instances → verify no `ClassFormatError`
  (the #184 bridge fix is the reference).
