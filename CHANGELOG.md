# Changelog

All notable changes to Onion are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- **A `while` body is narrowed by its condition, like an `if`-then branch** — `while (x != null) { x.method() }`
  now type-checks, including the idiomatic pointer-advance loop `while (cur != null) { use(cur); cur = cur.next }`
  (flow-sensitive: a use before the reassignment is narrowed, a use after is not). Soundness preserved:
  no narrowing leaks past the loop ([#303]).

- **Null narrowing flows through `||`, the De Morgan dual of the `&&` narrowing** — in `a || b`
  the right operand `b` is only reached when `a` is false, so a `x == null` left operand now
  narrows `x` to non-null while typing `b` (e.g. `x == null || x.length() == 0`). Likewise the
  fall-through of a `if x == null || cond { return }` guard narrows `x` to non-null. Soundness is
  preserved: `x != null || x.length()` still errors (there `x` may be null), mirroring the `&&`
  narrowing from #294 ([#302]).

- **`try`/`catch`/`finally` branches and the elvis `?:` fallback target-type an empty collection
  literal** — a `[]` / `[:]` in a try/catch branch (e.g. `def f(): List[Int] = try { [] } catch e: Exception { [1] }`)
  or as the right operand of `?:` (e.g. `def f(o: List[Int]?): List[Int] = o ?: []`) now infers its
  element type from the expected type instead of erasing to `Object` and failing with E0000/E0001,
  completing the target-typing set started by #300 ([#301]).

- **`if`/`else` and `select` branches target-type an empty collection literal** — a `[]` / `[:]`
  in a branch now infers its element type from the expected type (e.g. `def f(): List[Int] = if b { [] } else { [x] }`)
  instead of erasing to `Object` and failing the branch merge ([#300]).

- **`do[Option]` infers an empty bind's element type from the expected type** — a
  `do[Option] { x <- Option::none(); ret x + 1 }` in a `def f(): Option[Int]` / `val o: Option[Int]`
  context now types `x` as `Int` instead of `Object` (target typing) ([#279]).

## [0.4.3] - 2026-07-06

- **Overload resolution no longer leaks a type-parameter bound between overloads** — a call
  matching an unbounded `[T]` overload is no longer rejected by a sibling `[T extends ...]`
  overload's constraint, so e.g. `Collections::sort(list, comparator)` works on non-`Comparable`
  element types ([#298]).

- **`select` in statement position accepts mixed value/void case branches** — like an
  `if`/`else` statement, a `select` whose value is unused no longer requires its branches to
  unify; expression-position `select` stays strict ([#297]).

- **A block-expression may contain a control-flow statement before its trailing value** —
  `val y = { ...; if c { ... }; expr }` and `select` case bodies with an `if`/`while`/`foreach`
  before the result now parse ([#296]).

- **A nullable generic value matches a nullable generic parameter of the same type**
  (`Node[T]?` to a `Node[T]?` parameter) — generic containers with nullable links now work;
  invariant generics stay enforced ([#295]).

- **A reassignable `var` is smart-cast in the right operand of `&&`** — the common
  `while (p != null && p.method())` loop now works for a `var` reassigned in the body ([#294]).

- **LSP diagnostics land on the right token on tab-indented lines** — the tab-expanded
  compiler column is mapped back to a character position ([#293]).

- **The compiler reports an internal error instead of crashing if it ever emits invalid
  bytecode** — a `VerifyError`/`ClassFormatError` from a generated class is now surfaced as
  an I0000 diagnostic rather than an uncaught error.

- **An undefined array member is a type error, not a miscompile** — `arr.lengt` (a typo for
  `length`) reported no error and emitted invalid bytecode (a `VerifyError`); it now reports
  E0004 ([#292]).

- **A `void` expression as a collection element is a type error, not a compiler crash** —
  `[IO::println("x")]` (or a `void` map key/value) reported an internal error (I0000) when
  boxing `void`; it now reports E0000 ([#291]).

- **A `#!` shebang is only honored on the first line** — on any other line it is a syntax
  error instead of being silently skipped, and a first-line shebang no longer shifts
  reported line numbers ([#262]).

- **W0014**: a script that defines a `main` and also has bare executable top-level
  statements now warns that those statements are ignored (they are not the entry point) ([#278]).

- **An unknown declared type no longer cascades `E0002`** through later uses of the variable —
  the binding is recovered at the initializer's type (error recovery, cf. #257) ([#290]).

- **Nullable `var` smart-cast is flow-sensitive** — a `var` is narrowed in an `if v != null`
  branch even if it is reassigned *after* the branch, and `while ((v = e) != null) { ... }`
  narrows `v` in the loop body. A reassignment before/within the use still blocks it, and a
  flow-narrowed `var` captured by a closure is conservatively not narrowed inside it (closing
  an escaping-closure NPE) ([#288], [#289]).

- **auto-CLI accepts `--name=value` and handles `--help`/`-h`** — the GNU equals form is parsed, and `--help` prints usage to stdout and exits 0 ([#286], [#287]).

- **`Files::writeLines` accepts a `List`** (not only a `String[]`), matching `Strings::join` ([#285]).

- **`do[Future]` (and other `do[M]`) infers the element type of a throw-only lambda bind**, so a bind like `b <- Future::async(() -> { throw ... })` no longer types `b` as `Object` ([#284]).

- **Assigning `null` to a generic reference type parameterized by a type variable** (`Node[T]`) is now the same `W0012` warning as other null-to-non-null assignments, not a hard error ([#283]).

- **A cast between sibling interfaces is allowed** (`someNamed as Aged`), matching JLS 5.5.1 / javac; the runtime `checkcast` still enforces it. A provably-impossible cast (to an unrelated final class) is still rejected ([#282]).

## [0.4.2] - 2026-07-04

- **An uninitialized local `val` is a compile error (E0069)** instead of silently reading
  the JVM default (null/0) or NPEing ([#280]).

- **`do[Option]` infers the element type of an `Option::none()` bind** from the block's
  other binds, so arithmetic on the bound value no longer fails as `Option[Object]` ([#279]).

- **An explicit `this.field` / `self.field` read is smart-cast** after a null check, like the
  bare field name; only final (`val`) fields are narrowed ([#277]).

- **A mutable (`var`) nullable local that is never reassigned is smart-cast** by a null check ([#273]).

- **Generic inheritance with a primitive type argument resolves the super constructor** (`class IntBox(v: Int) : Box[Int](v)`) ([#272]).

- **A top-level `val`/`var` initializer runs when an explicit `main` exists** (was a silent miscompile leaving the field null/0) ([#270]).

- **A generic parent's type parameter is specialized in a child class** (`self.get()` on a `Box[String]` subclass returns `String`, not `T`) ([#271]).

- **`ExecutorService.invokeAll` and similar wildcard-bounded generic methods infer their type variable** ([#274]).

- **`Rand::choice` throws on an empty collection** instead of silently returning null ([#276]).

- **The diagnostic caret aligns on lines with leading tabs** ([#275]).

- **A generic subtype is assignable to its generic supertype.** `ArrayList[T]` is now
  accepted where `List[T]` is expected (e.g. returning a built `ArrayList[T]` as `List[T]`
  from a generic method); the type-variable assignability check consults the class
  hierarchy, not just the raw class. Invariant generics are still enforced ([#269]).

- **`override` is checked (E0068).** A method marked `override` that does not override any
  base-class or interface method is now a compile error instead of compiling silently ([#268]).

- **A `${expr}` / `$var` in a string literal now warns (W0013).** Shell/Kotlin-style
  interpolation, which Onion emits verbatim, produces a warning suggesting the `#{}` form
  instead of being a silent footgun ([#266]).

- **`Int + Boolean` and similar report the offending operand, not a confusing `String`.** When
  `+` cannot add numerically and neither operand is a String, the error names the invalid
  operand type instead of silently concatenating and surfacing a misleading String mismatch ([#265]).

- **Operator overloading resolves `extension` methods.** `a + b` now finds a `plus` (or
  `minus`/`times`/...) defined in an `extension` block instead of silently falling back to
  string concatenation — the only way to overload operators for a `record` ([#264]).

- **A generic constructor rejects an argument incompatible with the type argument.**
  `new Box[String](aStringBuilder)` is now a type error instead of compiling and throwing
  a runtime ClassCastException — the constructor checks arguments under the type-argument
  substitution, like instance methods do ([#263]).

- **The elvis operator is shown as `?:` in diagnostics.** A type error on `a ?: b` now names
  the operator `?:` instead of the reversed `:?`.

- **A lambda binds a Function type with an `Object` result, and JDK wildcard-result SAMs.**
  `Function1[String, Object] = (s) -> 42` is accepted, and a lambda passed directly to a
  `Function<? super T, ? extends U>` JDK method (`thenApply`, `Stream.map`, `Optional.map`)
  infers correctly instead of failing on the wildcard ([#260], [#259]).

- **A type-mismatched local binding no longer cascades spurious errors.** When a `val`/`var`
  initializer fails to type, the binding is still registered at its declared type, so later
  references resolve instead of emitting a chain of spurious E0002 ([#257]).

- **An explicit `as` cast of a primitive to a boxing supertype autoboxes.** `(x as Object)`
  / `as Number` / `as Comparable` on an `Int` now boxes and casts (matching the implicit
  assignment) instead of failing with E0000 ([#261]).

- **A runtime exception in a local initializer reports the correct line.** An exception
  thrown while evaluating a `val`/`var` initializer (`val bad: String = n!!`) now maps to
  the declaration's line instead of the previous statement's ([#258]).

- **A collection literal is target-typed to a nullable-wrapper element type.**
  `val xs: List[Integer?] = [1, null]` and `Map[String, Integer?]` are now honored
  (they matched only the primitive `Int?` form before) ([#255]).

- **A generic type variable is inferred from an argument even when a lambda comes first.**
  `apply2((s) -> s.length(), "hello")` for `apply2[A, B](f: Function1[A, B], x: A)` now
  infers `A` from `x` regardless of argument order, instead of failing with E0057 ([#256]).

- **A generic method infers its type parameter through a nullable container element.**
  `firstNonNull[T](xs: List[T?])` now infers `T` from a `List[String?]` argument, instead
  of leaving `T` unbound (`Object`) and rejecting the call ([#254]).

## [0.4.1] - 2026-07-04

- **A `foreach` loop variable can be smart-cast.** A loop variable never reassigned in
  the body is effectively final, so a null / `is` check narrows it (like an unassigned
  parameter). Previously it was always mutable and never narrowed, so arithmetic on a
  `List[Int?]` element after a null check silently string-concatenated or errored ([#253]).

- **An untyped-parameter lambda works as an argument to an unqualified call.**
  `applyF((n) -> n * 3, 5)` for a top-level (or bare same-class) function now infers the
  lambda parameter type from the resolved functional-interface parameter, matching the
  instance/static call paths; this completes the argument-position inference for #232.

- **A generic call in a lambda body infers its type arguments from the expected type.**
  `result.flatMap((x) -> Result::ok(x * x))` infers `Result[Int, String]` (the error type
  E is pinned by the expected SAM return) instead of failing as `Result[Int, Object]`
  ([#230]).

- **A throw-only lambda no longer forces an Object element type.**
  `val f: Future[String] = Future::async(() -> { throw ... })` infers `Future[String]`
  (the closure is routed through bidirectional inference so the expected type pins the
  type variable) instead of `Future[Object]` ([#233]).

- **Compound assignment evaluates an indexed target's sub-expressions once.**
  `a[idx()] += v` now runs `idx()` (and the receiver) exactly once, instead of twice
  (read + write); the array/index are bound to temps ([#249]).

- **A self-referential F-bound is accepted.** `class C[T extends C[T]]` (the CRTP /
  recursive self-bound pattern) no longer fails with E0030 — a generic type's arity is
  registered before its own bounds are resolved; the bound is still enforced ([#242]).

- **A lambda for a Java generic functional interface infers its parameter types.**
  `xs.forEach((s) -> ...)` on a `List[String]` gives `s: String` (the interface's type
  argument), instead of `Object` ([#232]).

- **A generic call in argument position infers its type arguments from the parameter type.**
  `take(Result::ok(7))` where `take` expects `Result[Integer, String]` now pins the
  argument to `Result[Integer, String]` (the error type E is inferred from the expected
  parameter type) instead of failing as `Result[Int, Object]`. When resolution finds no
  applicable method, a malleable argument (a generic static/unqualified call, or a
  collection literal) is re-typed against the single candidate's parameter types and
  resolution is retried ([#232]).

- **An `as` cast between erasure-compatible generic types is allowed.**
  `List[String] as List[Object]` (same raw type under erasure) casts through as Java's
  unchecked cast, instead of E0000 ([#231]).

- **A mutable local captured by a closure at script top level is shared.** Top-level
  `var` captured by a lambda now sees mutations (a shared boxed cell), matching closures
  inside methods ([#214]).

- **Same-class constructor delegation.** `def this(x: Int) : this(x, 0) { ... }` delegates
  to another constructor of the same class (in addition to the `: (args)` super-init form);
  field initializers run once, via the ultimately-invoked constructor ([#212]).

- **A class can implement a generic interface with itself as the type argument.**
  `class Ver <: Comparable[Ver]` (the natural-ordering pattern) no longer fails with
  E0000 — an unbounded type parameter accepts any reference-type argument without
  walking the not-yet-established supertype chain ([#252]).

- **`select` over a byte/short/char accepts int case labels.** `select b { case
  1: ... }` on a `Byte`/`Short`/`Char` scrutinee matches int labels by value (like
  Java's switch) instead of failing with E0000 ([#251]).

- **Compound assignment narrows for a byte/short/char local.** `b += 5` on a
  `Byte`/`Short`/`Char` local now type-checks (Java's `E1 = (T)(E1 op E2)`); a plain
  assignment still needs an explicit cast ([#250]).

- **Compiler crash fixed: `++`/`--` on a Long/Double/Float lvalue.** The
  increment literal now matches the operand type instead of always being an `int`
  (which crashed codegen). `++`/`--` also work on array elements (`a[i]++`),
  evaluating a side-effecting index once ([#248]).

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

[Unreleased]: https://github.com/onion-lang/onion/compare/v0.4.3...develop
[#303]: https://github.com/onion-lang/onion/issues/303
[#302]: https://github.com/onion-lang/onion/issues/302
[#301]: https://github.com/onion-lang/onion/issues/301
[#300]: https://github.com/onion-lang/onion/issues/300
[0.4.3]: https://github.com/onion-lang/onion/releases/tag/v0.4.3
[#298]: https://github.com/onion-lang/onion/issues/298
[#297]: https://github.com/onion-lang/onion/issues/297
[#296]: https://github.com/onion-lang/onion/issues/296
[#295]: https://github.com/onion-lang/onion/issues/295
[#294]: https://github.com/onion-lang/onion/issues/294
[#293]: https://github.com/onion-lang/onion/issues/293
[#292]: https://github.com/onion-lang/onion/issues/292
[#291]: https://github.com/onion-lang/onion/issues/291
[#262]: https://github.com/onion-lang/onion/issues/262
[#278]: https://github.com/onion-lang/onion/issues/278
[#290]: https://github.com/onion-lang/onion/issues/290
[#289]: https://github.com/onion-lang/onion/issues/289
[#288]: https://github.com/onion-lang/onion/issues/288
[#286]: https://github.com/onion-lang/onion/issues/286
[#287]: https://github.com/onion-lang/onion/issues/287
[#285]: https://github.com/onion-lang/onion/issues/285
[#284]: https://github.com/onion-lang/onion/issues/284
[#283]: https://github.com/onion-lang/onion/issues/283
[#282]: https://github.com/onion-lang/onion/issues/282
[0.4.2]: https://github.com/onion-lang/onion/releases/tag/v0.4.2
[#277]: https://github.com/onion-lang/onion/issues/277
[#279]: https://github.com/onion-lang/onion/issues/279
[#280]: https://github.com/onion-lang/onion/issues/280
[#273]: https://github.com/onion-lang/onion/issues/273
[#272]: https://github.com/onion-lang/onion/issues/272
[#270]: https://github.com/onion-lang/onion/issues/270
[#271]: https://github.com/onion-lang/onion/issues/271
[#274]: https://github.com/onion-lang/onion/issues/274
[#276]: https://github.com/onion-lang/onion/issues/276
[#275]: https://github.com/onion-lang/onion/issues/275
[#269]: https://github.com/onion-lang/onion/issues/269
[#268]: https://github.com/onion-lang/onion/issues/268
[#264]: https://github.com/onion-lang/onion/issues/264
[#265]: https://github.com/onion-lang/onion/issues/265
[#266]: https://github.com/onion-lang/onion/issues/266
[#263]: https://github.com/onion-lang/onion/issues/263
[#261]: https://github.com/onion-lang/onion/issues/261
[#257]: https://github.com/onion-lang/onion/issues/257
[#259]: https://github.com/onion-lang/onion/issues/259
[#260]: https://github.com/onion-lang/onion/issues/260
[#258]: https://github.com/onion-lang/onion/issues/258
[#255]: https://github.com/onion-lang/onion/issues/255
[#256]: https://github.com/onion-lang/onion/issues/256
[#254]: https://github.com/onion-lang/onion/issues/254
[0.4.1]: https://github.com/onion-lang/onion/releases/tag/v0.4.1
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
[#242]: https://github.com/onion-lang/onion/issues/242
[#249]: https://github.com/onion-lang/onion/issues/249
[#233]: https://github.com/onion-lang/onion/issues/233
[#232]: https://github.com/onion-lang/onion/issues/232
[#253]: https://github.com/onion-lang/onion/issues/253
[#230]: https://github.com/onion-lang/onion/issues/230
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
[#248]: https://github.com/onion-lang/onion/issues/248
[#250]: https://github.com/onion-lang/onion/issues/250
[#251]: https://github.com/onion-lang/onion/issues/251
[#252]: https://github.com/onion-lang/onion/issues/252
[#212]: https://github.com/onion-lang/onion/issues/212
[#214]: https://github.com/onion-lang/onion/issues/214
[#220]: https://github.com/onion-lang/onion/issues/220
