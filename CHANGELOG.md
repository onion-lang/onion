# Changelog

All notable changes to Onion are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- **An if-expression target-types to the expected type.**
  `val e: Event = if b { new Click() } else { new Key() }` and
  `val x: String? = if b { "a" } else { null }` adopt the expected type instead of
  failing on the branches' widened join ([#247]).

- **A map literal target-types its keys and values.** `Map[String, String?]`,
  `Map[String, Shape]` (mixed record subtypes) and other nullable/supertype map
  annotations are honored instead of failing on the entries' widened join, matching
  list literals ([#246]).

- **A list literal of mixed subtypes target-types to the expected supertype.**
  `val es: List[Event] = [new Click(..), new Key(..)]` builds a `List[Event]`
  instead of failing as `List[Object]` (the check now considers each element, not
  their widened join) ([#245]).

- **A bean-property accessor is callable with parens.** `e.message()` resolves to
  `getMessage()` like `e.message` already did (parens optional on property
  accessors, as on no-arg methods); a same-named user extension still wins
  (method > extension > getter) ([#244]).

- **Compiler crash fixed: `!!` inside a lambda.** A non-null assertion in a
  closure body crashed codegen (I0000); the closure's captured-variable collector
  now handles it. This also unblocks a type-class method used inside a lambda in a
  constrained function (it lowers to `dict!!.method(...)`) ([#243]).

- **Compiler crash fixed: integer-literal overflow.** `-2147483648`,
  `-9223372036854775808L`, and out-of-range literals no longer crash the parser
  with an internal error (I0000); `Int.MIN`/`Long.MIN` magnitudes parse and a
  genuinely out-of-range literal is a clean error ([#241]).

- **A bodyless interface parses**, like a bodyless class already did:
  `interface Marker`, `interface Marker;`, and `sealed interface Shape` (with the
  subtypes declared separately) no longer require an empty `{}` ([#240]).

- **A unary-minus right operand of `*`, `/`, `%` parses.** `7 * -3`, `7 / -3`,
  `7 % -3` were syntax errors (only `+`/`-` accepted one); left-associativity and
  precedence are unchanged ([#239]).

- **Miscompile fixed: `synchronized` + non-local exit.** A `return`/`break`/`continue`
  out of a `synchronized` body now releases the monitor (it used to leak it and
  throw `IllegalMonitorStateException`); a synchronized body that always returns is
  recognized as terminal (no false E0067) ([#238]).

- **Miscompile fixed: `finally` skipped on non-local exit.** A `finally` block now
  runs when the `try` or a `catch` exits via `return`, `break`, or `continue` (it
  was silently skipped, losing cleanup and side effects) ([#237]).

- **Miscompile fixed: self-referential typed local initializer.** `val x: T = x`
  loaded an uninitialized slot (VerifyError); it is now a clean "variable not
  found" error, like the type-inferred form ([#236]).

## [0.4.0] - 2026-07-03

The headline is **type classes**; also a batch of soundness and diagnostic fixes
surfaced by a gap-probe of real programs.

- **Type classes (Rust-trait style).** Declare a `trait Numeric[T] { def zero(): T;
  def plus(a: T, b: T): T }`, give it instances (`instance Numeric[Integer] { ... }`),
  constrain a generic with `[T: Numeric]`, and call trait methods via
  `Numeric[T]::zero()`. A constrained generic like
  `def sum[T: Numeric](xs: List[T]): T` works polymorphically — the compiler passes
  the right instance (a *dictionary*) at each call site and forwards it through
  constrained callers. Coherence is one instance per `(trait, type)` (primitive and
  boxed unify, so `Numeric[Int]` == `Numeric[Integer]`); a missing instance is a
  clean compile error. Ground `Trait[Int]::method(...)` also resolves directly. v1
  covers single-parameter traits, method/function-level constraints, and ground
  instances ([#224]).

### Correctness & diagnostics

Soundness and diagnostic holes surfaced by a gap-probe:

- A qualified assignment to a nonexistent field (`obj.field = v`) is now a compile
  error instead of a silent no-op that swallowed typos ([#225]).
- A `record` that leaves an implemented interface's abstract method unimplemented
  is now rejected at compile time (E0037) instead of throwing `AbstractMethodError`
  at runtime ([#226]).
- A body-less abstract method in a class may be followed by another member;
  previously that was a syntax error unless the abstract method was last ([#227]).
- A `new` on an inaccessible (private/protected) constructor is a compile error
  instead of a runtime `IllegalAccessError` ([#228]).
- A nullable primitive compares to a plain primitive with `==` (`Int? == Int`),
  matching `String? == String` ([#229]).
- A collection literal adopts the expected element type when its elements fit, so
  `val xs: List[String?] = ["a", null]` builds a `List[String?]` ([#231]).
- A field or `forward` member may be the last member on the same line as the
  class's closing brace (`class C { public: var x: Int = 0 }`) ([#234]).
- A `new` on / `C::m()` call to an inaccessible static method is a compile error
  instead of a runtime `IllegalAccessError` ([#235]).

## [0.3.8] - 2026-07-02

- **`re"..."` literals interoperate with the `Regex::` helpers.** A `re"..."`
  literal is a `java.util.regex.Pattern`; `Regex::matches`/`find`/`findAll`/
  `findFirst`/`groups`/`groupsAll`/`replace`/`replaceFirst`/`split` now have
  `Pattern` overloads, so `Regex::matches(s, re"...")` works (String overloads
  unchanged) ([#222]).

## [0.3.7] - 2026-07-02

- **Bare enum constants in `select`.** Over an enum scrutinee, `case CONST:`
  resolves to `EnumType::CONST` (for both matching and exhaustiveness) instead of
  failing with E0002; a local variable of the same name still takes precedence ([#220]).
- **List `take`/`drop`/`reverse`/`first`/`last` are no longer ambiguous.** These
  were declared with identical signatures in both `onion.Colls` and
  `onion.Iterables`, so `xs.take(2)` failed with E0006. Builtin extension methods
  are now deduplicated by signature; static calls and the default import are
  unaffected ([#221]).

## [0.3.6] - 2026-07-02

- **F-bounded type parameters.** A self-referential bound such as
  `T extends Comparable[T]` now resolves, on both generic classes and generic
  methods (previously E0003) — the parameter is in scope while its own bound is
  resolved ([#219]).
- Documented map index read/write (`m[k]`, `m[k] = v`, `m[k] += v`) in the
  collections guide.

## [0.3.5] - 2026-07-02

- **Compiler crash fixed: safe call on a nullable primitive.** `o?.method()`
  where `o` is `Int?`/`Long?`/`Double?`/`Boolean?` compiled with an internal
  error (I0000, "not a boxable type"); it now retypes the already-boxed value to
  its wrapper class instead of re-boxing ([#218]).

## [0.3.4] - 2026-07-02

- **SAM overloads are disambiguated by the lambda body.** A lambda passed to an
  overloaded method that has both a value-returning and a void functional-interface
  parameter — e.g. `ExecutorService.submit`, with `submit(Callable[T])` and
  `submit(Runnable)` — now resolves by the lambda: a value-producing body
  (`() -> 42`) picks the non-void SAM, a void body (`() -> { ... }`) picks the
  void SAM, instead of reporting an ambiguity (E0006) ([#210]).

## [0.3.3] - 2026-07-02

- **Map indexed assignment.** `m[k] = v` and `m[k] += v` now work — indexed
  assignment falls back to `put(key, value)` for Map-style containers (`list[i] = v`
  still uses `set`) ([#217]).
- **Integer literal narrowing.** An in-range integer literal (or its negation)
  target-types to a `Byte`/`Short`/`Char` assignment (`val b: Byte = 100`,
  `val s: Short = -32768`), like Java; out-of-range literals are still rejected.
  (Method-argument position is not covered; use `(x as Byte)` there.) ([#215]).

## [0.3.2] - 2026-07-02

Fixes surfaced by a deeper pre-release gap-probe of real programs.

- **Miscompile fixed: `!!` on a nullable primitive.** `n!!` for `Int?`/`Long?`/
  `Double?`/`Boolean?` now unboxes to the primitive instead of leaving a boxed
  value on the stack (which produced a `VerifyError`) ([#216]).
- **Elvis `?:` accepts a nullable right operand.** `a ?: b` where both are `T?`
  yields `T?`, which also makes the chained `a ?: b ?: c` idiom type-check ([#209]).
- **Dotted (fully-qualified) name as a type argument.**
  `java.util.List[java.lang.Integer]` parses in type-annotation positions and in
  `new` with a constructor `()` (a `new C[FQN]` without `()` still needs an
  import) ([#213]).

## [0.3.1] - 2026-07-02

Resolves the three limitations deferred from 0.3.0.

- **SAM conversion for a lambda cast with `as`.** `(() -> ...) as Runnable` (and
  `... as Comparator[Int]`) is SAM-converted instead of rejected ([#205]).
- **Static generic method with an explicit primitive type argument.**
  `Util::identity[Int](99)` boxes the primitive argument instead of failing with
  E0005 ([#207]).
- **Auto-CLI supports a trailing `String[]` rest parameter.**
  `def main(cmd: String, files: String[])` collects the remaining arguments into
  `files`; too few arguments fails with a usage message instead of silently doing
  nothing ([#208]).

## [0.3.0] - 2026-07-02

Builds on 0.2.0 with type-soundness fixes, ergonomic syntax, richer Java interop,
and clearer diagnostics — accumulated across the `0.3.0-M1`…`0.3.0-M5` milestones
and hardened by a pre-release gap-probe of real programs.

### Highlights
- **Soundness:** generic type arguments are invariant; a non-exhaustive `select`
  over a sealed type is a compile error (E0042) instead of a silent `null`; a
  value-returning method that can fall off its end is rejected (E0067).
- **Java interop:** static calls on fully-qualified names (`java.lang.Math::max`)
  and on basic-type keywords (`Long::toString`); SAM conversion in constructor
  arguments (`new Thread(() -> ...)`).
- **`forward` over a parameterized generic interface** now works.
- **Ergonomics:** constructor diamond, elvis with a control-expression right-hand
  side, one-line method declarations, argument-position empty literals, and
  smart-cast of a `val` nullable field.
- **Diagnostics & docs:** source-form types, clearer end-of-file errors, new codes
  E0066/E0067; English + Japanese documentation synced, every example verified
  against the compiler.

### Pre-release quality pass
A multi-domain gap-probe against the release candidate surfaced six issues; the
three quality-critical ones were fixed for 0.3.0:
- A non-exhaustive `select` over a sealed class/interface with non-record subtypes
  no longer returns `null` silently — it is a compile error (E0042) ([#203]).
- SAM conversion applies to constructor arguments, e.g. `new Thread(() -> ...)`
  ([#204]).
- `DateTime::parse` handles date-only patterns and fails loudly instead of
  silently returning epoch 0 ([#206]).

### 0.3.0-M5 — interop & diagnostics

- **Basic-type keywords as static receivers.** `Long::toString(42L)`,
  `Int::parseInt("7")`, and `Long::MAX_VALUE` now work — a basic-type keyword
  receiver maps to its boxed class. (A fully-qualified path ending in a keyword,
  `java.lang.Long::`, still doesn't parse; use the simple `Long::` form.) ([#202])
- **Clearer end-of-file syntax errors.** An unexpected EOF now reports
  "unexpected end of file — a closing `}` or `)` is probably missing" instead of
  dumping the raw expected-token list ([#201]).

### 0.3.0-M4 — interop, null safety & docs

- **Static calls on fully-qualified names.** `java.lang.Math::max(3, 7)` and
  `java.lang.Integer::MAX_VALUE` work without an import. (Basic-type keyword
  receivers such as `Long::` remain unsupported — those are keywords, not
  identifiers.) ([#197])
- **Smart-cast of a `val` nullable field.** Inside `if field != null { ... }` an
  immutable nullable field is narrowed to non-null, like a local ([#200]).
- Syntax errors that reach end-of-file now hint that a closing `}`/`)` is likely
  missing, rather than only listing expected tokens ([#201]).
- **Documentation synced (English + Japanese)** with the 0.3.0 feature set: null
  safety (elvis for nullable primitives, elvis with a `throw`/`return` right-hand
  side, `val`-field smart cast), `forward` over a generic interface, constructor
  diamond and type-argument invariance, fully-qualified static calls, argument-
  position empty literals, and new error codes E0066/E0067. Every code example is
  verified against the compiler.

### 0.3.0-M3 — ergonomics & syntax

- **Constructor diamond.** A generic constructor may omit its type arguments when
  the expected type pins them: `val b: Box[String] = new Box("x")` (previously
  E0066) ([#199]).
- **Elvis right-hand side may be a control expression.** `x ?: throw ...` and
  `x ?: return ...` now parse, so a null-check early-exit fits on one line ([#198]).
- **One-line method declarations parse.** A no-body method immediately followed by
  `}` on the same line, e.g. `interface A { def f(): Int }`, is no longer a syntax
  error ([#196]).

### 0.3.0-M2

- **`forward` delegation over a parameterized generic interface now works**
  (`forward val x: List[String]`, `Map[String, Int]`, a user-defined
  `Container[Int]`). The underlying fix corrects a bridge-generation
  miscompilation: a method declared at several levels of a generic hierarchy
  (e.g. `addLast` on both `List` and `SequencedCollection`) produced a *duplicate*
  bridge method (`ClassFormatError`), which also affected hand-written classes
  implementing such hierarchies ([#184]).
- **Empty collection literals are target-typed at argument position.** `foo([])`
  and `foo([:])` now bind the empty literal to the parameter's element type, for
  top-level, instance, static, and constructor calls ([#193]).

### 0.3.0-M1 — correctness & diagnostics

Type-soundness and diagnostic-quality fixes surfaced by gap-probing real programs.

- **Type-argument invariance.** Generic type arguments are now invariant:
  `Box[Dog]` is no longer assignable to `Box[Animal]`, closing a heap-pollution
  hole that produced a `ClassCastException` at a distant read ([#186]).
- **Missing-return analysis.** A value-returning method whose block body can
  complete without returning is rejected (`E0067`) instead of silently returning
  the JVM default (`0`/`null`) ([#185]).
- **Elvis for nullable primitives.** `n ?: -1` (and `s?.length() ?: -1`) works
  for `Int?`/`Long?`/`Double?`/`Boolean?`, not only for nullable references ([#187]).
- **No more compiler crash** on a throw-only lambda with an inferred return type
  (was a `VerifyError`) ([#188]).
- A top-level `def main(args: String[])` is now used as the entry point instead
  of colliding with the synthesized `main` ([#189]).
- Writing a non-public field from outside its class is rejected (`E0014`) rather
  than silently ignored ([#190]).
- An invalid `re"..."` literal is a compile-time error (`E0059`) in any position,
  not just inside `select`/`from` ([#191]).
- A top-level `break`/`continue` followed by a statement reports the real cause
  (`E0048`/`E0049`) instead of a misleading next-line syntax error ([#192]).
- Diagnostics render types in Onion source form (`List[String]`) rather than the
  JVM form (`java.util.List[java.lang.String]`), consistent with the REPL, which
  also shows results as `resN: Type = value` ([#194]).
- Reduced the VS Code extension's npm audit alerts (dev tooling) ([#195]).

### Known limitations (deferred to 0.3.1)
- A lambda cast to a functional interface with `as` (`(() -> ...) as Runnable`)
  is not SAM-converted; use a typed target instead (`val r: Runnable = () -> ...`)
  ([#205]).
- A static generic method called with an explicit primitive type argument and a
  primitive value (`Util::identity[Int](99)`) fails; omit the type argument
  (`Util::identity(99)`) or use a reference type ([#207]).
- A top-level `main` mixing scalar and array parameters is not auto-CLI-derived;
  use `main(args: String[])` ([#208]).

## [0.2.0] - 2026-07-02

First practical, stable release. Onion is now usable for real programs — from
quick scripts to small applications — with thorough English and Japanese
documentation. This section summarizes the user-facing changes accumulated
across the `0.2.0-M2`…`0.2.0-M14` milestones and the final stabilization work.

### Highlights
- A statically-typed, null-safe, object-oriented language on the JVM with
  seamless Java interop and a batteries-included standard library.
- **No raw generic types.** Every generic type must supply its type arguments
  (`List[Int]`, `Map[String, Int]`, `ArrayList[String]`), so element types are
  always explicit. See *Breaking Changes* below.
- Stability bar for this release: the compiler must never crash and must never
  emit invalid bytecode. Enforced by a mutation fuzzer, a crash-reproducer
  corpus, and end-to-end codegen-correctness tests.

### Language
- **Null safety:** nullable types `T?`, safe call `?.`, elvis `?:`, not-null
  assertion `!!`, safe index `?[ ]`, smart casts after `if x != null`, and
  null-safe value equality (`==` uses `java.util.Objects.equals`).
- **Records:** `record Point(x: Int, y: Int)` with accessors, positional and
  named `copy`, positional destructuring, and data-carrying `enum`s.
  - `record R(...) from re"..."` synthesizes a typed `parse`/`parseAll`.
  - `record R(...) derive!(Json, Yaml)` macro-derives serialization.
  - `record R(...) law name(...) { ... } example { ... }` runs properties and
    examples at compile time (E0064 / E0065 on failure).
- **Shape-first scripting:** scheme-prefixed raw literals `re"..."`,
  `file"..."`, `http"..."`; the `|>` pipeline operator; regex literals as
  compile-checked `select` patterns; `do[Future]` / `do[Option]` / `do[List]` /
  `do[Result]` monadic notation; and auto-CLI argument parsing derived from a
  top-level `main` signature.
- **Control flow:** `if`/`else if` as expressions, `select` with value, type
  (`is`), record-destructuring, and guard (`when`) patterns plus sealed
  exhaustiveness checks; labeled `break`/`continue`; inclusive/exclusive ranges
  (`a..b`, `a..<b`); C-style `for` and `foreach`.
- **Functions and lambdas:** bare and typed lambda parameters, expression
  bodies, trailing-lambda syntax, method references, and SAM conversion to Java
  functional interfaces — including primitive-typed parameters for generic
  interfaces such as `Comparator[Int]`, `Predicate[Int]`, and `Function1[Int, Int]`.
- **OOP:** primary constructors, `extension` blocks, `forward` delegation,
  implicit field access, and `self`/`this`.
- **Generics:** erasure-based generics with compile-time nullability tracking
  (`[T]` is nullable, `[T extends B]` is non-null). Primitive type arguments are
  supported and auto-boxed (`List[Int]` behaves like `List[Integer]`), including
  when implementing Java generic interfaces.
- Empty collection literals (`[]`, `[:]`) are target-typed from the expected
  type, so `val xs: List[Int] = []`, `return []`, and `this.f = []` all work.

### Standard library
- Core: `IO`, `Strings`, `Rand`, `Assert`, `Timing`, `Files`, `DateTime`.
- Data & IO: `Json`, `Http`, `Regex`.
- Functional: `Option`, `Result`, `Future`.
- Collections: `Colls`, `Iterables` (including `sort(List, Comparator)`),
  and new `Maps` (`getOrDefault`, `filterKeys`, `filterValues`, `mapValues`,
  `merge`, `newMap`) and `Sets` (`newSet`, `of`, `union`, `intersection`,
  `difference`, `containsAll`).
- Common console functions such as `println` are available unqualified via
  default static imports.

### Tooling
- `onionc` (compiler), `onion` (script runner, with `--watch`), and
  `onion-repl` (REPL).
- Language server (`onion-lsp`) with diagnostics, symbol-based completion,
  workspace symbol search, document symbols (incl. fields and records), hover,
  signature help, rename, and syntax-error hints for common mistakes.
- One-line installer: `curl -fsSL .../install.sh | sh` (AppCDS-accelerated
  startup), automated release versioning via
  [sbt-dynver](https://github.com/sbt/sbt-dynver), versioned artifacts, and
  SHA-256 checksums on GitHub Releases.

### Documentation
- Complete English and Japanese documentation: getting started, a language
  guide, an examples collection (basic, OOP, functional, scripting/CLI,
  JSON/HTTP, async, error handling), tool guides, and a language reference
  (specification, standard library, error codes). Every runnable code example is
  verified against the compiler.

### Compiler quality and robustness
- Invalid casts such as `String as Int` and `null as Int` are rejected at
  compile time instead of failing at runtime.
- Fixed code generation defects that produced invalid bytecode: an empty
  `try {}` block (illegal exception table), a value-returning closure whose only
  value was `null` (operand stack underflow), and a `do`-block with a
  misplaced/duplicate `ret` (now a clean diagnostic).
- Added a deterministic mutation fuzzer, a growing crash-reproducer corpus, and
  end-to-end codegen-correctness tests to guard the no-crash / no-miscompile
  invariants.

### Breaking Changes
- **Raw generic types are no longer allowed (E0066).** A generic type used
  without type arguments is a compile error. Update declarations, `new`
  expressions, and annotations to be explicit:
  - `val xs: List = [1, 2, 3]` → `val xs: List[Int] = [1, 2, 3]`
  - `new ArrayList()` → `new ArrayList[String]()`
  - `def f(): Map` → `def f(): Map[String, Int]`
  Erasure-only positions keep raw types: `is`/`as`, static-call receivers,
  `catch`, `extension` receivers, and `foreach (k, v)` entry variables.

### Known limitations
- `forward` delegation over a parameterized generic interface (e.g.
  `forward val x: List[String]`) does not yet compile; delegate over a
  non-generic interface instead ([#184]). *(Resolved in 0.3.0-M2.)*

## [0.1.0] - 2019-XX-XX

### Added
- Initial release.

[Unreleased]: https://github.com/onion-lang/onion/compare/v0.4.0...develop
[0.4.0]: https://github.com/onion-lang/onion/releases/tag/v0.4.0
[0.3.8]: https://github.com/onion-lang/onion/releases/tag/v0.3.8
[0.3.7]: https://github.com/onion-lang/onion/releases/tag/v0.3.7
[0.3.6]: https://github.com/onion-lang/onion/releases/tag/v0.3.6
[0.3.5]: https://github.com/onion-lang/onion/releases/tag/v0.3.5
[0.3.4]: https://github.com/onion-lang/onion/releases/tag/v0.3.4
[0.3.3]: https://github.com/onion-lang/onion/releases/tag/v0.3.3
[0.3.2]: https://github.com/onion-lang/onion/releases/tag/v0.3.2
[0.3.1]: https://github.com/onion-lang/onion/releases/tag/v0.3.1
[0.3.0]: https://github.com/onion-lang/onion/releases/tag/v0.3.0
[0.2.0]: https://github.com/onion-lang/onion/releases/tag/v0.2.0
[0.1.0]: https://github.com/onion-lang/onion/releases/tag/releases/0.1
[#184]: https://github.com/onion-lang/onion/issues/184
[#193]: https://github.com/onion-lang/onion/issues/193
[#196]: https://github.com/onion-lang/onion/issues/196
[#198]: https://github.com/onion-lang/onion/issues/198
[#199]: https://github.com/onion-lang/onion/issues/199
[#197]: https://github.com/onion-lang/onion/issues/197
[#200]: https://github.com/onion-lang/onion/issues/200
[#201]: https://github.com/onion-lang/onion/issues/201
[#202]: https://github.com/onion-lang/onion/issues/202
[#185]: https://github.com/onion-lang/onion/issues/185
[#186]: https://github.com/onion-lang/onion/issues/186
[#187]: https://github.com/onion-lang/onion/issues/187
[#188]: https://github.com/onion-lang/onion/issues/188
[#189]: https://github.com/onion-lang/onion/issues/189
[#190]: https://github.com/onion-lang/onion/issues/190
[#191]: https://github.com/onion-lang/onion/issues/191
[#192]: https://github.com/onion-lang/onion/issues/192
[#194]: https://github.com/onion-lang/onion/issues/194
[#195]: https://github.com/onion-lang/onion/issues/195
[#203]: https://github.com/onion-lang/onion/issues/203
[#204]: https://github.com/onion-lang/onion/issues/204
[#205]: https://github.com/onion-lang/onion/issues/205
[#206]: https://github.com/onion-lang/onion/issues/206
[#207]: https://github.com/onion-lang/onion/issues/207
[#208]: https://github.com/onion-lang/onion/issues/208
[#209]: https://github.com/onion-lang/onion/issues/209
[#210]: https://github.com/onion-lang/onion/issues/210
[#213]: https://github.com/onion-lang/onion/issues/213
[#216]: https://github.com/onion-lang/onion/issues/216
[#215]: https://github.com/onion-lang/onion/issues/215
[#217]: https://github.com/onion-lang/onion/issues/217
[#218]: https://github.com/onion-lang/onion/issues/218
[#219]: https://github.com/onion-lang/onion/issues/219
[#221]: https://github.com/onion-lang/onion/issues/221
[#222]: https://github.com/onion-lang/onion/issues/222
[#224]: https://github.com/onion-lang/onion/issues/224
[#225]: https://github.com/onion-lang/onion/issues/225
[#226]: https://github.com/onion-lang/onion/issues/226
[#227]: https://github.com/onion-lang/onion/issues/227
[#228]: https://github.com/onion-lang/onion/issues/228
[#229]: https://github.com/onion-lang/onion/issues/229
[#231]: https://github.com/onion-lang/onion/issues/231
[#234]: https://github.com/onion-lang/onion/issues/234
[#235]: https://github.com/onion-lang/onion/issues/235
[#236]: https://github.com/onion-lang/onion/issues/236
[#237]: https://github.com/onion-lang/onion/issues/237
[#238]: https://github.com/onion-lang/onion/issues/238
[#239]: https://github.com/onion-lang/onion/issues/239
[#240]: https://github.com/onion-lang/onion/issues/240
[#241]: https://github.com/onion-lang/onion/issues/241
[#243]: https://github.com/onion-lang/onion/issues/243
[#244]: https://github.com/onion-lang/onion/issues/244
[#245]: https://github.com/onion-lang/onion/issues/245
[#246]: https://github.com/onion-lang/onion/issues/246
[#247]: https://github.com/onion-lang/onion/issues/247
[#220]: https://github.com/onion-lang/onion/issues/220
