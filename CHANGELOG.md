# Changelog

All notable changes to Onion are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **`run/Mastermind.on`, a 309-line AI-vs-AI Mastermind code-breaking
  simulation.** Two `Guesser` strategies (`RandomGuesser`, `SmartGuesser`)
  compete across 50 random secrets; stats are compared and colour
  frequency is checked for uniformity. Exercises records with value
  equality (`Feedback`, `Code`), a homogeneous `enum Strategy` dispatched
  via `select` in a factory function, an extension method on `Int`
  (`.colorName()`), `List.filter` with closures over `List[Code]`,
  `Map[String, Int]` with `?:` null coalescing, `foreach (k, v)` over a
  typed map, `List.zip` with nested generics, and `try`/`catch` with
  nullable types.

- **`run/ColorPalette.on`, a 399-line color science toolkit.** Covers
  RGB↔HSL/HSV/CMYK conversion, named color lookup, five harmony palette
  types, WCAG accessibility contrast checking, and palette
  temperature/brightness analysis. Exercises `record` types with method
  bodies, a data-carrying `enum` with `case`, sealed-hierarchy `select`
  matching, `foreach (k, v) in map` destructuring, and collection
  pipelines.

- **`run/WordSearch.on`, a 300-line word-search puzzle generator and
  solver.** Hides a word list in a 16×16 grid across 8 directions, fills
  the remaining cells with random letters, then solves the puzzle to
  verify placement accuracy. Exercises an ADT case-enum (`Direction`,
  8 singleton cases) dispatched via `select`, records with methods
  (`Placement`, `Found`), 2D arrays, extension methods on `Int`/`String`,
  and collection pipelines (`map`/`filter`/`fold`/`groupBy`/`sortedBy`).

- **`run/MusicTheory.on`, a 560-line music theory calculator.** Covers the
  full chromatic note system, interval naming, chord voicings, scale
  construction, modal analysis, and chord progressions: homogeneous enums
  (`Note`, `ChordQuality`, `ScaleType`, `Mode`), records with methods
  (`Chord`, `Scale`) including a diatonic-triad builder, a mutable
  `ProgressionBuilder` class, and collection pipelines (`map`/`filter`/
  `fold`/`find`) over chord/scale membership and common-tone analysis.

- **`run/ExprEval.on`, a 292-line symbolic expression evaluator.** Replaces
  the earlier 98-line interface-based sketch with an ADT `enum Expr` (Num,
  Var, Add, Sub, Mul, Div, Pow, Neg) evaluated and symbolically
  differentiated (`diff`, product/quotient/power rules) via nested
  `select`/`case is` pattern matching, plus a single-step algebraic
  simplifier, a hand-written recursive-descent parser
  (`parseExpr → parseTerm → parsePower → parseFactor`), and a
  `Map[String, Double]` variable environment for batch evaluation over
  `List[String]`/`List[Double]`.

- **`run/PaymentProcessor.on`, a 338-line payment-processor simulation.**
  Luhn credit-card validation and batch transaction analytics: card-network
  detection from BIN prefix (plain `enum CardType`), approve/decline
  dispatch via a data-carrying ADT case-enum (`TxResult`), records with
  `example` clauses, `do[Option]` notation for safe amount parsing,
  extension methods on `String`/`Int`/`Double`, and collection pipelines
  (`filter`/`groupBy`/`sortedBy`/`fold`/`distinct`/`partition`) over
  per-transaction, per-network, per-category, and top-merchant reports.

- **`run/CpuScheduler.on`, a 464-line CPU scheduling simulator.** Runs FCFS,
  SJF (non-preemptive), Round-Robin (quantum-based), and preemptive Priority
  scheduling against a shared 5-process workload, rendering a text Gantt
  chart and a per-process metrics table (wait/turnaround/response) for each
  algorithm, then compares them and picks the one minimizing average wait
  time. Exercises a data-carrying ADT case-enum dispatched via `select`,
  class inheritance with per-algorithm overrides, primary-constructor
  superclass delegation (`class Sub(args) extends Base(args)`), extension
  methods on `Int`/`String`, and nullable-typed pick results.

### Fixed

- **Internal compiler error (I0000) on a `do[List]` comprehension whose
  downstream closure locally re-declares a binding name.** A closure that
  declares a local variable shadowing an outer `do`-binding name (e.g.
  `x <- [1, 2]` followed by a nested closure with `val x = ...`) crashed
  bytecode generation with `Index -1 out of bounds for length 0` in
  `ClosureCodegen.emitNewClosure`. `CapturedVariableScanner` now tracks
  locally-declared names per closure body and excludes them from the
  captured-variable set (unless a nested closure still captures the same
  name, in which case it correctly stays boxed).

## [0.13.0] - 2026-08-20

### Added

- **`run/PolynomialAlgebra.on`, a 295-line polynomial arithmetic demo.**
  Exercises record operator overloading (`plus`/`minus`/`times` mapped to
  `+`/`-`/`*`), static factory methods, `List[Double]` generics, `foreach` over
  ranges, `while` loops, nullable return types (`Double?`), string
  interpolation, and batch computation. Demonstrates Horner evaluation,
  Newton-Raphson root finding, Chebyshev polynomials, product-rule
  differentiation cross-check, indefinite integration, iterated derivatives,
  and polynomial composition — all results verified analytically.

- **`run/FileSystemSim.on`, a 442-line virtual in-memory filesystem simulator.**
  Covers an ADT enum (`FsNode = FileNode | DirNode | SymlinkNode`) matched via
  `select`/`is`, a data-carrying enum (`Permission`), a plain enum
  (`SortOrder`), a generic record (`Pair[A, B]`), a plain record (`Stat`) with
  methods, a class implementing an interface (`VirtualFs conforms
  Filesystem`), collection pipelines (`filter`, `sortedBy`, `fold`,
  `groupBy`), recursive tree traversal, nullable types, closures, string
  interpolation, and `foreach` over integer ranges and map entries.

- **Three new `run/` samples: `ConnectFour.on`, `Mandelbrot.on`, and
  `MarkovText.on`.** `ConnectFour.on` (286 lines) is a deterministic AI-vs-AI
  Connect Four match over ADT enums (`Cell`, `GameResult`), an `interface AI`
  with two implementing strategies, and a flat-array 2-D grid.
  `Mandelbrot.on` (279 lines) renders the Mandelbrot set and several Julia
  sets as ASCII art using a `record Complex` with operator methods
  (`plus`/`times`), a homogeneous enum, extension methods on `Double`, and
  top-level `example` clauses checked at compile time. `MarkovText.on`
  (312 lines) is a bigram Markov chain text generator covering an ADT
  case-enum, records with `example` clauses, extension methods on `String`
  and `Int`, `foreach (k, v)` map destructuring, and collection pipelines.

### Fixed

- **A Kotlin (`fun`), Swift/Go (`func`), or Rust (`fn`) function/method declaration
  got a bare expected-token dump with no mention of `def`.** None of `fun`, `func`,
  or `fn` are keywords in Onion, so at statement position the keyword parses as a
  bare identifier reference and the parser trips on the declaration *name* that
  follows (`Encountered "greet"`), while inside a class/interface body it trips on
  the keyword itself — neither message names `def`. `commonSyntaxHint` in
  `Parsing.scala` now matches the full source line for a `fun`/`func`/`fn <name>(`
  declaration (the same source-line strategy as the `switch` hint, since the
  offending token is never the keyword itself at statement position) and hints
  `def name(...): ReturnType { ... }`. A real call to a method literally named
  `fun`/`func`/`fn` is unaffected (`fun(...)` has no name between the identifier
  and the paren, so it never matches).

- **`new T[null]` (a `null` literal used as an array-size expression) crashed the
  compiler with an internal error (`I0000: Bad type on operand stack`)** instead
  of being rejected during type checking. `ConstructionTyping.typeNewArray` typed
  each dimension expression but never checked it was actually an `Int` (or an
  integer type unboxable to one), so a `null` literal — or any other
  non-integer size expression — reached code generation and produced bytecode
  that pushed a null reference where the JVM's `newarray`/`multianewarray`
  expects an `int` on the operand stack, which the verifier rejects at class
  load time. It now runs the same `Boxing.tryUnboxToInteger` + integer-type
  check `typeIndexing` already uses for array indices, and reports
  `INCOMPATIBLE_TYPE` (E0000) on the offending dimension instead. Found by
  `MutationFuzzSpec` (issue #812).

- **A `CONSTRUCTOR_NOT_FOUND` (E0021) diagnostic's "Available constructors:" list header
  was always English**, even under a Japanese-locale JVM where the rest of the message
  (`constructor applicable for ... is not found`) was correctly translated —
  `SemanticErrorReporter.reportConstructorNotFound` built that header from a hard-coded
  string literal instead of going through the bilingual `errorMessage*.properties` bundle
  every other suggestion (`error.suggestion.candidates`, `suggestion.didYouMean`, ...) uses.
  It now resolves `error.suggestion.availableConstructors` from the bundle, so the header
  is Japanese under `-Duser.language=ja` like the rest of the message.

- **A Java/JS/C-style `switch` statement (`switch x { case 1: ... }` or
  `switch (x) { ... }`) got a bare expected-token dump with no mention of
  `select`.** `switch` isn't a keyword in Onion, so it parses as a bare
  identifier-reference statement and the parser actually trips on whatever
  follows it — the condition, or the `{` of a parenthesized condition —
  never on `switch` itself, so no existing hint (all of which match on the
  *offending* token) could name it. `commonSyntaxHint` in `Parsing.scala`
  now also receives the full source line of the error and matches a leading
  `switch` there, so both spellings now hint at `select value { case 1:
  ...; else: ... }`.

- **A name that differs from the real one only by case (`.Length()` instead of
  `.length()`, `UserName` instead of `userName`, `myclass` instead of `MyClass`)
  got no "did you mean" suggestion**, just a bare not-found error. `Suggestions.findSimilar`
  compares names case-insensitively but then filtered out `distance == 0` results —
  meant to skip a self-match, it also threw away the single most common typo of all,
  since a real case-sensitive exact match would never have reached the not-found path
  to begin with. The filter now keeps `distance == 0`, so a pure case mismatch is always
  suggested, for variables, methods, fields, and classes alike.
- **A lowercase, Java/Scala-style primitive type name (`int`, `boolean`, `long`, ...)
  got a generic "check spelling or add import" instead of a suggestion**, because
  Onion's primitive keywords (`Int`, `Boolean`, `Long`, ...) were never in the
  class-not-found candidate list — they aren't classes, so nothing offered them as
  a match. They're now added to the candidates the "did you mean" check draws from,
  so `var x: int = 0` suggests `Int`.
- **A misspelled named-argument name (`f(x = 1, kount = 2)`) got a bare
  "unknown parameter name" with no "did you mean" suggestion**, unlike every
  sibling not-found diagnostic (variable, method, field, class), which all
  suggest a similar name. `UNKNOWN_PARAMETER_NAME` (E0043) was the one
  not-found error still wired through the plain data-driven message path in
  `SemanticErrorReporter`, so none of its six call sites ever passed the
  candidate parameter names along. It now has a dedicated handler that builds
  the suggestion from the callee's parameter names, for static calls,
  instance calls, unqualified calls, and constructor calls alike.
- **`break`/`continue` with a misspelled label (`break outr` instead of
  `break outer`) got a bare "label not found" with no "did you mean"
  suggestion**, the one `*_NOT_FOUND` diagnostic still on the plain
  data-driven message path. `LocalContext` already tracked every enclosing
  labeled loop's name in `labelStack` but exposed no way to read it back;
  it now has a `labels` accessor, and `LABEL_NOT_FOUND` (E0058) has a
  dedicated handler that suggests the closest enclosing label name.
- **A misspelled `override` target (`override def great()` instead of
  `override def greet()`) got a bare "does not override any method" with no
  "did you mean" suggestion**, the last `*_NOT_FOUND` diagnostic still on the
  plain data-driven message path. `DuplicationChecks.checkOverrideTargets`
  already collects every overridable base-class/interface method name while
  checking the override target; it now passes that list along, and
  `OVERRIDE_TARGET_NOT_FOUND` (E0068) has a dedicated handler that suggests
  the closest matching base method name.

### Added

- **`run/PackageDelivery.on`, a 413-line package delivery tracking simulation.**
  Covers an ADT case-enum (`PackageStatus`, 5 cases) matched exhaustively via
  `select`/`is`, a plain enum (`Priority`), records with `example` laws
  (`Address`, `Customer`, `RouteStop`), an `extension Int` block (`asTime`,
  `asCurrency`, `asWeight`), a mutable class with an event log (`Package`),
  a class built on collection pipelines (`DeliveryCompany`: `filter`,
  `groupBy`, `sortedBy`, `fold`), closures stored in a `val` and invoked via
  `.call`, recursive helpers, string interpolation, and nullable-safe lookups.

- **`run/SpreadsheetCalc.on`, a ~400-line mini spreadsheet evaluator with a
  recursive-descent formula parser.** Covers ADT enums (`CellVal`, `FExpr`),
  records, classes, user-defined generics, `HashMap`/`HashSet`, nullable
  fields, `select`/pattern matching, and `try`/`catch`. The formula engine
  supports literals, cell references, binary operators (`+ - * /`), range
  notation (`A1:A3`), and aggregate functions (`SUM`, `MAX`, `MIN`, `AVG`),
  plus cycle detection over circular formula references and a formatted
  table report.

- **`run/BrainFuck.on`, a 357-line Brainfuck interpreter** with five verified
  demo programs and analytical reporting sections. Covers a data-carrying
  ADT case-enum (`BfInstr`, 8 cases, `JumpFwd`/`JumpBck` each carrying an
  `Int` target) matched exhaustively via `select`, an `Int[]` data tape,
  extension methods on `Int` (`clamp`, `mod256`, `toHex`) and `String`
  (`repeat`, `padRight`), a mutable class with public getters (`BfMachine`),
  `Map[String,Int]` with `foreach (k,v)`, collection pipelines, string
  interpolation, nullable types, and runtime Brainfuck code generation (a
  3×3 multiplication table computed via generated BF programs).

## [0.12.1] - 2026-08-19

### Fixed

- **A dangling `else` (one with no matching `if` immediately before it) got a hint
  claiming Onion doesn't support `if` as an expression.** `if`/`else` has always worked
  as an expression (`val x = if cond { a } else { b }`), so the hint's suggested fix —
  "use `if (cond) { ... } else { ... }`" — didn't even address a dangling `else`, whose
  actual fix is adding the missing `if`, not rewriting anything as an expression. The
  `case "else"` branch of `commonSyntaxHint` in `Parsing.scala` now reports a
  `error.parsing.hint.dangling_else` hint (added to both `errorMessage.properties` and
  `errorMessage_ja.properties`) that names the real requirement: `else` must directly
  follow an `if` block's closing `}`.

- **The ternary and old-trailing-arrow parser hints printed literal single-quote
  characters around their braces instead of the braces alone**, e.g. `if cond '{' a '}'
  else '{' b '}'` instead of `if cond { a } else { b }`. Both hints are looked up through
  the zero-argument `Message(property)`, which returns the resource bundle string as-is
  and never runs it through `MessageFormat` — so the `'{'`/`'}'` brace-escaping
  convention, needed only when a lookup actually formats arguments (as
  `trailing_lambda_parens` does), leaked its escaping quotes straight into the message.
  `errorMessage.properties` and `errorMessage_ja.properties` now spell both hints with
  plain braces.

- **A stray `cond ? a : b` ternary now gets a hint instead of a bare expected-token
  dump.** Onion has no ternary operator — `if`/`else` works as an expression instead —
  but a newcomer writing `?` still hits the parser at a point where the expected-token
  list (`<EOF>, <EOL>, ";"`) never mentions the operator or the rewrite. `commonSyntaxHint`
  in `Parsing.scala` gained a `case "?"` alongside the existing hints for old `<:`/`:`
  inheritance syntax, `for x in xs`, and the retired `=>` arrow, naming the operator and
  pointing at `if cond { a } else { b }`.

- **`W0016` now also fires when a `@TailRecursive` mutual-recursion group's parameter
  lists don't match.** `0.12.0` documented `W0016` as covering all four requirements
  `MutualRecursionOptimization` places on a group, but the parameter-list check was the
  one requirement `validateGroup` never made — it lived in `transformGroup`, which
  silently `return`ed instead of reporting. A group whose members differed only in
  argument count or per-position types therefore compiled with no diagnostic at all,
  which is the same silent `StackOverflowError` risk `W0016` exists to prevent. The
  check moved into `validateGroup`, so it now reaches the `W0016` path like the other
  three.

- **README trailing-lambda examples still used the retired `=>` arrow.** `0.12.0`'s
  one-arrow change (`->` everywhere, `=>` no longer part of the language) missed three
  examples in `README.md` and one in `docs/guide/control-flow.md`, left over from before
  the rename. One of them (`future.onComplete(onSuccess, onFailure) { result => ... }`)
  was also calling a two-argument `onComplete` as if it took a third trailing block, so it
  never compiled even with the arrow fixed — replaced with a `list.fold(0) { acc, x -> ... }`
  example that actually demonstrates positional args plus a trailing lambda. All four
  snippets are now verified against the compiler.

- **The `for x in xs` parser hint was hard-coded English, breaking bilingual
  diagnostics.** Every other hint in `commonSyntaxHint` (`Parsing.scala`) resolves through
  `Message("error.parsing.hint.*")`, so it follows the JVM's default locale like the rest
  of the compiler's diagnostics; the `case "in"` hint (suggesting the C-style loop rewrite
  for the retired `for x in xs` syntax) was still a raw string literal, so a Japanese-locale
  syntax error came out in Japanese with the hint sentence stuck in English mid-message.
  Added `error.parsing.hint.old_for_in` to both `errorMessage.properties` and
  `errorMessage_ja.properties` and pointed the hint at it.

- **`commonSyntaxHint`'s fallback "a block is expected" hint was also hard-coded
  English**, same bug as the `for x in xs` hint above: a `while`/`if` head missing its
  `{ ... }` block (e.g. `while true` / `if true` on its own line) fell through to a raw
  string literal instead of the `error.parsing.hint.*` bundle lookup every other hint
  uses, so the hint sentence stayed English even under a Japanese locale. Added
  `error.parsing.hint.block_expected` to both `errorMessage.properties` and
  `errorMessage_ja.properties` and pointed the fallback case at it.

## [0.12.0] - 2026-08-18

### Changed

- **BREAKING: one arrow. The trailing-lambda arrow is `->`, and `=>` is no longer part of
  the language.** `->` was already the arrow of a function type (`(Int) -> Int`) and of a
  parenthesized lambda (`(x) -> x * 2`); only the trailing form spelled it `=>`, so
  `[1, 2, 3].filter { x -> x % 2 == 0 }` was a syntax error for no reason a reader could see.
  Now it is the ordinary way to write it, `{ acc, x -> acc + x }` takes several parameters,
  `{ n: Int -> n * n }` types one, and a trailing lambda's body can itself be a lambda.
  Nothing else changes: `.filter { … }` with no parentheses worked before and still does.

  Two consequences worth knowing. A trailing-lambda parameter cannot be given a *bare*
  function type — `{ k: Int -> Int -> k(0) }` has no readable parse — so a lambda whose
  parameter is a function is written in the parenthesized form, `.map((k: Int -> Int) ->
  k(0))`, where the `)` ends the type; the grammar reads a trailing parameter's type with a
  production that stops before `->` (`type_no_arrow`), and the parenthesized form keeps the
  full type grammar. And old source that still writes `{ x => … }` gets a hint naming the
  arrow rather than an expected-token dump. Every `run/` sample, every documentation
  fence, the TextMate grammar, the Pygments lexer, the formatter and the stdlib Javadoc
  were migrated; the migration touched only `=>` in code, never one inside a string
  literal or a comment (`SourceRegions.codeMask` decided which was which).

- **BREAKING: constructors are primary/secondary, the way Scala and Kotlin have them.**
  The primary constructor is the parameter list after the class name together with the
  arguments on the `extends` clause. It alone calls the superclass constructor and stores
  the `val`/`var` parameter fields. A `def this` in a class that has a primary must
  delegate to it with `: this(...)` — directly or through another secondary — and a
  `def this` that does not is **E0087**. A class with neither a parameter list nor
  `extends` arguments has no primary; its `def this` constructors keep calling the
  superclass no-arg constructor implicitly, exactly as before, so the 146 `def this`
  constructors in `run/` compile unchanged.

  This is not a style rule; it closes a real hole. `class P(val x: Int) { def this { } }`
  compiled and `new P().x` was `0`, because the primary's field stores lived in the
  synthesized constructor's own block, which a sibling never ran. And
  `class Dog(name: String) extends Animal(name) { def this { } }` resolved `super()`
  against `Animal`'s no-arg constructor — a confusing E0021 for a class that visibly says
  `extends Animal(name)`. Both are E0087 now, with the primary's parameter types in the
  message so the fix is copy-pasteable.

  **The anonymous super-call form `def this(x) : (x) { }` is gone.** Arguments for the
  superclass constructor are written on `extends`, and only there; there is deliberately
  no `super(...)` clause to reintroduce a second route around the primary. Every use in
  the repository became shorter by turning into a primary constructor —
  `class CircleShape(val data: Circle) extends Shape(ShapeKind::CIRCLE) { … }` — and a
  leftover `: (args)` gets a hint pointing at the `extends` clause.

### Fixed

- **A field initializer can now read the primary constructor's parameter fields.**
  `class Point(val x: Int) { var total: Int = x * 2 }` gave `total == 0`, always: the
  initializer was spliced in *before* `this.x = x`, so `x` — resolved to the field, since
  initializers are typed without the constructor's parameters in scope — was still at its
  default. Initializers now run after the primary's parameter stores, once, in the primary
  only, in the order Kotlin and Scala use. A plain (non-`val`/`var`) primary parameter is
  still not visible to an initializer; that needs the initializer typed in the primary's
  scope, and is deliberately out of scope here.

- **`def this` inside a record or enum body no longer crashes the compiler (E0089).** The
  body pass never typed it, and code generation then took the record's synthetic
  parameter-to-field path against a constructor of the wrong arity — an ASM exception
  surfacing as I0000. Records and enums have their canonical constructor; a second one has
  no meaning, and the diagnostic says to use a static factory instead.

- **A field read inside `: this(...)` or `extends B(...)` arguments no longer produces a
  class the JVM refuses to load (E0090).** The object does not exist while its delegation
  arguments are evaluated, so `def this : this(seed)` with `seed` a field was a GETFIELD on
  `uninitializedThis`; the verifier rejected the class and the compiler reported I0000.
  Closures in that position remain fine — they capture `this` and run later.

- **A constructor delegation cycle is a compile error (E0088), not a `StackOverflowError`
  at `new`.** `def this(a) : this(b)` with `def this(b) : this(a)` was never checked. Now
  that `: this(...)` is the ordinary way to write a secondary, the walk — one outgoing
  edge per self-delegating constructor, O(constructors) — runs once per class.

- **docs(project-cli): stop `onion.lock` from contradicting itself two sections down.**
  `#788` (feat/dependency-lock) added the `### onion.lock` section documenting the lock
  file but left the `### Not yet supported` section above it, and the `## Deferred` list
  further down, still claiming "there is no lock file" / listing "dependency lock files"
  as out of scope — on both the English and Japanese pages. Trimmed both sections to the
  one thing that is still actually deferred: offline resolution.

### Added

- **`run/LambdaArrows.on`**, showing the one arrow in every position it appears in.
- **`TermWalk`**, a shared structural walk over typed terms and statements; the ASM
  backend's try-detector and the new `this`-before-delegation check are both expressed on
  it, so the reflective child enumeration exists once.

- **`W0016`: warn when `@TailRecursive` is on a mutual-recursion group that
  `MutualRecursionOptimization` cannot actually optimize.** The optimization only
  rewrites a group into a state machine when every member is private, shares one
  return type, shares one parameter list, and only tail-calls within the group
  (`MutualRecursionOptimization.validateGroup`). A group that fails one of those
  checks was left silently unoptimized — the methods still called each other, just
  as ordinary non-tail calls — so the annotation looked honored right up until deep
  recursion overflowed the JVM stack at runtime, with nothing at compile time
  pointing back at the annotation. `W0016` now fires at compile time, naming the
  failed requirement, on every method in the group. Docs
  (`docs/compiler/tail-call-optimization.md`, ja) gained the `@TailRecursive`
  requirements and a worked example, since neither previously said what mutual
  recursion needs to actually be optimized.

### Documentation

- Every documentation page and both `CLAUDE.md` files spell trailing lambdas with `->`.
  The `.call(f)` spelling for invoking a function value is gone from every example — 85
  sites became direct calls, `f(x)`, and `this.handler.call(e)` became `handler(e)`;
  the one paragraph that explains *what* a function value is (`onion.FunctionN` with a
  `call` method) is the one place `.call` still appears. The constructor sections of the
  class guide (EN and JA — the JA page had no `def this` documentation at all) are
  rewritten around the primary/secondary model; `docs/grammar.txt` gained the primary
  parameter list, the `extends(args)` clause and, for the first time, the lambda and
  trailing-lambda productions. Every changed guide fragment was compiled: 431 blocks
  from the touched pages, compared by content against the same blocks on the previous
  release, with zero regressions.

## [0.11.2] - 2026-08-18

### Added

- **`onion.lock`: a build resolves the same artifacts the next time, and says so if it
  cannot.** `[dependencies]` names direct dependencies at a version each, which does not
  reproduce a build — a transitive dependency's version is chosen at resolution time, so two
  builds of a byte-identical `onion.toml` compile against different jars the moment a
  transitive publishes. That failure appears on one machine, cannot be reproduced on
  another, and nothing in the project changed.

  The lock records the whole transitive coordinate set, which a later build resolves
  *instead of* re-deriving, and a SHA-256 per artifact compared before anything compiles.
  Different bytes behind an already-published version stop the build and name the file, both
  hashes, and the way out. That is not paranoia: a published version's bytes are supposed to
  be immutable, and a repository serving different ones is broken or hostile.

  The two halves are deliberately kept apart rather than paired. coursier returns resolved
  coordinates sorted and downloaded files in resolution order, with no stated
  correspondence; zipping them looks obvious, writes a plausible-looking lock, and pairs the
  wrong hash with the wrong coordinate — which then surfaces as an integrity failure on a
  perfectly good build. Comparing hashes as a set needs no correspondence and tolerates two
  artifacts sharing a file name.

  A lock that no longer describes the manifest is discarded and rewritten rather than
  enforced against a question it does not answer. Reordering `[dependencies]` is not such a
  change. `onion clean` leaves the lock alone — it is an input to the next build, not an
  output of this one.

  **It is not an offline mode**, and the reason was checked rather than assumed:
  `coursierapi.Cache` exposes a location, a thread pool and a logger, and no cache policy at
  all, so there is no honest way to promise a build that never reaches the network.

## [0.11.1] - 2026-08-18

### Added

- **Semantic tokens in the language server — the colouring a TextMate grammar cannot
  produce.** A grammar decides what a word is from the shape of the line around it, so
  `Greeter`, `greet` and `count` all look alike: they are words. The server now classifies
  each identifier by what the document declares it to be — class, interface, enum, record,
  method, field, local — and marks declaration sites with the `declaration` modifier. Regex
  literals are distinguished from other scheme literals, and `Int` is coloured as a type
  rather than as the keyword the lexer calls it.

  **It emits nothing it is unsure of.** An identifier the document does not declare produces
  no token, and the editor falls back to the grammar. Semantic tokens override the grammar,
  so a guess would replace a right answer with a wrong one — which is also why soft keywords
  are left alone: `conforms` arrives as an identifier because only the parser's lookahead
  decides whether it is a keyword there, and colouring it from the lexer would light up a
  method of that name.

  Keyword, operator and primitive-type kinds are read from the generated parser's own token
  table rather than listed in the server, so a keyword added to the grammar is classified
  with no edit here. That hand-maintained duplication is exactly what let the TextMate
  grammar rot to 70% coverage; there is nothing to keep in step this time.

  This was the last item deferred from the highlighting work, and it was waiting on the
  parser recording real spans rather than single columns.

### Documentation

- **Documented `Archive::gunzipFile` in the stdlib reference (en/ja).** It sits
  right next to its documented sibling `gzipFile` in `Archive.java`, but was
  never mentioned in either `docs/reference/stdlib.md` or its Japanese copy,
  making it undiscoverable without reading the Java source. Added a
  `StdlibDocArchiveModuleParitySpec` drift guard, mirroring the existing
  `StdlibDocFilesModuleParitySpec`, so a future Archive method that lands
  without a doc update gets caught the same way.

## [0.11.0] - 2026-08-17

### Added

- **`onion fmt` — a formatter, and an LSP formatting provider sharing its implementation.**
  Onion had neither. The editor and the command line call the same code, so they cannot
  disagree about what formatted means; a difference there shows up as a file that changes
  every time it crosses between them.

  ```bash
  onion fmt              # src/ and tests/, or the paths you name
  onion fmt --check      # writes nothing, lists what would change, exits 1 if any
  ```

  **What it changes is deliberately small**, and every boundary was measured against the 182
  sample programs rather than guessed. It tightens the punctuation that binds to what it
  touches — `f(a , b)` to `f(a, b)`, `"=" .rep(60)` to `"=".rep(60)`, `f(- 1)` to `f(-1)` —
  and adds a missing newline at the end of a file. Everything else is reproduced byte for
  byte, tabs included.

  Three larger rules were built, measured, and removed, and the measurements are the
  interesting part:

  - **Reindenting from brace depth** rewrote 36% of the corpus, almost all of it wrongly.
    Onion's continuations are not bracketed — an expression body on the line after `=`, a
    method chain led by `.filter`, an expression carried on by a trailing `+`, a
    `from re"…"` clause under a record — and nothing lexical separates those from a
    misindented line, so the rule *reindents correct code*.
  - **Rebuilding whitespace from column numbers** turned two tab-indented programs into
    eight-space-indented ones, 110 lines in one file, because JavaCC advances a column to
    the next multiple of eight for a tab. Whitespace is now sliced out of the source text.
  - **Tightening brackets** (`f( a )` to `f(a)`, `f (1)` to `f(1)`) fired dozens of times
    and every firing was damage: `new Employee( 1, …)` above `new Employee(10, …)` is a
    right-aligned ID column, and an enum padded so its cases' argument lists line up uses
    five spaces on one line and one space on the next — so not even "collapse a lone space"
    separates the typo from the intent. There was no misplaced bracket space in the corpus
    for the rule to fix. A rule with no true positives is not a rule.

  A line break can end a statement in Onion, so the formatter never re-wraps either; moving
  one would be a semantic change rather than a cosmetic one.

  Two implementation notes worth recording. Onion's lexer is state-dependent and the parser
  drives those states, so a token manager run on its own never emits the `EOL` tokens a
  parse would see — the first version produced every file on a single line. And comments
  arrive attached to the *following* token, which means a comment at the end of a file hangs
  off `EOF`, and an implementation that stops at `EOF` deletes it without a word.

  Every file is verified before it is written: the formatted text must lex to the same token
  and comment stream, comments included, or the file is left untouched and reported. Three
  properties are checked over the whole corpus rather than on toy inputs — idempotence,
  every line break preserved, and the token stream unchanged. Worth recording that those
  properties caught none of the three bugs above: dedenting a whole class body, or expanding
  every tab, or closing up an aligned column, is idempotent and preserves every token.
  Running it over the corpus and reading the diff is what caught all three.

  Run over the corpus now, the whole formatter changes four lines. That is the honest size
  of the problem a formatter working from tokens alone can see here.

- **A drift guard tying `OnionCli`'s subcommands to both project CLI pages.** `onion doc`
  was a complete generator that no launcher, subcommand or documentation page ever named,
  and nothing failed for months because nothing was checking. The guard runs in both
  directions, so a page describing a command that no longer exists fails too.

- **`onion test --report-xml <path>` writes a JUnit XML report.** The run printed
  `N tests, N passed, N failed` and returned an exit code, which tells CI *that* something
  broke and never *what*. Every JVM CI system reads this format, so producing it is what
  turns a red build into an annotated diff.

  The escaping is the part that has to be right rather than approximately right: a test's
  captured output is arbitrary, and an XML file the CI parser rejects is worse than no file
  at all, because the failure then looks like a broken pipeline instead of a failing test.
  Markup, quotes and newlines are escaped; control characters XML 1.0 cannot represent at
  all — not even as a numeric reference — are replaced. Every assertion about this goes
  through a real XML parser, since only a parser can tell "looks wrong" from "will be
  rejected".

  A report that cannot be written is an error in its own right, so it neither turns a
  failing run into a passing one nor hides a real failure behind an I/O problem.

- **`onion doc` — the documentation generator is reachable.** `onion.tools.doc.OnionDoc`
  has been a complete generator for a while: six files, HTML output, its own `-d` and
  `--help`. Nothing could invoke it — no launcher script, no subcommand, no build task, no
  mention in any documentation — and a feature nobody can run is indistinguishable from one
  that does not exist.

  ```bash
  onion doc                                   # src/ into target/doc
  onion doc -d api src/main.on src/util.on    # explicit, works outside a project too
  ```

  Doc comments are carried across, not just signatures. Running it outside a project with
  no files named says where to run it instead of failing obscurely.

- **A debugger can now see variables.** Generated methods carried line numbers but no
  LocalVariableTable — `visitLocalVariable` appeared nowhere in the compiler — so a JVM
  debugger could step through `.on` source and then show nothing at all for any variable.
  Stepping without being able to look at a value is most of the way to useless.

  Verified with `jdb`, not just by inspecting the class file: stopping on a line of Onion
  source now prints `factor = 40`, `doubled = 80`, `running = 0`, `i = 0`.

  The names were the hard part. `LocalBinding` carries an index, a type and two flags but
  no name — by codegen the scopes are the only thing that still knows what the author
  called anything — so `LocalFrame` now exposes its index-to-name mapping. A slot with no
  name is left out rather than invented; a debugger showing `var3` is worse than showing
  nothing.

  `-g:none` omits the table for anyone who wants the smaller class file. Scripts run with
  `onion` always emit it, since they are compiled in memory and there is no artefact to
  keep small.

- **`onion.Db`: SQL over JDBC.** The driver is never bundled — it is whatever the project
  declares in `[dependencies]`, which is the first thing that became possible now that a
  project can depend on a jar at all.

  ```onion
  val rows = db.query("SELECT id, name FROM users WHERE age > ?", 18)
  db.transaction((conn) -> {
    conn.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", 100, 1)
    conn.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", 100, 2)
  })
  ```

  Values are always bound, never pasted into the SQL, so the API gives no way to write an
  injection by accident. A transaction commits when the body returns and rolls back when
  it throws — there is no `begin`/`commit` pair to forget between. Rows are `Map`s from
  column label to value in the order selected; two columns sharing a label is refused
  rather than silently losing one.

  `DriverManager` only offers drivers its *caller* can see, so a driver pulled in through
  `[dependencies]` — loaded by Onion's own classpath loader — would have come back as "No
  suitable driver" in exactly the case this class exists to serve. `Db` falls back to
  asking the context classloader directly.

- **`onion.Archive`: zip and gzip.** Onion could read and write files but not archives,
  which is most of what a release, a backup or a log rotation touches. **Extraction
  refuses to write outside the target directory** — an entry named
  `../../.ssh/authorized_keys` is the standard "zip slip" attack, and a naive extractor
  writes exactly where it is told. Entries carry a fixed timestamp, so zipping the same
  inputs twice produces the same bytes; an artefact that differs run to run cannot be
  checksummed or cached. Tar is left out: it needs a dependency.

- **`onion.Concurrent`: bounded parallelism, and the pieces needed to use it safely.**
  `Future` could already run one thing off the current thread, but there was no way to
  bound how many run at once, to share a counter, to hold a lock, or to hand work between
  threads.

  ```onion
  val pool = Concurrent::pool(4)
  val bodies = pool.mapAll(urls, (u) -> Http::get(u))
  pool.close()
  ```

  `mapAll` returns results in the *input's* order, not the order they finished — output
  that depends on timing cannot be tested — and reports a failing element only once every
  task has settled, so one bad input cannot leave workers running behind a caller that has
  given up. `withLock` releases even when the body throws. Channels are bounded, because
  an unbounded one hides a producer outrunning its consumer until memory runs out, and
  refuse `null`, which would be indistinguishable from an empty receive. Pool threads are
  daemons, so a forgotten `close()` cannot keep the JVM alive. Virtual threads are absent:
  they need Java 21 and Onion targets 17.

- **`onion.Net`: TCP sockets.** Onion could reach the network only as an HTTP client —
  there was no way to speak any other protocol, and no way to accept a connection at all.

  ```onion
  val listener = Net::listen("localhost", 0, 4)   // 0 asks the OS for a free port
  val peer = listener.accept()
  peer.writeLine("echo: " + peer.readLine())
  peer.close()
  ```

  Reads with `readLine`/`readAll`/`readBytes`, writes with `write`/`writeLine`/`writeBytes`
  (every write flushes), plus `timeout`, `closeWrite` for protocols that signal EOF by
  half-closing, and an idempotent `close`. Failures name the address that failed, so a
  `catch` says which host rather than just "connection refused".

- **`onion.Server`: an HTTP server**, on the JDK's own implementation, so it adds no
  dependency. A language that can only make requests and never answer one is missing half
  of what people write tools for.

  ```onion
  server.handleAll((req) -> select req.path() {
    case re"/users/(\d+)" (id): Server::json("{\"id\": " + id + "}")
    case "/health":             Server::text("ok")
    else:                       Server::notFound()
  })
  ```

  `handleAll` exists so routing can live in Onion — `select` over the path with `re"…"`
  patterns, the captured group bound as a value — rather than in a table of registered
  paths. Port 0 asks the OS for a free port and `port()` reports it, which is what makes a
  server testable without picking a number and hoping. Responses are immutable values
  built without touching a socket, so a handler is testable on its own, and a handler that
  throws produces a 500 instead of taking the server down or leaving a client waiting on a
  socket that never answers.

  `run/MiniWebService.on` is a complete service that answers its own requests and exits.

- **`ONION_JAVA_OPTS`, so JVM flags can be set at all.** Neither launcher passed anything
  through, so setting a heap size or attaching a debug agent meant abandoning `onion` and
  hand-writing a `java -cp` command:

  ```bash
  ONION_JAVA_OPTS="-Xmx4g" onion big-job.on
  ```

  `ONION_DEBUG_STARTUP=1` also stops the launcher silencing the JVM's class-sharing
  messages, which is how to find out why an archive is being ignored.

- **`[dependencies]` in `onion.toml`: a project can finally use a Java library.**
  `ProjectBuilder` compiled every project with `classPath = Seq.empty` and the manifest
  rejected any key outside `[package]`, so a project could not reference a single
  third-party jar — while the README described Onion as a language that "runs on the JVM
  and calls Java directly". Scripts could always take `-classpath`; projects could not.

  ```toml
  [dependencies]
  "org.postgresql:postgresql" = "42.7.3"
  ```

  Resolution is coursier's, embedded through `io.get-coursier:interface` — its Java API,
  with everything shaded, so it adds no transitive baggage beyond `slf4j-api` and cannot
  collide with anything Onion already ships. (The Scala API has no Scala 3 build and would
  have had to come in through `for3Use2_13` with its own dependency graph.) Transitives are
  resolved, and the jars reach the compile classpath, the run classpath and the test
  classpath alike — a library you compile against is there when the program runs.

- **`[[repositories]]`, searched before Maven Central and in the order written.**

  ```toml
  [[repositories]]
  url = "https://nexus.example.com/repository/maven-public"
  ```

  Central is kept rather than replaced. An array of tables rather than a plain
  `repositories = [...]`, because a bare key-value pair belongs to whatever table header
  precedes it: written after `[package]` the array form silently becomes
  `package.repositories`, and the table form can go anywhere in the file. Only absolute
  `http`/`https`/`file` URLs are accepted, so a typo is rejected at the repository line
  instead of resurfacing later as a resolution failure blamed on a dependency.

- **The editor now validates against the project's classpath** (`LspProjectClasspath`).
  Validation used a fixed classpath of `.`, so a symbol defined in a sibling file read as
  undefined, and — once dependencies existed — every type coming from a jar would have been
  underlined while `onion build` succeeded. Diagnostics that disagree with the build are
  worse than no diagnostics. Resolution is cached per project root and invalidated by the
  manifest's size and modification time, since resolving on every keystroke would be
  unusable. A malformed manifest or an unresolvable dependency degrades to validating
  without them and says so on the server's stderr, rather than refusing to validate the
  file the author is looking at.

- **`onion new` scaffolds a commented `[dependencies]` block.** Otherwise a new project
  gives no hint that Maven coordinates are accepted at all.

- **Syntax highlighting on the documentation site.** The site is built with MkDocs,
  which highlights through Pygments, and Pygments had no `onion` lexer — so all
  1213 ` ```onion ` fences across 71 pages rendered as unstyled plain text, with no
  warning from the build. `tools/pygments-onion/` is a Pygments lexer registered
  through the `pygments.lexers` entry point; `.github/workflows/docs.yml` installs
  it next to `mkdocs-material`, and because `mkdocs.yml` already sets
  `pygments_lang_class`, not a single markdown file had to change. The TextMate
  grammar could not be reused here: Pygments cannot read one, and the paths that
  would let it (a browser-side highlighter) give up static generation.

- **A drift guard over every highlighting grammar Onion ships**
  (`SyntaxHighlightingDriftSpec`). It derives the keyword set mechanically from
  `grammar/JJOnionParser.jj` — hard keywords from the `TOKEN` block, soft keywords
  from both spellings of semantic lookahead the grammar uses (`getToken(n).image
  .equals(...)` and the `la(...)` helpers, since `in` only ever appears as the
  latter) — and holds both the TextMate grammar and the Pygments lexer to it, in
  both directions. Forward catches a keyword added to the parser and forgotten in a
  highlighter; reverse catches a stale entry left behind after a keyword is removed.
  Keeping two extra grammars is only defensible with this in place.

- **Contributor documentation for highlighting**
  (`docs/contributing/syntax-highlighting.md` and its Japanese counterpart, both
  linked from the nav): why there are two highlighters, how to add a keyword, why
  soft-keyword rules are pinned with lookaheads, and why ` ```onion ` fences cannot
  be highlighted on github.com.

- **`.gitattributes` mapping `*.on` to Scala for display**
  (`linguist-language=Scala linguist-detectable=false`), so the ~170 sample programs
  under `run/` are no longer rendered as colourless text on github.com. Shared
  constructs come out right; Onion-only keywords such as `conforms` and `tool` do
  not, which is the honest cost of borrowing another grammar. The statistics
  override keeps the repository language bar from claiming these files are Scala.
  Fenced blocks are unaffected — GitHub resolves a fence's info string against
  Linguist's language list, which requires roughly 2000 indexed files per extension,
  and no per-repository override exists for it.

- **`tools/pygments-onion/check_docs.py`, run in CI before deploy and on pull
  requests.** A missing rule for a non-keyword form does not show up as a keyword
  gap — it shows up as a Pygments `Error` token, which renders as unstyled text and
  fails nothing. This lexes the fences directly and fails on any such token. The
  docs workflow now also builds (without deploying) on pull requests, so a broken
  site is caught before it reaches `main` rather than after.

- **`RunSamplesSpec` coverage for 34 more `run/` examples that were added
  without a corresponding test** (`Automaton`, `EmployeeManager`,
  `ExpenseAuditor`, `GameStore`, `MatrixCalc`, `MazeSolver`, `MiniRpg`,
  `MovieRecommender`, `MuseumCollection`, `MusicLibrary`,
  `NationalParkTracker`, `NutritionTracker`, `ParkingGarage`,
  `PayrollReport`, `PlaylistManager`, `PokerHands`, `RankedChoice`,
  `RecipeManager`, `RestaurantOrders`, `ShipmentTracker`, `ShoppingCart`,
  `SnippetLibrary`, `SortingShowcase`, `SpaceMission`, `StockPortfolio`,
  `StudentGradeBook`, `Sudoku`, `SudokuSolver`, `TaskPlanner`,
  `TimesheetTracker`, `TournamentStandings`, `TournamentTracker`,
  `VirtualMachine`, `VirtualShell`). Each already ran and compiled cleanly;
  `sbt test` simply never exercised them, so a regression in any of them
  would have gone unnoticed. `ExpenseAuditor` is a `tool`-declared script, so
  its test drives the auto-CLI with a temp input/output file pair rather
  than calling it with no arguments. `Calculator.on` (a Swing GUI program
  requiring a display) is intentionally left uncovered, matching how the
  repository already treats headless-incompatible samples.

- **`RunSamplesSpec` coverage for 20 `run/` examples that were added without a
  corresponding test** (`ChemCalculator`, `CodeContest`, `CryptoPortfolio`,
  `DependencyResolver`, `DnaAnalyzer`, `ElevatorDispatcher`,
  `GenericLeaderboard`, `GeneticSequencer`, `HospitalWard`, `InsuranceClaims`,
  `KaraokeNight`, `MarkdownConverter`, `MiniTypeChecker`, `NetworkMonitor`,
  `PlantCare`, `RecipeVault`, `RuleEngine`, `SortAlgorithms`, `SupplyChain`,
  `TransitPlanner`). Each already ran and compiled cleanly; `sbt test` simply
  never exercised them, so a future regression in any of them would have gone
  unnoticed. This closes the coverage gap.

- **`RecordJsonSpec`/`RecordYamlSpec`, regression coverage for `derive!(Json, Yaml)`
  rejecting a nullable scalar record component** (`nickname: String?`). Docs describe
  `derive!` as supporting "scalar components only"; `ScalarConversions.isDerivable`
  only matches the exact non-null scalar types, so a nullable wrapper around an
  otherwise-supported type is already rejected with E0062 the same way a wholly
  unsupported component type (a nested record) is -- but only the JSON side had a
  regression for the nested-record case, and neither side tested the nullable case.
  Both already behaved correctly; this closes a coverage gap rather than a live bug.

- **`TryInArrayIndexAssignmentSpec`, regression coverage for `try`/`catch` used
  as *both* the index and the assigned value of the same array write**
  (`arr[try {...} catch {...}] = try {...} catch {...}`). The existing index-side
  fix (#758 write-side sibling of #745/#669) and the pre-existing value-side
  handling were each tested individually, but never combined in the same
  write; the combination already passed, so this closes a coverage gap rather
  than a live bug.
- **`TryInEnumCaseArgSpec`, regression coverage for `try`/`catch` used as an
  argument to an `enum case` constructor invocation**
  (`new Circle(try {...} catch {...})`). The generated constructor already
  flows through the same `visitNewObject` path ordinary records use (already
  hardened by the #669/#745/#752 fixes), so this already passed; this closes
  a coverage gap rather than a live bug.
- **`TryInCompoundBoxedLocalAssignmentSpec`, regression coverage for `try`/`catch`
  on the right-hand side of a compound assignment (`+=`) to a closure-captured
  (boxed) local variable** (`x += try {...} catch {...}`), both from inside the
  capturing closure and from the declaring scope. `TryInCompoundFieldArrayAssignmentSpec`
  already locked in the same `BinaryTerm`-wrapped-`try` shape for field/array
  compound-assignment targets, but the boxed-local path in `emitSetLocal`
  (`AsmCodeGeneration.scala`) was untested. `TermContainsTry.contains` already
  recurses generically through a term's children, so both cases already passed;
  this closes a coverage gap rather than a live bug.

### Fixed

- **`onion fmt` collapsed a whitespace-only file to empty.** A source with no real token
  and no comment produced no lexeme at all, so the formatter's main loop was a no-op and
  the trailing-newline logic never ran either — a file of nothing but blank lines was
  silently rewritten down to an empty string and reported as changed. No rule anchors to
  whitespace with no token around it, so it is now left exactly as written, the same
  guarantee already held for whitespace next to a token.

- **Diagnostics underlined one token instead of the construct they were about.** Every
  leaf already spanned its own token, but a compound expression was anchored at its
  operator and a call at its name, so:

  ```
    5 |     val n: Int = 3 * "x"
      |                    ^
    4 |     val total: Int = "hello" + someUndefinedThing(1, 2)
      |                                ~~~~~~~~~~~~~~~~~~
  ```

  became:

  ```
    5 |     val n: Int = 3 * "x"
      |                  ~~~~~~~
    4 |     val total: Int = "hello" + someUndefinedThing(1, 2)
      |                                ~~~~~~~~~~~~~~~~~~~~~~~~
  ```

  Binary expressions get their extent by merging their operands' locations
  (`Location.spanningTo`) rather than by capturing tokens, which is why it composes: the
  left operand of `1 + 2 + "x"` is itself a binary expression and the outer span covers
  all of it. Calls run from the method name through the closing paren. A call
  deliberately does **not** include its receiver: in a chain like `a.b().c()` every link
  would otherwise underline everything before it.

  The reported column moves with the underline — from the operator to the start of the
  expression, and from `::` to the method name. `onion run` reporting a project's entry
  point now points at `println` rather than at the `::` before it.

- **Rename in the editor rewrote comments and string literals.** It replaced every
  whole-word occurrence of the identifier in the file, so renaming `count` also rewrote
  `"count of items"` and `// count starts at zero`. An edit that changes a program's output
  while calling itself a rename is the worst kind: the file still compiles, so nothing
  downstream catches it.

  A new `SourceRegions` scanner classifies each offset as code or not, handling `//` and
  `/* */`, string escapes, character literals, and scheme-prefixed raw literals (`re"…"`).
  `#{ … }` interpolation counts as **code** — the `count` in `"n=#{count}"` is a real
  reference, and skipping everything between quotes would have broken the file the other
  way. Renaming from a position inside a comment or a literal is now refused, since the
  word there is not a reference to anything.

  Rename remains file-local and not scope-aware, and the language-server documentation now
  says so plainly rather than leaving it to be discovered. Fixing that needs the typed AST
  with real source ranges, and the parser currently produces spans only a token wide.

- **Onion started in half the time depending on how you got it, and nothing noticed.**
  There are two launcher implementations — the `bin/` scripts, which the distribution zip
  ships and a source checkout runs, and the ones `install.sh` writes for a `curl | sh`
  install — and they had drifted apart. The installed launchers had a class-data-sharing
  archive and the `--sun-misc-unsafe-memory-access=allow` workaround; the `bin/` ones had
  neither. So the same program took 0.73s from the zip and 0.38s from the installer
  (JDK 25, `onion run/Hello.on`), and printed four lines of JVM deprecation warnings on
  every single run in the first case and none in the second.

  The `bin/` launchers now share one `bin/onion-jvm.sh` (and `onion-jvm.bat`) that does
  both. `LauncherParitySpec` fails the build if either implementation gains a capability
  the other lacks — it caught the archive filename disagreeing between the POSIX and
  Windows halves while it was being written.

  The Unsafe workaround needs a JDK version check, and the option does not exist before
  JDK 23 — an unknown `--` option makes the JVM refuse to start. The version is read from
  the JDK's own `release` file rather than probed by running `java`, which would add a
  process launch to every invocation. The Windows scripts do not do this check yet, so
  they still show the warnings.

- **A stale class-data-sharing archive narrated itself on every run.** After a JDK
  upgrade or a moved install the JVM refuses the archive and carries on correctly — but
  says so on stderr each time, which lands in anything capturing a tool's output. Both
  launchers now pass `-Xshare:auto` with the CDS log tags off, so a stale archive costs
  the speedup and nothing else. `ONION_DEBUG_STARTUP=1` shows the messages again.

- **The build cache could serve classes compiled against an old classpath.** The build
  fingerprint hashed the manifest bytes, which pin only *direct* dependencies; a transitive
  version can move without `onion.toml` changing a byte. The whole resolved set now feeds
  the fingerprint, and `BuildFingerprint.SchemaVersion` is bumped to 2 so build state
  written before this is rejected rather than read back with the wrong meaning.

- **The TextMate grammar was missing the most distinctive parts of the language.**
  It had drifted to roughly 70% keyword coverage: `trait`, `instance` and the
  lowercase `void` were absent, as was every soft keyword (`tool`, `requires`,
  `shape`, `law`, `example`, `from`, `derive`), along with scheme-prefixed raw
  literals (`re"…"`, `file"…"`, `http"…"` — 225 occurrences in the docs alone), the
  `|>` pipeline operator, and backtick-quoted identifiers. Soft-keyword rules are
  pinned with lookaheads so that a variable named `shape` or `tool` stays
  uncoloured, and keyword rules deliberately precede the scheme-literal rules so
  that `return"x"` highlights as a keyword followed by a string, matching the
  push-back the lexer in `JJOnionParser.jj` performs. Verified against
  `vscode-textmate` and `vscode-oniguruma` — the engine VS Code itself uses.
  (`vscode-onion` 1.1.0 → 1.2.0.)

- **A `#!` shebang was unlexable.** `Parsing.scala` strips a shebang from the first
  line of a script, but neither highlighter knew about it, so seven code blocks
  across five documentation pages were emitting an error token for the `#`. Pinned
  to line one with `\A`, matching what the compiler accepts.

- **An ASCII-art inheritance diagram in `docs/guide/inheritance.md` was tagged
  ` ```onion `.** It is a tree drawing, not code. It survived because
  `DocExamplesCompileSpec` only scans `docs/examples/`, so nothing under
  `docs/guide/` was ever checked; the new fence lexing is what surfaced it.

- **Three tests failed under `-Duser.language=ja`, which nobody could see.** Once the
  locale flag actually took effect, `ErrorCountMessageSpec` and
  `ErrorMessageJapaneseTranslationSpec` went red — and both for the same reason.
  `ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)` does not return English
  here: there is no `errorMessage_en.properties` (the base file *is* the English one),
  and when the requested locale has no bundle of its own the JDK falls back to the
  **default locale** before it falls back to the base file. On a `ja_JP` machine the
  "English" bundle was therefore the Japanese one, so a test asserting the Japanese text
  differs from the English text compared Japanese with Japanese. The two specs now take
  their bundles from a new `MessageBundles` test helper, which pins each language with
  `ResourceBundle.Control.getNoFallbackControl`. Production behaviour is unchanged and
  deliberately different: `onion.compiler.toolbox.Message` follows the ambient locale,
  because a user's diagnostics should follow their machine.

- **`QualityBarSpec`'s test-count check used fixed bounds, and the recorded figure had
  drifted inside them.** The bar said 3410 against an actual 3595 while sitting
  comfortably within a hardcoded 2700–4000 window — and that window rots in both
  directions as the suite grows, eventually failing spuriously at the ceiling while the
  floor stops catching anything. The band is now derived from the number of tests
  declared by hand across `src/test/scala`, so it moves with the suite.

- **The documented way to verify a release stopped working after the move to sbt 2, and
  failed silently.** `CLAUDE.md`, `docs/quality-bar.md` and `docs/RELEASING.md` all told
  you to run `sbt -Duser.language=en test`. Under sbt 2.0.6 that command can test nothing
  and still exit 0, for two independent reasons: `test` now delegates to `testQuick`, so a
  second run over an unchanged tree prints `No tests to run`; and `-D` flags only reach a
  *freshly started* sbt server, so passing `-Duser.language=ja` to a server already running
  under `en` leaves `java.util.Locale.getDefault()` reporting `en_JP`. Running the two
  locales back to back therefore reported two green runs having tested one locale once.
  All three documents (and their Japanese copies) now say
  `sbt shutdown && sbt -Duser.language=<xx> testFull`. CI was never affected — the
  incremental state lives in `target/`, which `setup-java`'s `cache: 'sbt'` does not
  cache, so every CI run starts cold.

- **A change to the highlighting lexer did not redeploy the documentation site.**
  The docs workflow triggered only on `docs/**`, `mkdocs.yml` and itself, so a fix
  to how every code block renders would have sat unpublished. `tools/pygments-onion/**`
  is now a trigger path.

## [0.10.37] - 2026-08-16

### Added

- **`ThreeLevelNestedClosureCapturedVariableSpec`, regression coverage for a
  `var` relayed through *three* levels of closure nesting.**
  `NestedClosureCapturedVariableSpec` (#756) locked in the two-levels-deep
  case for a variable captured from an outer scope, relayed through one
  intermediate closure, and mutated only by the innermost one. Both
  `CapturedVariableScanner` (typing) and `ClosureCodegen.emitNewClosure`'s
  `adjustedFrame`-based boxed-ness lookup (codegen) were written to recurse
  to arbitrary depth rather than special-cased for exactly two levels, but
  nothing exercised a third level of relay -- including the case where the
  innermost closure's assigned value runs its own `try`/`catch` (the
  #745/#669 stack-corruption family, reached via the relay path). Both new
  cases already passed, so this closes a coverage gap rather than a live bug.
- **`TryInCompoundFieldArrayAssignmentSpec`, regression coverage for `try`/`catch`
  as the right-hand side of a compound assignment (`+=`) on a field or array
  element** (`obj.field += try {...} catch {...}`, `arr[i] += try {...} catch
  {...}`). Compound assignment desugars `target op= value` to `target = target
  op value`, so the `try` ends up nested inside a `BinaryTerm` rather than
  directly as the assigned value -- unlike the plain-assignment cases already
  covered by `TryInFieldAssignmentSpec`/`TryInArrayIndexAssignmentSpec`. This
  locks in that `TermContainsTry.contains`'s generic recursion still finds the
  nested `try` and `visitSetField`/`visitSetArray` still spill the
  receiver/array-and-index correctly (the #745/#752 stack-corruption family);
  all three new cases already passed, so this closes a coverage gap rather
  than a live bug.
- **`run/TryCatchEdgeCases.on`, a regression sample combining `try`/`catch`
  with expression positions not covered by any single existing
  `TryInXxxSpec`.** A `select` scrutinee, a `select` case guard, a `while`
  condition, a `foreach` iterable, a record constructor argument, an
  `is`/`as` target, a compound-assignment (`+=`) right-hand side, a string
  interpolation segment, and a closure reassigning a per-iteration
  captured `var` -- all via `try`/`catch` -- exercised together as one
  combined integration check (`Shell.Success(138)`), on top of the
  existing narrowly-scoped unit coverage for the array-index,
  binary-operand, and field/local-assignment positions (#669, #745, #752
  and friends).

### Fixed

- **`try`/`catch` as the index of an array *write* (`arr[try {...} catch
  {...}] = value`) no longer crashes the compiler.** `visitSetArray` only
  spilled the array reference into a local when the assigned *value* could
  run its own `try`, never when the *index* could -- so an array reference
  already pushed before evaluating such an index was silently discarded by
  the JVM clearing the operand stack on the exceptional path, desyncing the
  merged stack shape from the normal path with an `[I0000]` internal error.
  This is the write-side sibling of the array-read fix for the same
  underlying issue (#745, #752, and friends) -- the read path (`visitRefArray`)
  already checked its index; the write path was missing the same check.
- **A `var` declared inside a closure's own body and mutated only by a
  closure nested inside it now correctly shares storage with the declaring
  closure.** `ClosureCodegen.generateClosureMethod` built each closure's own
  local-variable context with only parameter registration, never consulting
  the boxed-variable information typing already computes for the closure's
  frame -- unlike the top-level-method codegen path, which always does
  both. So a `var` declared inside a closure and mutated by a closure
  nested inside it got a disconnected unboxed copy per closure level
  instead of sharing one heap-boxed cell, silently dropping the inner
  closure's write. When the assigned value ran its own `try`/`catch`, the
  same gap crashed the compiler with `[I0000] Inconsistent stackmap
  frames` (the same family as #745/#669). This is the same-closure sibling
  of the cross-closure-relay fix for issue #756.

- **A `var` mutated only by a closure nested two (or more) levels deep now
  correctly shares storage with the declaring scope.** `CapturedVariableScanner`
  (used during typing to decide which variables need heap-boxed storage)
  only ever examined the closure immediately enclosing a variable's use; a
  variable referenced solely inside a closure nested *inside* that one was
  never marked as boxed, so each closure level silently got its own
  disconnected copy instead of sharing one cell — a write from the innermost
  closure was invisible to the declaring scope. This is the transitive-capture
  sibling of the single-level fix in #214. A related codegen bug compounded
  it: even once boxing was correctly inferred, `ClosureCodegen.emitNewClosure`
  determined a captured variable's boxed-ness via a lookup that only
  understood a closure's own frame-0 locals, so a variable merely *relayed*
  through an intermediate closure was still treated as unboxed. Combined with
  a `try`/`catch` as the assigned value, the unboxed path also crashed the
  compiler with an `[I0000]` internal error (inconsistent stackmap frames):
  the JVM clears the operand stack when dispatching to the `catch` handler,
  discarding the pending `this` reference the unboxed-assignment path had
  already pushed, desyncing the merged stack shape from the normal path (the
  same family of issue as #745/#669, reached via a different unguarded path).
- **`try`/`catch` as the right-hand operand of a binary operator no longer
  crashes the compiler.** `1 + (try { ... } catch { ... })`, and the same for
  other arithmetic, bitwise, and comparison operators, previously crashed
  with an `[I0000]` internal error during bytecode verification: the JVM
  clears the operand stack when dispatching to an exception handler, so a
  left operand already pushed before the `try`'d right operand ran was
  silently discarded on the exceptional path. This is the binary-operator
  sibling of the constructor-argument, list/map-literal, and field/array-
  assignment fixes for the same underlying issue (#669 and friends).
- **`try`/`catch` as an array-index expression no longer crashes the
  compiler.** `arr[try { ... } catch { ... }]` previously crashed with an
  `[I0000]` internal error during bytecode verification: the array reference
  was pushed onto the operand stack before evaluating the index, and the JVM
  clears the stack when dispatching to an exception handler, silently
  discarding that reference on the exceptional path. This is the array-read
  sibling of the array-write fix for the same underlying issue (#745 and
  friends).
- **`try`/`catch` as a later dimension of a multi-dimensional array creation
  no longer crashes the compiler.** `new Int[3][try { ... } catch { ... }]`
  previously crashed with an `[I0000]` internal error during bytecode
  verification: every dimension was pushed onto the operand stack
  unconditionally before the `multianewarray` instruction, and the JVM
  clears the stack when dispatching to an exception handler, silently
  discarding earlier dimensions on the exceptional path. This is the
  array-creation sibling of the array-index and array-assignment fixes for
  the same underlying issue (#745, #752, and friends).
- **`try`/`catch` as the index of a safe array-read (`arr?[...]`) no longer
  crashes the compiler.** `arr?[try { ... } catch { ... }]` previously
  crashed with an `[I0000]` internal error during bytecode verification:
  the array reference (plus a duplicate for the null check) was pushed onto
  the operand stack before evaluating the index, and the JVM clears the
  stack when dispatching to an exception handler, silently discarding the
  pending reference on the exceptional path. This is the safe-indexing
  sibling of the plain array-index fix for the same underlying issue
  (#745, #752, and friends).
- **`try`/`catch` assigned to a closure-captured (boxed) `var` no longer
  crashes the compiler.** `x = try { ... } catch { ... }`, where `x` is a
  `var` that some closure captures and mutates (so it is stored in a heap
  box rather than a plain local slot), previously crashed with an `[I0000]`
  internal error during bytecode verification whether the assignment ran
  inside the capturing closure or in the variable's declaring scope: the
  box reference was pushed onto the operand stack before evaluating the
  right-hand side, and the JVM clears the stack when dispatching to an
  exception handler, silently discarding the pending box reference on the
  exceptional path. This is the boxed-local-variable sibling of the
  field/array-assignment fixes for the same underlying issue (#745 and
  friends).

## [0.10.36] - 2026-08-15

### Added

- **`run/AccessLogAnalyzer.on`, a 325-line HTTP access-log analyzer.**
  Parses Common-Log-Format lines via `record LogEntry from re"..."`
  (`parseAll`), an ADT case-enum (`HttpStatus`: `Info`/`Success`/
  `Redirect`/`ClientError`/`ServerError`), a class with mutable state
  (`LogAnalyzer`, `PathSummary`), extension methods on `Int`/`Double`/
  `String`, `do[Option]` monadic chaining, `|>` pipeline, collection
  pipelines (`groupBy`/`sortedBy`/`filter`/`map`/`fold`/`count`/
  `partition`/`distinct`), `foreach (k, v) in map`, and recursion.
- **`run/DnaAnalyzer.on`, a 469-line DNA sequence analysis toolkit.**
  Exercises an ADT enum with shared methods (`Nucleotide`), a
  data-carrying ADT enum (`GCCategory`), records with `from re"..."`
  and `example` clauses (`FastaHeader`, `SequenceRegion`, `MotifMatch`),
  extension methods on `Int`/`String`, `do[Option]` composition,
  collection pipelines (`map`/`filter`/`fold`/`partition`/`flatMap`/
  `sortedBy`/`groupBy`), regex `select` patterns, and nullable types.
- **`run/ChemCalculator.on`, a 274-line chemical formula analyser.**
  Exercises a plain enum (`ElementGroup`) alongside a data-carrying enum
  (`BondType`), records with body methods (`Element`, `FormulaComponent`),
  an interface (`Describable`) implemented by a class (`Compound`), `select`
  pattern matching on enum values, closures stored in `val` and passed as
  arguments, and a wide sweep of collection pipelines (`filter`/`map`/
  `fold`/`sortedBy`/`groupBy`/`find`/`partition`/`zip`/`distinct`/
  `Colls::concat`), plus `try`/`catch`, tail recursion, and
  `foreach (k, v) in map`.
- **`run/NetworkMonitor.on`, a 270-line network traffic analysis and
  alerting sample.** Parses Apache-style access log lines via
  `record ... from re"..."` (`LogEntry`), and exercises an ADT case-enum
  (`Alert`: `HighErrorRate`/`SlowEndpoint`/`SuspiciousIp`/`QuotaExceeded`),
  a data-carrying enum (`Method`), a plain enum (`AlertLevel`), a record
  with body methods (`PathStats`), extension methods on `Int`/`String`,
  regex `select` patterns, `do[List]` comprehension, collection pipelines
  (`groupBy`/`sortedBy`/`filter`/`map`/`count`/`any`/`partition`),
  `foreach (k, v) in map`, and tail recursion.
- **`run/MarkdownConverter.on`, a 371-line Markdown-to-HTML converter.**
  Exercises an ADT case-enum with shared methods (`Block`: `Heading`/
  `Paragraph`/`BulletItem`/`OrderedItem`/`CodeBlock`/`Blockquote`/`HRule`),
  `select` + type-pattern binding, a `record` (`DocStats`), `extension
  String` helpers, a cursor-style parser class with mutable state,
  collection pipelines (`filter`/`map`/`groupBy`/`partition`/`find`/
  `sortedBy`), nullable types with null-guards, string interpolation,
  `while`/`foreach`, and `Regex::replace`/`Regex::groups` for inline
  transforms.
- **`run/ElevatorDispatcher.on`, a 352-line multi-car elevator dispatch
  simulator.** A `record ... from re"..."` dispatcher-log parser
  (`CallLogEntry`) with `example` clauses, a plain enum (`Direction`), a
  data-carrying enum (`DoorState`), an ADT case-enum (`DispatchEvent`:
  `Arrived`/`DoorsOpened`/`Overloaded`/`WentIdle`), a `record` with body
  methods (`RiderRequest`), an interface (`Dispatchable`) implemented by a
  class with mutable state (`ElevatorCar`), extension methods on `Int`/
  `String`, `do[Option]` chaining, regex `select` patterns, collection
  pipelines (`filter`/`map`/`fold`/`sortedBy`/`groupBy`/`find`/`partition`/
  `zip`/`distinct`), `foreach (k, v) in map`, nullable types, `try`/`catch`,
  and tail recursion.

### Fixed

- **Calling `.method()` (not `?.method()`) on a nullable class-typed value
  (e.g. `x: Box?`) reported the generic, unhelpful `E0041`
  ("type Box? is not a valid method call target.") instead of the dedicated
  null-safety diagnostic `E0070`, which points at `?.` / `?:` / `!!` / a null
  check.** Field access on the same nullable value already reported `E0070`;
  `MethodTargetTypingSupport`'s method-call path just never had a
  `NullableType` case to route through it.
- **Assigning a `try { ... } catch e: T { ... }` expression directly to a
  field or array element crashed the compiler with `[I0000] Internal
  compiler error in LawCheck: Inconsistent stackmap frames`** (issue #745),
  most visibly inside a constructor. The JVM clears the operand stack when
  it dispatches to an exception handler, so a receiver (or array ref/index)
  already pushed before the `try` silently vanished on the exceptional path,
  desyncing the merged stack shape from the normal path — the field/array
  sibling of issue #669's call-argument bug. `visitSetField`/`visitSetArray`
  now spill an already-pushed target (and index) into locals before
  evaluating a value that may run its own `try`, mirroring the existing
  `emitArgumentsWithAdaptation` fix for call arguments.
- **A `try { ... } catch e: T { ... }` expression used as an array-literal
  element (`new Int[]{1, try { ... } catch _: Exception { -1 }, 3}`) crashed
  the compiler with the same `[I0000] Inconsistent stackmap frames` error**,
  the array-literal sibling of the fix above. `visitNewArrayWithValues` left
  the array reference and element index on the operand stack (via `dup()`
  and a pushed constant) while evaluating each element, which the JVM
  silently discards on the exceptional path; it now evaluates any element
  that may run its own `try` first with an empty operand stack, stashes the
  result in a local, and only then reloads the array reference, index, and
  value for the `arrayStore`, mirroring `visitSetField`/`visitSetArray`.
- **A `try { ... } catch e: T { ... }` expression used as a constructor
  argument, a super-constructor argument, a safe-call (`?.`) argument, a
  `List`-literal element, or a `Map`-literal key/value crashed the compiler
  with the same `[I0000] Inconsistent stackmap frames` error** — four more
  siblings of the fixes above. For the safe-call case, the already-pushed,
  already-initialized target just needed the same "spill to a local, reload
  after" treatment as `visitSetField`/`visitNewArrayWithValues` (now applied
  in `visitSafeCall`), and the `List`/`Map`-literal fix follows the same
  pattern. The constructor and super-constructor cases needed a different
  fix: the value `new` pushes (or `this`, before `super(...)`/`this(...)`
  runs) is not an ordinary reference but a JVM-tracked "uninitialized"
  value, which cannot survive being spilled into a local across a `try`'s
  merge the way an already-initialized receiver can — attempting that
  produced a *different* verifier rejection. `visitNewObject` and
  `codeConstructor`'s super/self-delegation call now evaluate every
  argument into a plain local *before* emitting `new`/loading `this` at
  all, so no uninitialized value is ever in flight while an argument's own
  `try` runs.

## [0.10.35] - 2026-08-14

### Added

- **`run/GenericLeaderboard.on`, a 273-line polymorphic leaderboard sample.**
  The first `run/` sample to exercise Onion's type-class machinery
  (`trait`/`instance`) as a first-class feature of a realistic program:
  two independent type classes (`Scoreable[T]`, `Formattable[T]`), a
  multi-constraint generic function (`[T: Scoreable + Formattable]`), and
  `Trait[T]::method()` dictionary dispatch, shared across two domains
  (chess ratings and F1 standings).
- **`run/GeneticSequencer.on`, a 482-line DNA sequence analysis toolkit.**
  Covers a bioinformatics domain not yet in the corpus: records with
  methods (`Sequence`, `ORF`, `RestrictionSite`), a 21-variant
  data-carrying enum (`AminoAcid`), a plain enum with `select` dispatch
  (`Nucleotide`), an interface/class validator hierarchy, collection
  pipelines (`map`/`filter`/`fold`/`groupBy`/`flatMap`/`sortedBy`/`find`),
  and tail-recursive k-mer frequency building.
- **`run/KaraokeNight.on`, a 529-line karaoke night management simulator.**
  Records with methods (`Song`, `Singer`, `Performance`); a data-carrying
  enum (`Difficulty` with a multiplier); an ADT case-enum (`ScoreGrade`:
  `Perfect`/`Good`/`Okay`/`NeedsWork`); interfaces (`Reportable`,
  `Rankable`); classes with mutable state; extension methods on `Int`,
  `Double`, `String`; collection pipelines including `zip` and
  `partition`; and `select`/type-pattern dispatch.
- **`run/CodeContest.on`, a 543-line ICPC-style competitive programming
  contest simulator.** Data-carrying enum (`Language`), plain enum
  (`Verdict`), ADT case-enum (`ContestPhase`); records with `example`
  clauses (`Problem`, `Submission`) and a generic record (`Ranked[T]`);
  a class/interface pair (`Contest`, `Contestant conforms Scorable`);
  `do[Option]` and `do[List]` monadic notation; collection pipelines
  (`map`/`filter`/`fold`/`groupBy`/`sortedBy`/`distinct`/`find`); and
  the `|>` pipeline operator.
- **`run/InsuranceClaims.on`, a 349-line insurance claims processing
  system.** Data-carrying enum (`CoverageType`), ADT case-enum
  (`AdjustmentDecision`: `FullApproval`/`PartialApproval`/`Denial`);
  records with methods (`Claimant`, `Policy`, `Claim`, `AdjustmentRecord`);
  an interface/class pair (`Auditable`, `ClaimsProcessor`); extension
  methods on `Int`, `Double`, `String`; collection pipelines
  (`filter`/`groupBy`/`partition`/`find`); `select`/type-pattern dispatch;
  nullable types; closures; and `try`/`catch`.
- **`run/PlantCare.on`, a 299-line plant care and growth tracker.** ADT
  sealed enums with embedded methods (`PlantFamily`, `CareAction`);
  records with instance methods (`Plant`, `CareEvent`); typed generic
  collections (`List[Plant]`, `Map[String, List[Plant]]`); `groupBy`/
  `sortedBy` collection pipelines; `foreach` map destructuring; `while`
  loops; string interpolation; and `select`/`is` pattern matching.

### Fixed

- **`docs/ja/GENERICS_DESIGN.md` linked to two anchors that MkDocs could
  never generate.** Its cross-references to `reference/specification.md#ジェネリクス`
  and `guide/classes-and-objects.md#ジェネリッククラス` relied on MkDocs
  slugifying Japanese heading text, but the slugifier can't build a stable
  slug from it and falls back to positional ids (`#_6`, `#_9`) instead —
  confirmed broken via `mkdocs build --strict`. The two target headings now
  carry explicit `attr_list` ids (`#generics`, `#generic-classes`, mirroring
  the English page's slugs) and the links point at those.

## [0.10.34] - 2026-08-14

### Fixed

- **`extension List[Int]` and `extension List[String]` (or any two primitive-extension
  blocks differing only in type argument) in the same file collided as duplicate class
  definitions (E0008).** `TypingHeaderPass.extractTypeName` / `extractTypeDescName`
  discarded the type-argument list from `ParameterizedType` when generating the
  extension container class name, so both produced `Extension$List`. Now the
  type-argument names are folded into the generated name (`Extension$List_Int` vs
  `Extension$List_String`).
- **`select` with an empty `else:` body silently re-executed the preceding
  `case` branch's body instead of doing nothing.** In the JavaCC
  `select_expression()` production the shared accumulator `ss` was
  initialized to `null` and reassigned per `case`; an empty `else:` skipped
  the optional `[ss=block_elements()]` assignment, so `ss` still held the
  last `case` branch's statement list and the generated `elseBlock` was a
  copy of it. Reset `ss` to an empty list immediately before the optional
  assignment (mirroring the existing pattern in `block()`), so an empty
  `else:` now correctly executes nothing.

### Added

- **`run/HospitalWard.on`, a 261-line hospital patient-ward management sample.**
  Exercises case-enum ADTs, a data-carrying homogeneous enum, records with body
  methods, extension methods on `Int`/`Double`, collection pipelines
  (`groupBy`/`sortedBy`/`filter`/`fold`/`partition`/`find`), foreach
  map-destructuring, nullable find with a null guard, closures, and recursion.
- **`run/RecipeVault.on`, a recipe-management sample.** Exercises ADT enums,
  records, collection pipelines, closures, do-notation, and nullable fields.
- **`run/MiniTypeChecker.on`, a 489-line bidirectional type checker for a small
  lambda calculus.** Exercises self-referential ADT enums (`Ty`, `Expr`),
  records, a `HashMap`-backed functional-update environment, extension
  methods on `Int`/`String`, `select` + type-pattern dispatch, and a
  39-case self-checking test runner.
- **`run/SupplyChain.on`, a 338-line purchase-order monitoring sample.**
  Exercises data-carrying and ADT case-enums, records with body methods,
  extension methods on `Int`/`String`/`Double`, and a broad collection
  pipeline (`groupBy`/`sortedBy`/`distinct`/`partition`/`zip`/`flatMap`).

## [0.10.33] - 2026-08-13

### Documentation

- **Marked `docs/GENERICS_DESIGN.md` (and its ja copy) as shipped.** The
  page, linked from the mkdocs nav under "Design Notes", still framed
  generics as an unimplemented TODO plan ("Goal: Add basic generics...")
  even though generics have been fully implemented for a long time.
  Added a "Status: shipped" note pointing to
  `docs/reference/specification.md` and `docs/guide/classes-and-objects.md`
  for the current syntax/semantics, following the same pattern already
  used by `docs/design/roadmap.md`.
- **Documented all 15 `--Wno` warning codes (`W0001`–`W0015`) in a new
  reference table in `docs/tools/compiler.md` and `docs/ja/tools/compiler.md`.**
  The `--Wno <codes>` option showed a single example (`W0001,unused-parameter`)
  but never enumerated the warning categories, their codes, or their
  `--Wno` name aliases defined in `WarningCategory` (`Warning.scala`) —
  unlike `E0xxx` error codes, which get a full guarded reference page. Added
  a drift-guard spec (`WarningCodeDocCoverageSpec`), mirroring
  `ErrorCodeDocCoverageSpec`, so future warning categories get caught the
  same way.

### Added

- **`run/SprintPlanner.on`, a 291-line agile sprint-planning sample.** Covers
  a data-carrying enum (`Priority(weight: Int)`), a plain enum (`Status`)
  matched via `select this`, an ADT case-enum (`CapacityStatus` with
  `CapOk`/`CapWarn`/`CapOver`) matched by type pattern, a record with a
  nullable field (`Task(..., assignee: String?)`), a generic record
  (`Ranked[T](value: T, score: Int)`), an interface implemented with
  `conforms` (`Reportable`), extension methods on `Int`/`String`, broad
  collection pipelines (`filter`/`map`/`fold`/`groupBy`/`sortedBy`/
  `distinct`/`partition`/`zip`/`reduce`/`find`), a closure stored in a
  `val`, string interpolation, `try`/`catch`, a tail-recursive helper, and
  `foreach` over both exclusive and inclusive ranges.
- **`run/NationalParkTracker.on`, a 478-line national-park visitor tracking and
  reporting sample.** Covers extension methods on `String`/`Int`/`Double`, a
  plain enum (`ParkType`, `Season`) with `public:` methods, a data-carrying
  enum (`ConservationStatus(description: String)`), an ADT case-enum
  (`Activity`) matched via `select`/`is`, records with methods (`Park`,
  `Visit`, including nullable-safe derived fields), an interface implemented
  with `conforms` (`Reportable`), a mutable class (`ParkRegistry`) built on
  `List[Object]` and `<<`, a broad collection pipeline (`filter`, `map`,
  `fold`, `sortedBy`, `groupBy`, `find`, `partition`, `distinct`),
  `foreach (k, v)` over a `groupBy` result, `foreach` over an inclusive
  range, closures, string interpolation, `try`/`catch`, and recursion.
- **`run/RestaurantOrders.on`, a 327-line restaurant order management sample.**
  Covers records with method bodies (`MenuItem`, `OrderLine`), a plain enum
  matched via `select this` (`ItemCategory`, `OrderStatus`), an ADT
  case-enum (`Payment { case Cash, case Card, case Voucher }`), an
  interface implemented with `conforms` (`Billable`), `Double`/`String`
  extension methods, `groupBy` + `foreach (k, v)`, a broad collection
  pipeline (`filter`, `flatMap`, `fold`, `sortedBy`, `distinct`, `find`,
  `partition`, `take`, `zip`), ADT `select` type patterns, a nullable
  (`Payment?`) field, string interpolation, `try`/`catch`, and `foreach`
  over an inclusive range.
- **`run/NutritionTracker.on`, a 296-line daily nutrition log sample.**
  Covers a homogeneous enum (`MacroKind`) matched via `select`, an ADT
  case-enum (`MealType`) with `label()`/`isMainMeal()` methods, records
  with method bodies (`FoodItem`, `Serving`, `DailyGoal`), a mutable
  `Meal`/`DailyLog` class pair over `List`, `Double` extension methods for
  formatting, collection pipelines (`fold`, `filter`, `sortedBy`), a
  nullable-returning tip generator with the `?:` operator, and `string
  interpolation` across a multi-section macro/calorie report.
- **`run/MatrixCalc.on`, a 434-line integer-matrix calculator sample.**
  Covers `Int[][]` 2D arrays as class fields, public `val` fields alongside
  private data (section-based access), extension methods on `Int`
  (`abs`/`max`/`padLeft`) and `String` (`padRight`/`center`), static
  factory methods (`Matrix::zeros`/`identity`/`of`), recursive determinant
  via cofactor expansion (1×1 through 4×4+), `List[Int]` + `(x as Int)`
  unboxing for row/column sums, `foreach x: Object in list` with an
  explicit cast, `select` on an `Int`, and nested `while` loops.
- **`run/SnippetLibrary.on`, a 664-line code snippet library manager
  sample.** Covers an ADT data-carrying enum (`Language`, 7 cases) with
  `displayName()`/`fileExt()`/`isCompiled()` dispatched via `select`
  type patterns, plain enums (`Category`, `Difficulty` with
  `stars()`/`rank()`, `SortOrder`), records with method bodies
  (`Snippet`, `Tag`) including `upvotedCopy()`/`viewedCopy()`/`hasTag()`,
  an interface (`Searchable`), a typed-generic class over
  `ArrayList[Snippet]`, a broad collection pipeline (`map`, `filter`,
  `fold`, `sortedBy`, `groupBy`, `distinct`, `find`, `partition`, `zip`,
  `any`, `count`), a nullable (`Snippet?`) lookup with a null guard,
  `String`/`Int` extension methods, the `|>` pipeline operator, and
  `try`/`catch`.
- **`run/SortAlgorithms.on`, a 377-line sorting-algorithm benchmark.**
  Covers a plain enum with `select` exhaustiveness (`Complexity`, with
  `bigO()`/`isEfficient()`), a data-carrying ADT enum (`SortFamily`, with
  `Comparison`/`Distribution` cases), a record with a method body
  (`SortStats::summary()`), an interface with polymorphic dispatch
  (`Sorter`, five implementing classes: bubble/insertion/selection/merge/
  quick sort), classes with mutable state tracking comparisons/swaps,
  1D arrays with manual allocation and copying, recursion (`mergeSort`/
  `merge`, `quickSort`/`partition`), a collection pipeline (`map`,
  `filter`, `sortedBy`, `fold`, `find`, `groupBy`), a nullable
  (`SortStats?`) winner lookup, closures stored in `val`s, `foreach` over
  a list and over a `Map` (k, v), `try`/`catch`, and string
  interpolation.

## [0.10.32] - 2026-08-13

### Documentation

- **Documented the lossless-shapes/lens API and combinators in the `## Shape`
  section of `docs/reference/stdlib.md` and `docs/ja/reference/stdlib.md`.**
  The section covered `parse`, `print`, `canPrint`, and the L1/L2 laws, but
  never mentioned `isLossless`, `parseLossless`, `printLossless`, `Lossless`,
  `Residue`, the combinators (`eachLine`, `lines`, `sepBy`, `xmap`,
  `orElse`), or `Shapes::config`/`Shapes::yaml` — leaving a real,
  actively-used feature (lossless config editing, see
  `docs/guide/shapes.md`) undiscoverable from the API reference a user would
  actually search. Added a drift-guard spec
  (`StdlibDocShapeModuleParitySpec`) so future additions to the `Shape`/
  `Lossless` public API get caught the same way.
- **Documented `Shapes::config` and `Shapes::yaml` in the "When to reach for
  `Shapes` directly" section of `docs/guide/shapes.md` and
  `docs/ja/guide/shapes.md`.** The section only mentioned `Shapes::regex` and
  `Shapes::json`, even though `onion.Shapes` also exposes `config` and `yaml`
  factories — the hand-written equivalents of `shape name = config` /
  `shape name = yaml` — making them undiscoverable without reading the Java
  source. Added a drift-guard spec (`ShapesGuideDirectApiParitySpec`) so
  future additions to `Shapes`'s public API get caught the same way.
- **Documented five undocumented `onion.Colls` batching/aggregation helpers
  (`chunked`, `windowed`, `sumBy`, `averageBy`/`maxBy`/`minBy`) in a new
  "Batching, windowing, and selector aggregation" subsection of the Colls
  Module section in both `docs/reference/stdlib.md` and
  `docs/ja/reference/stdlib.md`.** The "Modules at a glance" summary table
  already promised "chunked/windowed, sumBy/maxBy" for `Colls`, but the Colls
  Module section itself never mentioned them, and — unlike `map`/`filter`/
  `reduce`/`fold` — no other part of the doc covered them either, making them
  undiscoverable without reading the Java source. Added a drift-guard spec
  (`StdlibDocCollsBatchingAggregationParitySpec`) so future additions to
  `Colls`'s public API get caught the same way.
- **Documented nine undocumented `onion.Iterables` methods (`mapMap`,
  `toList`, `reduce`, `newList`, `first`, `last`, `reverse`, `take`, `drop`)
  in the Iterables Module section of both `docs/reference/stdlib.md` and
  `docs/ja/reference/stdlib.md`.** These public static methods existed in
  code but were absent from the docs in both languages, making them
  undiscoverable without reading the Java source. Added a drift-guard spec
  (`StdlibDocIterablesModuleParitySpec`) so future additions to
  `Iterables`'s public API get caught the same way.
- **Documented `Http::getResponse`/`Http::postResponse` and the `Response`
  object they return (`status`, `body`, `headers`, `isOk()`, `isError()`) in
  the Http section of both `docs/reference/stdlib.md` and
  `docs/ja/reference/stdlib.md`.** These public `onion.Http` methods existed
  in code but were absent from the docs in both languages, making them
  undiscoverable without reading the Java source — the only documented way to
  make a request was the body-only `get`/`post`, with no way to see the
  status code or response headers. Added a drift-guard spec
  (`StdlibDocHttpModuleParitySpec`) so future additions to `Http`'s public
  API get caught the same way.
- **Documented ten undocumented `onion.Regex` `Pattern`-typed overloads
  (`matches`, `find`, `findAll`, `findFirst`, `groups`, `groupsAll`,
  `replace`, `replaceFirst`, `split`/`split` with limit) in a new "Pattern
  literal overloads" subsection of the Regex Module section in both
  `docs/reference/stdlib.md` and `docs/ja/reference/stdlib.md`.** These
  overloads exist specifically so a `re"..."` literal (which compiles to
  `java.util.regex.Pattern`, not `String`) can be passed straight into the
  `Regex::` helpers, but the docs only ever showed the `String`-pattern
  signatures, making the `Pattern` overloads undiscoverable without reading
  the Java source. Added a drift-guard spec
  (`StdlibDocRegexModuleParitySpec`) so future additions to `Regex`'s public
  API get caught the same way.
- **Documented eleven undocumented `onion.Files` methods
  (`isFile`, `isDirectory`, `mkdirs`, `listFiles`, `size`, `getAbsolutePath`,
  `copy`, `move`, `copyDir`, `writeLines`, `appendText`) in the Files Module
  section of both `docs/reference/stdlib.md` and `docs/ja/reference/stdlib.md`.**
  These public static methods existed in code but were absent from the docs in
  both languages, making them undiscoverable without reading the Java source.
  Added a drift-guard spec (`StdlibDocFilesModuleParitySpec`) so future
  additions to `Files`'s public API get caught the same way.
- **Fixed stale/actively-misleading heap guidance in `docs/quality-bar.md`,
  `docs/ja/quality-bar.md`, `docs/contributing/building.md`, and
  `docs/ja/contributing/building.md`.** `.jvmopts` raised the project's
  default heap from `4g` to `10g` back at 0.10.18, but these four docs still
  described (or, in `building.md`'s "Out of Memory" section, actively
  recommended) a heap at or below the old `4g`/`2g` figures — advice that,
  followed literally, overrides `.jvmopts` down to a lower ceiling and
  reproduces the `OutOfMemoryError` it claims to fix (#691). Updated all four
  to state the real `10g` default and to warn against setting `SBT_OPTS` to
  anything below it.
- **Documented six undocumented `onion.Rand` methods
  (`nextInt(min, max)`, `nextDouble(bound)`, `nextDouble(min, max)`, `choice`,
  `sample`, `uuid`) in the Rand Module section of both `docs/reference/stdlib.md`
  and `docs/ja/reference/stdlib.md`.** These public static methods existed in
  code and were already name-dropped in the "Modules at a glance" table, but
  had no actual signature or example in the Rand Module section itself, making
  them undiscoverable without reading the Java source. Added a drift-guard spec
  (`StdlibDocRandModuleParitySpec`) so future additions to `Rand`'s public API
  get caught the same way.

## [0.10.31] - 2026-08-12

### Documentation

- **Documented nine undocumented `onion.Strings` methods
  (`splitRegex`, `isEmpty`, `isBlank`, `substring`, `indexOf`, `lastIndexOf`,
  `lines`, `reverse`, `decapitalize`) in the Strings Module section of both
  `docs/reference/stdlib.md` and `docs/ja/reference/stdlib.md`.** These public
  static methods existed in code but were absent from the docs in both
  languages, making them undiscoverable without reading the Java source. Added
  a drift-guard spec (`StdlibDocStringsAccessorParitySpec`) so future
  additions to `Strings`'s public API get caught the same way.
- **Documented `Json::asObject()`/`Json::asArray()`/`Json::parseOrNull()` in the
  Json Module section of both `docs/reference/stdlib.md` and
  `docs/ja/reference/stdlib.md`.** These `onion.Json` public static methods
  existed in code but were absent from the docs in both languages, making them
  undiscoverable without reading the Java source. Added a drift-guard spec
  (`StdlibDocJsonAccessorParitySpec`) so future additions to `Json`'s public
  API get caught the same way.
- **Documented `Json::object()`/`Json::array()` and `Value.isNull()`/`Value.size()`/
  `Value.raw()` in the English Json Module reference (`docs/reference/stdlib.md`),
  and added the missing `Json::array()`/`Value.raw()` mentions to the Japanese
  reference (`docs/ja/reference/stdlib.md`).** These `onion.Json` builder helpers
  and `Value` accessors existed in code but were absent (or partially absent) from
  the docs in both languages, making them undiscoverable without reading the
  Java source.
- **Documented `Timing::elapsedMillis`, `Timing::formatMillis`,
  `Timing::measureVoid`, and `Timing::sleepNanos` in the Timing Module section
  of both `docs/reference/stdlib.md` and `docs/ja/reference/stdlib.md`.** These
  `onion.Timing` public static methods existed in code but were absent from the
  docs in both languages, making them undiscoverable without reading the Java
  source. Added a drift-guard spec (`StdlibDocTimingModuleParitySpec`) so
  future additions to `Timing`'s public API get caught the same way.
- **Documented 21 undocumented `onion.IO` methods
  (`readLine`, `readAll`, `printf`, `format`, `eprint`, `eprintln`, `eprintf`,
  `readInt`, `readLong`, `readDouble`, `readBoolean`, `tryReadInt`,
  `tryReadDouble`, `tryReadLong`, `readLines`, `eachLine`, `printLines`,
  `printAll`, `flush`, `newline`, `clear`) in the IO Module section of both
  `docs/reference/stdlib.md` and `docs/ja/reference/stdlib.md`.** These public
  static methods existed in code but were absent from the docs in both
  languages, making them undiscoverable without reading the Java source. Added
  a drift-guard spec (`StdlibDocIOAccessorParitySpec`) so future additions to
  `IO`'s public API get caught the same way.

## [0.10.30] - 2026-08-11

### Documentation

- **Added the missing `E0032` (`TYPE_ARGUMENT_MUST_BE_REFERENCE`) walkthrough
  section to `docs/reference/error-codes.md` and `docs/ja/reference/error-codes.md`.**
  Every other code had a numbered `### EXXXX` section except this one, leaving
  a visible gap between `E0031` and `E0033`. The new section explains that the
  check has live report sites but is not reachable from valid Onion source
  today, since the grammar never routes a type-argument position through
  `void_type()` — matching the reasoning already recorded in
  `SemanticErrorCodeCoverageSpec`.

### Fixed

- **`[I0000]` internal compiler crash when a `try`/`catch` expression is used
  as a non-first call or constructor argument (issue #669).** The JVM clears
  the operand stack when it dispatches to an exception handler, so any
  argument already pushed before an inline `try { ... } catch { ... }`
  argument was silently discarded, producing invalid bytecode (mismatched
  stackmap frames) instead of a working method or a normal diagnostic.
  `AsmCodeGenerationVisitor` now spills already-pushed operands (including a
  call receiver) into locals before evaluating an argument that may run its
  own `try`, and reloads them afterward, preserving left-to-right evaluation
  order.

### Added

- **`run/ConferenceSchedule.on`, a 451-line conference schedule management sample.**
  Exercises a data-carrying enum (`Verdict` with `Approved`/`Rejected`/`Pending`,
  dispatched via `select case is`), a plain enum (`Track`), an interface
  (`Summarizable`) implemented by two records, records with methods and `conforms`
  (`Speaker`, `Session`, `Proposal`), a generic `Box[T]` class with a `map[U]`
  method, extension methods on `Int` (`hourStr`) and `String` (`padRight`,
  `truncate`), collection pipelines (`filter`/`map`/`fold`/`sortedBy`/`groupBy`/
  `partition`/`zip`/`flatten`/`distinct`/`any`/`count`), `foreach (k, v)` on
  `Map`, nullable types with null guards, a tail-recursive prefix search, a
  `while` loop, `try`/`catch`, and `string interpolation` throughout.
- **`run/PharmacySystem.on`, a 320-line pharmacy/prescription management
  sample.** Covers ADT enums (`DrugCategory`, `PrescriptionStatus`) with
  exhaustive `select this { case x is Y: ... }` dispatch, records (`Drug`,
  `Patient`, `Prescription`, `FilledRx`), typed generics (`List[T]`,
  `Map[String, List[Drug]]`), collection pipelines (`filter`/`map`/`fold`/
  `groupBy`/`sortedBy`), `foreach (k, v)` map destructuring, nullable-aware
  lookup helpers, and `string interpolation` across a ten-section report.
- **`run/PropertyManager.on`, a 351-line rental property portfolio management
  sample.** Covers an ADT enum (`MaintenancePriority`) with `label()`/`score()`
  methods, homogeneous enums (`PropertyType`, `LeaseStatus`), records with
  inline bodies (`Address`, `Property`, `Tenant`, `Lease`, `MaintenanceRequest`),
  extension methods on `Int`/`Double`/`String`, collection pipelines
  (`filter`/`map`/`fold`/`sortedBy`/`groupBy`/`find`/`partition`),
  `foreach i: Int in 1..12`, `foreach (k, v)` map destructuring, and
  `String::format`-based currency/percentage formatting across a ten-section
  report.

## [0.10.29] - 2026-08-10

### Added

- **`run/HuffmanCoding.on`, a 318-line Huffman lossless compression demo.**
  Builds a Huffman tree as an ADT case-enum (`HLeaf`/`HNode`) with recursive
  `depth`/`leafCount`/`isLeaf` methods, generates prefix codes via DFS, and
  encodes/decodes 5 corpus texts with verified exact round-trip. Covers
  records with an inline `example`, extension methods (`Int.rjust`,
  `Int.percent`, `Double.fmt2`, `String.times`, `String.padLeft/Right`),
  collection pipelines (`filter`, `fold`, `sortedBy`, `groupBy`, `indexOf`),
  a mutable `Map` frequency/code table, nullable downcasts, and nested
  `select` dispatch during decode.
- **`run/CipherSuite.on`, a 420-line classical cryptography toolkit sample.**
  Covers five ciphers (Caesar, ROT-13, Atbash, Vigenère, rail-fence
  transposition) with round-trip verification, a data-carrying enum
  (`CipherKind`), frequency analysis with `groupBy`/`sortedBy`, and
  `String`/`Int` extension methods for report formatting.
- **`run/SpellCheck.on`, a 312-line Levenshtein edit-distance spell checker
  sample.** Covers dynamically-sized `Int[]` arrays, an iterative two-row
  edit-distance DP, an interface/class pair (`Dictionary`/`SimpleDictionary`),
  records with method bodies, `select` on `Int` distance bands, and
  collection pipelines (`filter`, `map`, `sortedBy`, `groupBy`, `take`,
  `find`, `join`, `fold`).
- **`run/SudokuSolver.on`, a 317-line Sudoku solver.** Exercises a plain enum
  (`Difficulty`), a result record (`SolveResult`), extension methods on `Int`
  (`toCell`, `isGiven`), a `SudokuBoard` class with a public accessor method
  (`btCount`), backtracking recursion, 2D list mutation (`List[Object]` as a
  9×9 grid), a `hintSummary` method using closures, collection pipelines
  (`filter`/`fold`/`sortedBy`/`map`), `select`-based difficulty-bar rendering,
  and `Timing::nanos` for elapsed-time measurement. Solves Easy (30 clues,
  ~4 000 backtracks), Medium (~36 clues, 9 backtracks), and Hard/AI-Escargot
  (23 clues, ~9 000 backtracks) puzzles with verified correct solutions.
- **`run/HRSystem.on`, a 374-line human-resources management system sample.**
  Covers a data-carrying ADT enum (`EmploymentType` with `FullTime`/`PartTime`/
  `Contractor` cases), a plain enum (`Rating`) with `stars`/`order`/
  `raisePercent` methods, records with `example` clauses (`Employee`,
  `PayRaise`, `DeptStats`), extension methods on `Int`/`Double`/`String`,
  an `HRDatabase` class with nullable lookups (`findById(): Employee?`),
  collection pipelines (`filter`, `sortedBy`, `fold`, `find`, `take`, `map`,
  `any`), `foreach (k, v) in map` iteration, and exhaustive `select` dispatch
  over the ADT enum. Generates six reports: employee directory, department
  statistics, workforce summary, top earners, proposed raises, and a tenure
  distribution bar chart.

### Fixed

- **A record with a duplicate component name (`record R(a: Int, a: Int)`) no
  longer crashes the compiler.** Two components sharing a name generate two
  same-named accessor methods and fields, which previously went completely
  unchecked and only surfaced later as an internal compiler error (I0000,
  a JVM `ClassFormatError`) once the class was loaded. It is now reported as
  a normal `E0086` semantic error at the offending component's location.
  Found by the mutation fuzzer (issue #666).

## [0.10.28] - 2026-08-09

### Added

- **`run/MazeSolver.on`, a 295-line maze generator + BFS solver sample.**
  Generates a perfect maze via recursive-backtracking DFS with a Fisher-Yates
  shuffle, then finds the shortest path with an iterative BFS using
  array-based visited/parent trackers. Exercises an ADT enum (`Cell`) matched
  with `select`/`is`, a `Point` record, classes with `public:`/`private:`
  sections (`Maze`, `BfsSolver`, `MazeStats`), extension methods on `String`,
  a flat row-major `List[Cell]` grid, `Boolean[]`/`Int[]` primitive arrays,
  recursion, `while`/`foreach` loops, and string interpolation.
- **`run/TextAnalytics.on`, a 272-line multi-document text-analysis sample.**
  Exercises records (`Document`, `WordFreq`, `SentenceInfo`, `AnalysisResult`),
  a data-carrying enum (`ReadabilityLevel`) matched via `select this`, a class
  with a constructor argument (`TextAnalyzer(topN)`), `Map[String, Int]`
  frequency tables built with `Maps::countBy`/`Maps::toList`, nullable `Int?`
  lookups, closures, `foreach` over lists, string interpolation, and
  `Format::fixed` percentage/float formatting. Reports word-frequency
  profiles, sentence-length breakdown, readability classification, pairwise
  Jaccard similarity, and a complexity ranking across three sample documents.
- **`run/SpaceMission.on`, a 324-line space mission planner sample.** Exercises
  records with body methods (`Planet`, `Spacecraft.wetMass`/`massRatio`,
  `MissionLog`), a plain enum (`MissionStatus`), a data-carrying enum
  (`MissionOutcome`), an ADT case-enum (`MissionEvent` with 4 cases), a class
  with mutable state (`MissionController`), collection pipelines (`filter`,
  `map`, `fold`, `groupBy`, `sortedBy`, `find`), exhaustive `select` over the
  ADT cases, nullable lookups (`findPlanet(): Planet?`), closures, string
  interpolation, recursion (Newton's method for orbital period), `foreach`
  over a range, `while`, `try`/`catch`, and `foreach (k, v) in map`.
- **`run/KingdomSim.on`, a 550-line medieval kingdom simulation sample.**
  Exercises operator overloading (`plus`/`times`/`minus` on a `Resource`
  record), `do[List]` comprehension for trade-pair generation, multiple
  interface conformance, `foreach (k, v)` map iteration, extension methods
  on `Int`/`Double`/`String`, the `|>` pipeline operator, an ADT case-enum
  (`KingdomEvent`) with 5 data-carrying cases, `example`/`law` clauses on a
  record, `try`/`catch`, nullable returns, exhaustive `select`, and a broad
  set of collection pipelines (`groupBy`, `sortedBy`, `fold`, `partition`,
  `zip`, `distinct`, `find`, `any`, `take`).

## [0.10.27] - 2026-08-09

### Added

- **`run/Sudoku.on`, a 261-line Sudoku solver with backtracking search.** Exercises
  a flat `List[Int]` as a 2D 9×9 grid (row-major `row*9+col` indexing), records
  with methods (`Puzzle.label`, `SolveResult.summary`), a class with a mutable
  field and a public getter (`Solver.numSteps`/`steps()`), extension methods on
  `Int` (`cellChar`, `boxStart`), nested `foreach` over ranges (`0..<9`, `1..9`),
  collection pipelines (`count`/`filter`/`sortedBy`), recursion (backtracking
  solver), and explicit `return` statements in block-body methods. Solves an easy
  puzzle (30 givens, 4208 steps), a medium (36 givens, 54 steps), and a hard (23
  givens, 879417 steps) with full row/column/box validation confirming each
  solution. `SampleProgramsSpec` passes (120/120) including `Sudoku.on`.
- **`run/DoctorScheduler.on`, a 336-line medical appointment scheduling sample.**
  Exercises plain enums with `select`/`when`-guard dispatch, records with nullable
  fields, a class with public-section fields, `foreach (k, v) in map` map-entry
  destructuring, string interpolation, and collection grouping.
- **`run/TimesheetTracker.on`, a 329-line employee timesheet management sample.**
  Exercises a homogeneous enum (`Category`) with `select`-based methods, records
  with body methods and `example` clauses, a class backed by `Map[String, Employee]`
  and `Map[String, Double]` registries, nullable `Double?` lookups, collection
  pipelines (`filter`/`fold`/`sortedBy`), `foreach (k, v) in map`, extension
  methods on `String` (`padRight`/`padLeft`), closures, and overtime-pay logic.
- **`run/LibrarySystem.on` expanded from a 374-line stub to a 529-line library
  management system.** Exercises extension methods on `String`/`Int`/`Double`,
  homogeneous enums with `public:` methods (`Genre`, `MemberStatus`,
  `LoanStatus`), records with inline `public:` methods, interface conformance
  (`Reportable`), classes with mutable typed-list fields, in-place mutation via
  `indexOf`/`List.set`, collection pipelines (`filter`/`map`/`fold`/`groupBy`/
  `sortedBy`/`partition`/`zip`/`any`/`count`), `foreach (k, v) in map`, nullable
  types with null guards, closures stored in `val`s, and a tail-recursive helper.

## [0.10.26] - 2026-08-08

### Added

- **`run/SortingShowcase.on`, a 402-line sorting-algorithms showcase.** Implements
  bubble, insertion, selection, merge (recursive), quick, and counting sort behind
  a `SortKind` enum dispatched via `select`, with `TestCase`/`SortResult` records,
  extension methods on `Int`, in-place `List[Int]` mutation, collection pipelines,
  recursive algorithms, and `try`/`catch`.
- **`run/BookClub.on`, a 383-line book-club management sample.** Exercises a
  homogeneous enum (`Genre`) with public methods, an ADT case-enum
  (`ReadStatus`: `Unread`/`InProgress`/`Finished`/`Abandoned`), records
  (`Book`, `MemberBook`, `Meeting`), classes backed by `List` state (`Member`,
  `BookClub`), extension methods on `Int`, collection pipelines
  (`map`/`filter`/`fold`/`groupBy`/`sortedBy`/`find`/`partition`/`reduce`),
  `select`/type-pattern dispatch over `ReadStatus`, `foreach` over an
  inclusive range and over `Map` entries, nullable return types, recursion,
  and `try`/`catch`.
- **`run/GameOfLife.on`, a 422-line Conway's Game of Life simulation.** Exercises
  a homogeneous enum (`CellState`) and a data-carrying enum (`PatternKind`) with
  a `select`-based label method, records with methods (`Cell.distanceTo`/`label`,
  `Generation.summary`/`isStable`/`isEmpty`/`delta`), extension methods on `Int`
  (`wrapIn`, `sign`) and `String` (`padLeft`, `padRight`), a sparse
  `Map[String,Boolean]` grid with toroidal wrapping, `foreach (k, v) in map`,
  collection pipelines (`filter`/`sortedBy`/`groupBy`/`map`/`distinct`), and
  closures stored in `val`s. Verified oscillators: Glider (period-4), Blinker
  (period-2), Pulsar (period-3), and a 50-generation R-Pentomino run.

- **`run/RecipeBook.on`, a 620-line recipe management sample.** Exercises a
  data-carrying enum (`Measurement` with `Grams`/`Milliliters`/`Pieces`/
  `Tablespoons`/`Teaspoons`/`Cups`, dispatched via `select`/`case`
  destructuring), plain enums (`Cuisine`, `MealType`, `Difficulty`), records
  with methods and `static def of(...)` factories (`Ingredient`,
  `NutritionInfo`, `Recipe`), extension methods on `Double`/`Int`/`String`,
  collection pipelines (`filter`/`map`/`fold`/`sortedBy`/`distinct`/`find`),
  nullable types with safe calls and null coalescing, and a mutable
  `RecipeBook` class with query and report-generation methods.

- **`run/PerfReview.on`, a 284-line employee performance review dashboard.**
  Exercises ADT case-enum (`PerformanceBand` with `Exceptional`/`Strong`/`Meets`/
  `BelowTarget`/`Critical` and `select this` dispatch), a homogeneous enum
  (`ReviewStatus` with `::` constant access), records with compile-time `example`
  clauses (`ReviewMetrics`), extension methods on records, collection pipelines
  (`filter`/`map`/`fold`/`groupBy`/`sortedBy`/`partition`/`zip`/`distinct`),
  nullable map lookups, `foreach (k, v) in map` destructuring, and `foreach :
  Type in list` typed iteration.

- **`run/GradeReport.on`, a 255-line student gradebook sample.** Exercises an
  ADT enum (`Standing`) dispatched via `select this { case x is T: }`, a
  data-carrying enum (`LetterGrade`), records with `List` fields (`Student`,
  `Enrollment`, `Course`), an interface implemented by a class
  (`Reportable`/`StudentReport`), extension methods on `Double`/`Int`,
  collection pipelines (`map`/`filter`/`fold`/`reduce`/`sortedBy`/`groupBy`),
  recursive and `while`-based iteration, `foreach` over an inclusive range and
  over `Map` entries, and `try`/`catch`.
- **`run/SocialNetwork.on`, a 568-line social network simulator.** Exercises ADT
  enums (`PostContent`, `Notification`) with exhaustive `select`/type-pattern
  dispatch, a homogeneous enum (`ReactionKind`), records (`User`, `Post`,
  `Comment`, `Reaction`), a `Network` class backed by `HashMap` stores,
  collection pipelines (`sortedBy`/`groupBy`/`fold`/`foreach (k, v)`), a
  BFS-style friend-of-friend recommendation engine, and nullable lookups
  (`User?`/`Post?`).
- **`run/PetShelter.on`, a 269-line pet shelter management sample.** Exercises a
  data-carrying enum (`PetType`), an ADT case-enum (`AdoptionStatus` with
  `Available`/`Foster`/`Adopted`/`Quarantine` cases and shared methods via
  `select this`), records (`Pet`, `Adopter`, `FosterRecord`), a class with
  public/private sections and a replace-on-update mutation pattern, extension
  methods on `String`/`Int`, collection pipelines (`filter`/`sortedBy`/
  `groupBy`/`find`/`partition`), `foreach (k, v) in map`, and `try`/`catch`
  for intake validation.
- **`run/GameOfLife.on`, a 422-line Conway's Game of Life simulation.**
  Exercises a homogeneous enum (`CellState`), an enum with a `select`-based
  method (`PatternKind.label()`), records with methods (`Cell.distanceTo()`,
  `Generation.summary/isStable/isEmpty`), extension methods on `Int`
  (`wrapIn`/`sign`) and `String` (`padLeft`/`padRight`), a `Map`-backed
  sparse grid with toroidal wrapping, `foreach (k, v) in map`, nullable map
  lookups, collection pipelines (`filter`/`sortedBy`/`groupBy`/`map`/
  `distinct`/`forEach`), and closures stored in `val`s. Verified against
  canonical Game of Life periods (glider, blinker, pulsar, R-pentomino).

### Fixed

- **`sbt test` could hit a real JVM `OutOfMemoryError`** (in `SampleProgramsSpec`
  and `MutationFuzzSpec`) as the `run/` corpus grew past 121 samples, wedging
  CI on `develop` instead of failing cleanly — the same failure mode fixed at
  0.10.17 (78 samples, `-Xmx6G`) recurring as the corpus kept growing. Raised
  the heap headroom again, both locally (`.jvmopts`: 4g → 10g) and in CI
  (`SBT_OPTS`: `-Xmx6G` → `-Xmx10G`); a clean run now completes in ~4 minutes
  with room to spare. `docs/quality-bar.md` / `docs/ja/quality-bar.md` rows 2–3
  (sample count 119→121, large-program count 62→64, adding `EspressoShop` and
  `MusicFestival`) updated to match, fixing the two `QualityBarSpec` failures
  those additions had left behind.

## [0.10.25] - 2026-08-07

### Added

- **`run/WeatherReport.on`, a 261-line weather data analysis sample.** A new
  domain not previously covered by the corpus. Exercises a data-carrying enum
  (`AlertLevel`: `NONE`/`ADVISORY`/`WARNING`/`EMERGENCY`), records with inline
  methods (`DailyReading`, `WeekSummary`), an interface implemented by a class
  (`Reportable`/`MonthData`), extension methods on `Double` and `String`,
  `select` dispatch over enum cases, a recursive moving-average function, and
  collection-pipeline operations underused elsewhere in the corpus
  (`reduce`/`partition`/`take`/`drop`/`zip`/`flatten`/`distinct`/`sortedBy`).
- **`run/CourseRegistration.on`, a 365-line university course registration sample.**
  Exercises plain enums (`Department`, `Grade`) with `select` dispatch, records with
  inline methods (`Course`, `Student`, `Enrollment`, `RankedStudent`), a class with
  private `Map[String, T]` fields, `foreach (k, v) in map` iteration, nullable
  (`Grade?`) fields with null-checks, an extension method on a record
  (`Student::shortLabel`), and an in-place insertion sort over `List[RankedStudent]`.
- **`run/RankedChoice.on`, a 312-line instant-runoff voting simulation.** Runs a
  5-candidate/30-ballot election (4 elimination rounds) and a 3-candidate
  "plurality != RCV" demo. Exercises records with inline methods (`Candidate`,
  `ElectionSummary`), `foreach (k, v) in map` and `foreach v in map.values()`,
  `break` and non-local `return` inside `foreach`, collection pipelines
  (`filter`/`sortedBy`/`find`), `String::format` for column-aligned output, and
  an `extension String` method.
- **`run/StudentGradeBook.on`, a 248-line student grade management sample.**
  Exercises records (`Course`, `Student`, `Enrollment`), a data-carrying enum
  (`Semester(label)`), collection pipelines (`filter`/`map`/`sortedBy`/
  `sortedByDescending`/`groupBy`/`fold`/`find`), `select` dispatch on `String`,
  closures capturing outer `val`s, recursion (`halveDepth`), `foreach` over
  list/map-entry/range, `while`, `try`/`catch`, and the two-step
  `(val as Int) as Double` numeric cast pattern.
- **`run/CensusAnalyzer.on`, a 468-line population data parsing and reporting
  sample.** Combines features under-represented elsewhere in the corpus:
  `from re"..."` pattern-attached record parsing (`Person`), `do[List]`
  list-comprehension cross-tabulation, the `|>` pipeline operator, an ADT
  `case`-enum (`AgeGroup`) alongside a homogeneous enum with methods
  (`IncomeBracket`), a wrapper record with computed properties (`PersonExt`),
  extension methods on `Int`/`Double`, `foreach (k, v)` over `Map`, and
  `record example {}` build-time parse round-trip assertions.
  111 total corpus programs.
- **`run/ClinicRecords.on`, a 523-line medical clinic patient and appointment
  management sample.** Exercises homogeneous enums with `select` dispatch
  (`BloodType`, `Severity`, `ApptStatus`), an ADT `case`-enum (`DiagnosisResult`:
  `Clear`/`Condition`/`Referral`) matched with type patterns, records with method
  bodies including a nullable field (`Appointment.notes: String?`), a class with
  typed `List[T]` fields, `sortedBy`/`groupBy`/`fold` pipelines, and extension
  methods on `String`/`Int`/`Double`.
  112 total corpus programs.

### Documentation

- **`docs/ja/reference/stdlib.md`'s Json Module section never mentioned `Json::value`,
  the navigable JSON wrapper (`Value`, indexable with `[]` and read with
  `asString`/`asInt`/`asLong`/`asDouble`/`asBoolean`/`isNull`/`size`) that
  `docs/reference/stdlib.md` documents.** Added the missing subsection and example to
  the Japanese page, and added `StdlibDocJsonModuleParitySpec` (same approach as
  `StdlibDocYamlModuleParitySpec`) so this can't silently drift again.

- **`docs/tools/compiler.md` and `docs/tools/script-runner.md` (and their `docs/ja`
  translations) had no section for the real `-super <super class>` and `--verbose`
  flags, and the `compiler.md` pair was additionally missing `--effects`** — all three
  work today but were absent from every option reference. Added the missing sections
  to all four pages and added `CliOptionDocCoverageSpec`, which ties each page to the
  actual `--help` option list in `CompilerFrontend`/`ScriptRunner` so a future flag
  added to one and not the other fails the build instead of drifting silently.

## [0.10.24] - 2026-08-06

### Documentation

- **`docs/ja/reference/stdlib.md`'s Yaml Module section collapsed the English section's
  four subsections (`Yaml::parse`, `Yaml::stringify`, the round-trip guarantee, and
  `derive!(Yaml)` usage) into a single short paragraph, dropping the round-trip
  guarantee statement and the `derive!(Yaml)` code examples (`ServerConfig`,
  `derive!(Json, Yaml)`) entirely.** Restored the missing subsections and examples, and
  added `StdlibDocYamlModuleParitySpec` (same subheading-count approach as
  `StdlibDocFutureModuleParitySpec`, plus a direct check for the `derive!(Yaml)` example)
  so this can't silently drift again.

- **`docs/ja/reference/stdlib.md`'s Maps Module and Sets Module sections were missing
  several members that `docs/reference/stdlib.md` documents** — Maps: `newMap`,
  `getOrDefault`, `filterKeys`, `filterValues`, `toList`, `forEach`, `merge`; Sets:
  `newSet`, `containsAll`, `forEach`. Restored the missing entries and added
  `StdlibDocMapsSetsModuleParitySpec` (same approach as `StdlibDocStringsModuleParitySpec`)
  so this can't silently drift again.

- **`docs/ja/reference/stdlib.md`'s Strings Module section was missing 13 of the 29
  documented `Strings::*` members** (`split`, `join`, `upper`, `lower`, `trim`, `replace`,
  `replaceRegex`, `startsWith`, `endsWith`, `contains`, `padLeft`, `padRight`, `repeat`) —
  the first code block was dropped during translation, leaving only the case/inspection
  and null-safe-parsing examples. Restored it in the same grouping as the English
  reference, and added `StdlibDocStringsModuleParitySpec` to guard against this drifting
  again (the existing subheading-based parity specs don't cover this section, since it
  has no `###` subheadings to count).

- **`docs/reference/error-codes.md` and `docs/ja/reference/error-codes.md` listed `E0058`
  (label not found), `E0059` (invalid regex literal), `E0060` (regex capture group /
  binding count mismatch), `E0061` (record component type unsupported by `from
  re"..."`), `E0062` (record component type unsupported by `derive!(Json)`), `E0063`
  (unknown `derive!` marker), `E0064` (law violation), `E0065` (example failed),
  `E0068` (override target not found), `E0069` (local val requires an initializer),
  `E0070` (nullable member access), `E0071` (static call on instance), and `E0072`
  (abstract method with a body) only in the end-of-file summary table.** Added the
  missing `### E0058`–`### E0072` sections (13 codes total) to both language
  references, filed under their most fitting existing category (control-flow,
  pattern-matching, resolution, null-safety, and declaration errors), using the
  existing dedicated regression specs (`LabelNotFoundSpec`, `RegexLiteralValidationSpec`,
  `RegexPatternSpec`, `RecordFromRegexSpec`, `RecordJsonSpec`, `LawExampleSpec`,
  `OverrideTargetNotFoundSpec`, `UninitializedValSpec`, `NullableMemberAccessSpec`,
  `StaticCallOnInstanceSpec`, `AbstractMethodBodySpec`) as the basis for the examples.
  This closes the remaining gap in the error-code reference — every code in the
  summary table now has a full prose section, except `E0032` (intentionally
  skipped — no in-process trigger under the current grammar, see the note in
  `SemanticErrorCodeCoverageSpec`).

- **`docs/reference/error-codes.md` and `docs/ja/reference/error-codes.md` listed `E0043`
  (unknown named argument), `E0044` (duplicate argument), `E0045` (positional argument
  after named argument), `E0046` (wrong destructuring binding count), `E0047` (not a
  record type), `E0048` (break outside loop), `E0049` (continue outside loop), `E0050`
  (current instance not available in static context), `E0051` (return type is
  required), `E0052` (lambda parameter needs a type), `E0053` (cyclic type alias),
  `E0054` (duplicate type alias), and `E0055` (function requires a body) only in the
  end-of-file summary table.** Added the missing `### E0043`–`### E0055` sections to
  both language references (a new "Control-flow errors" category covers `E0048`–`E0050`),
  and added regression coverage asserting the error *code* (not just compile failure)
  for `E0046`, `E0047`, and `E0052`, which previously had no such assertion.

- **`docs/reference/error-codes.md` and `docs/ja/reference/error-codes.md` listed `E0036`
  (cannot assign to val), `E0037` (unimplemented abstract method), `E0038` (cannot
  instantiate abstract class), `E0039` (cannot override a final method), `E0040` (cannot
  call method on primitive type), and `E0041` (invalid method call target) only in the
  end-of-file summary table.** Added the missing `### E0036`–`### E0041` sections to both
  language references. `E0041` (`INVALID_METHOD_CALL_TARGET`) had no regression test at
  all; added `InvalidMethodCallTargetSpec` covering the "indexing a nullable class-typed
  value" trigger site.
- **`docs/reference/error-codes.md` and `docs/ja/reference/error-codes.md` listed `E0030`
  (type is not generic), `E0031` (type argument arity mismatch), `E0033` (method is not
  generic), `E0034` (method type argument arity mismatch), and `E0035` (erased JVM
  signature collision) only in the end-of-file summary table.** Added the missing
  `### E0030`–`### E0035` sections (skipping `E0032`, which has a live report site but
  no in-process trigger under the current grammar — see the note in
  `SemanticErrorCodeCoverageSpec`) to both language references, using the existing
  `SemanticErrorCodeCoverageSpec` cases as the basis for the examples, and added a new
  `E0030` coverage case (previously untested).
- **`docs/reference/error-codes.md` and `docs/ja/reference/error-codes.md` listed `E0025`
  (duplicate constructor), `E0026` (duplicate generated method), `E0027` (type not
  boxable), `E0028` (lvalue required), and `E0029` (duplicate type parameter) only in
  the end-of-file summary table.** Added the missing `### E0025`–`### E0029` sections
  to both language references, using the existing `SemanticErrorCodeCoverageSpec` and
  `RecordDuplicateLawExampleSpec` cases as the basis for the examples.

- **`docs/reference/error-codes.md` and `docs/ja/reference/error-codes.md` listed `E0004`
  (field not found) only in the end-of-file summary table, unlike its neighbors `E0003`
  and `E0005`, which both have a full prose section.** Added the missing `### E0004`
  section to both language references, using the existing `SemanticErrorCodeCoverageSpec`
  case as the basis for the example.
- **`docs/reference/error-codes.md` and `docs/ja/reference/error-codes.md` listed `E0011`
  (duplicate global variable), `E0012` (duplicate function), `E0013` (method not
  accessible), `E0014` (field not accessible), and `E0015` (class not accessible) only
  in the end-of-file summary table.** Added the missing `### E0011`–`### E0015` sections
  to both language references, using the existing `DuplicateGlobalVariableSpec`,
  `DuplicateFunctionSpec`, `StaticMethodAccessSpec`, `FieldWriteAccessSpec`, and
  `ClassAccessibilitySpec` cases as the basis for the examples.
- **`docs/reference/error-codes.md` and `docs/ja/reference/error-codes.md` listed `E0016`
  (cyclic inheritance), `E0018` (illegal inheritance), `E0019` (illegal method call),
  `E0020` (cannot return value), `E0022` (ambiguous constructor), and `E0023` (interface
  required) only in the end-of-file summary table.** Added the missing `### E0016`,
  `### E0018`–`### E0020`, and `### E0022`–`### E0023` sections to both language
  references, using the existing `CyclicInheritanceSpec` and
  `SemanticErrorCodeCoverageSpec` cases as the basis for the examples.

## [0.10.23] - 2026-08-05

### Fixed

- **A `static` method declared with no body (e.g. `static def main(args: String[]): void`
  followed by a stray blank instead of `{ ... }`) no longer crashes the compiler with an
  I0000 internal error.** The grammar accepts a bodyless method as an abstract/interface-style
  declaration, and `TypingOutlinePass.processMethodDeclaration` unconditionally OR'd in
  `M_ABSTRACT` whenever the body was absent — even for a method already carrying `M_STATIC`.
  The resulting class file declared a method with both `ACC_STATIC` and `ACC_ABSTRACT`, which
  the JVM rejects; loading it (e.g. via `OnionClassLoader`, exercised by `LawCheckPhase`) threw
  a raw `ClassFormatError` that surfaced as an uncaught internal compiler error instead of a
  normal diagnostic. A static method with no body is now rejected up front with a new error,
  **E0085**. Found by the mutation fuzzer stripping the opening brace from `main`'s body in
  `run/LineFilter.on`.

### Documentation

- **`docs/reference/error-codes.md` and `docs/ja/reference/error-codes.md` listed `E0084`
  (duplicate extension method) and `E0085` (static method with no body) only in the
  end-of-file summary table, unlike every other code, which also gets a prose section
  with an example and a fix.** Added the missing `### E0084` and `### E0085` sections to
  both language references.
- **`E0006` (ambiguous method) and `E0007`–`E0010` (duplicate local variable/class/
  field/method) had the same gap** — mentioned only in the summary table, with no
  prose section, example, or fix. Added the missing sections to both language
  references, using the existing `SemanticErrorCodeCoverageSpec` cases as the basis
  for each example.

## [0.10.22] - 2026-08-04

### Fixed

- **The English `error.count` trailer no longer says "1 errors are found."
  for a single-error compile.** `error.count` in `errorMessage.properties`
  used a plain `"{0} errors are found."` template regardless of count; it now
  uses a `MessageFormat` choice format (`1#1 error is found.|1<{0} errors are
  found.`) so a single error reads "1 error is found." while multiple errors
  keep the plural wording. The Japanese bundle was unaffected (Japanese does
  not pluralize).
- **An ADT case-enum's shared-body `override def toString`/`equals`/`hashCode`
  is no longer silently ignored on every case.** `enum Shape { case Circle(...)
  public: override def toString(): String = ... }` desugars the shared body to
  a sealed-interface default method and each `case` to a bodyless
  `record X(...) conforms Shape`; `TypingOutlinePass` generated each case's
  synthetic toString/equals/hashCode whenever the record's OWN body had no
  override, with no way to see the interface's default, so the shared
  override compiled cleanly but was never actually called (a class's own
  method — even a mechanical, auto-generated one — always wins over an
  inherited interface default for these three names). A record whose
  conforms-to interface already supplies a concrete default now gets a real
  forwarding method (`INVOKESPECIAL` back to the interface) instead of the
  field-based synthetic.

## [0.10.21] - 2026-08-03

### Fixed

- **A constructor argument needing ordinary numeric widening (an `Int` literal
  passed where a `Double`/`Float`/`Long` parameter is declared, e.g. `new
  Game(99)` against `record Game(price: Double)`) no longer crashes the
  compiler with an I0000 internal error in `BytecodeGeneration`.**
  `ConstructionTyping.findConstructorWithBoxing` only inserted an explicit
  conversion for the Byte/Short/Char constant-narrowing case (issue #374); a
  genuine widening match — already accepted as legal by
  `TypeRelations.isAssignableWithBoxing` — left the argument term unconverted,
  so codegen pushed a 1-slot `int` where the constructor's descriptor declared
  a 2-slot `double`/`long`, corrupting the JVM stack map frames
  (`NegativeArraySizeException` in ASM's `Frame.merge`, or a `VerifyError` at
  class-load time). Argument adaptation (boxing and numeric conversion alike)
  now happens once, in a shared `adaptToFormals` step, applied uniformly
  whether the constructor was matched by the primary finder or the boxing
  fallback. Found by `MutationFuzzSpec` mutating a decimal literal (`59.99` ->
  `99`) in `run/GameStore.on`.
- **A homogeneous enum with two identically-named methods no longer crashes
  the compiler with an I0000 internal error ("Duplicate method name").**
  `TypingDuplicationPass.run()` dispatched over `ClassDeclaration`,
  `InterfaceDeclaration`, and `RecordDeclaration` but silently dropped
  `EnumDeclaration`, so the duplicate slipped through to codegen and produced
  invalid bytecode instead of the normal `E0010`/`E0035` diagnostic.
- **Records with a user-defined `override def toString`/`equals`/`hashCode`/`copy` in
  their body no longer crash the compiler with an I0000 internal error
  ("Duplicate method name").** `TypingOutlinePass` now skips emitting the
  synthetic record method whenever the user has already defined one with the
  same name, so the user's override compiles through the normal path instead
  of colliding with the auto-generated one.

### Added

- **`run/JobScheduler.on`, a 261-line priority-based CI/build job scheduler.**
  Exercises an ADT enum with per-case data (`JobState`: `Pending`, running
  `Running(startedAtMs)`, `Succeeded(durationMs)`, `Failed(reason)`), records
  with `example`s, extension methods on `Int`/`String`, a class-based priority
  queue, `try`/`catch` around job validation (rejecting an out-of-range
  priority and a non-positive estimate), exhaustive `select` / type-pattern
  matching, and the full collection-pipeline set
  (`map`/`filter`/`fold`/`sortedBy`/`groupBy`/`partition`/`find`/`distinct`/
  `any`/`take`) alongside `foreach`, string interpolation, and the `|>`
  pipeline. 47th large sample, 104 total corpus programs.
- **`run/CarRentalFleet.on`, a 225-line car rental fleet & billing report.**
  Exercises an ADT enum (`VehicleCategory`), records with `example`s, extension
  methods on `Int` and `Double`, and the full collection-pipeline set
  (`map`/`filter`/`fold`/`groupBy`/`sortedBy`/`find`/`partition`/`distinct`)
  alongside `foreach` over ranges and `Map` entries, `select` with type
  patterns, string interpolation, closures, `try`/`catch`, and the `|>`
  pipeline. 46th large sample, 103 total corpus programs.
- **`run/ExpenseAuditor.on`, a 135-line expense-report auditing tool.** Exercises
  the `tool` capability boundary (`requires { read(src), write(out), console }`,
  with `--help`/`--plan`/`--contract` all deriving from the single declaration),
  an ADT enum (`Category`) with per-case behavior, a record derived from a regex
  pattern (`from re"..."`) documented with `example`s rather than a `law` (a
  free-text field can contain the format's own delimiter, so a full round-trip
  law isn't satisfiable — a design point worth having on record), an extension
  method on `Double`, and collection pipelines (`filter`/`map`/`groupBy`/`fold`/
  `sortedBy`/`partition`). 45th large sample, 102 total corpus programs.
- **Six new large end-to-end samples added to the `run/` corpus**:
  `InventoryReport.on` (249 lines), `AirlineReservation.on` (598 lines),
  `PokerHands.on` (443 lines), `VirtualMachine.on` (337 lines, a stack-based
  bytecode interpreter), `TournamentStandings.on` (249 lines), and
  `LogAnalytics.on` (298 lines, log analysis pipeline with `record ... from
  re"..."` parsing). Corpus now stands at 100 samples, 43 of them ≥ 100 lines.
- **`run/BugTracker.on`, a 398-line software issue tracker sample.** Exercises
  ADT enums (`Priority`, `Status`, `Resolution` — including data-carrying cases
  `Fixed(inVersion)`, `WontFix(reason)`, `Duplicate(ofId)`), data-carrying
  homogeneous enum (`IssueType`), a record with `example` (`Issue` with nullable
  `assignee`), extension methods on `Int` (story-size label, bar chart) and
  `Double` (percentage string), an interface (`Reportable`), a class
  (`IssueTracker`), collection pipelines
  (`filter`/`map`/`fold`/`sortedBy`/`groupBy`/`partition`/`distinct`/`find`/
  `zip`/`any`/`count`/`take`), `|>` pipeline operator, select / type-pattern
  matching with exhaustiveness, nullable null checks, string interpolation,
  `try/catch`, recursion, `while`, and `foreach` over ranges and `(k, v)` maps
  (37th large sample, 94 total corpus programs).
- **`run/CinemaBooking.on`, a 228-line cinema seat-booking system sample.**
  Exercises an ADT enum (`SeatType`: `Standard`/`Premium`/`VIP`) dispatched via
  `select`, records with `example` clauses (`Movie`, `Booking`, `MovieStat`),
  extension methods on `Int` and `Double`, collection pipelines
  (`filter`/`map`/`fold`/`groupBy`/`sortedBy`/`partition`/`distinct`), `foreach`
  over integer ranges and `(k, v)` map entries, string interpolation, a closure
  stored in a `val`, `try/catch`, and the `|>` pipeline operator (44th large
  sample, 101 total corpus programs).

## [0.10.20] - 2026-08-02

### Added

- **`run/MathParser.on`, a 413-line recursive-descent arithmetic expression
  parser and evaluator sample.** Exercises an ADT enum (`Expr` with 7
  cases: `Lit`/`Var`/`BinOp`/`Neg`/`Cmp`/`IfExpr`/`Let`) dispatched via
  `select this { case x is T: ... }` with E0042 exhaustiveness, a
  homogeneous enum (`TokKind`, 20 variants), records with body methods
  (`Tok`, `EvalResult` with nullable fields), a hand-written `Lexer` and
  recursive-descent `Parser` class, `HashMap[String, Object]` scope
  chaining for `let` binding, `if/then/else` as an expression, collection
  pipelines (`filter`/`map`/`fold`/`sortedBy`/`groupBy`), and `try/catch`
  for parse/runtime errors (32nd large sample, 89 total corpus programs).

- **`run/FleetManager.on`, a 364-line vehicle fleet management sample.**
  Exercises classes implementing an interface (`Vehicle`, `Car`, `Truck`),
  a data-carrying ADT enum (`VehicleStatus`), a plain enum with a method
  (`FuelType`), records with `example` (`TripEntry`, `MaintenanceEntry`,
  `FleetStats`), extension methods on `String`/`Double`/`Int`, collection
  pipelines (`filter`/`fold`/`groupBy`/`sortedBy`/`partition`/`distinct`),
  `foreach (k, v)` map iteration, `select` type-pattern dispatch, nullable
  `String?`, `try/catch`, and the `|>` pipeline operator (33rd large
  sample, 90 total corpus programs).

- **`run/InventoryManager.on`, a 329-line stock-tracking sample.** Exercises
  a data-carrying enum (`Category`, 4 variants), records (`Product`,
  `StockMovement`), an interface (`Reportable`) implemented by a class with
  public `List[Object]` fields, extension methods on `Double`/`Int`,
  collection pipelines (`filter`/`fold`/`sortedBy`/`groupBy`/`find`),
  `select` pattern matching on enum values and strings, nullable `Object?`
  with `null` guards, a recursive function, `while`/`foreach` loops
  (including an exclusive range `0..<5`), `try/catch` around
  `Integer::parseInt`, and string interpolation throughout (34th large
  sample, 91 total corpus programs).

## [0.10.19] - 2026-08-02

### Fixed

- **A `static def` in an ADT case-enum's `public:` section could not be
  called** — `Grade::fromScore(x)` raised `E0005: method ... is not found`
  even when the signature matched exactly, because
  `TypingOutlinePass.processInterfaceMethodDeclaration` hard-coded
  `M_PUBLIC` for every interface method declaration, silently dropping
  `M_STATIC`. ADT case-enums desugar to a sealed interface + one record per
  case, so a static factory method declared in the enum's `public:` section
  was registered without the static modifier, and
  `collectMethodsMatching(isStaticMethod)` could no longer find it.

### Added

- **`run/MusicLibrary.on`, a 274-line music library catalog sample.**
  Exercises a simple enum (`Genre`), records with a nullable field
  (`Entry(song, rating: Rating?)`), a generic class (`Catalog[T extends
  Object]`), extension methods on `Int`/`String`, collection pipelines
  (`fold`/`filter`/`map`/`groupBy`/`sortedBy`/`sortedByDescending`/
  `distinct`), `select`/enum pattern matching, string interpolation, and
  `foreach` over typed lists and maps (28th large sample, 85 total corpus
  programs).

- **`run/GameStore.on`, a 292-line game store inventory and sales
  reporting sample.** Exercises homogeneous enums (`Genre`, `Platform`),
  records with methods (`Game`, `Sale`), typed generics (`List[Game]`,
  `Map[String, Double]`), collection pipelines (`groupBy`/`sortedBy`/
  `fold`/`filter`/`find`/`indexOf`), nullable types with null-guards,
  string interpolation, extension methods on `Int`/`Double`/`String`,
  `foreach (k, v)` map iteration, closures, and a class with access
  sections and mutable state (29th large sample, 86 total corpus
  programs).

- **`run/EventTicketing.on`, a 279-line event ticketing system sample.**
  Exercises homogeneous enums with methods (`TicketTier`, `EventStatus`),
  an ADT enum (`BookingResult { case Ok(...); case Refused(...) }`) matched
  with `case r is Ok:` / `case r is Refused:`, records (`Venue`, `Event`,
  `Order`, `CustomerSpend`), typed collections (`List[Event]`,
  `List[Order]`, `Map[String, Double]`), collection pipelines
  (`filter`/`sortedBy`), `foreach (k, v)` map iteration, `foreach` over a
  range, nullable handling with null-guards, and string interpolation
  (30th large sample, 87 total corpus programs).

- **`run/ParkingGarage.on`, a 254-line multi-level parking garage
  management sample.** Exercises a homogeneous enum with methods
  (`VehicleType`), a plain enum (`SpotStatus`), two ADT enums (`ParkResult
  { case Parked(...); case Denied(...) }`, `ExitResult { case
  Charged(...); case Rejected(...) }`), records with methods (`Spot`,
  `Ticket`, `LevelUsage`), extension methods on `Int`/`Double`, a class
  with mutable `Map`/`List` state (`Garage`), collection pipelines
  (`filter`/`sortedBy`), `foreach (k, v)` map iteration, `foreach` over a
  range and a list literal, nullable handling (`Spot?`, `String?`),
  `try`/`catch`, and string interpolation (31st large sample, 88 total
  corpus programs).

## [0.10.18] - 2026-08-02

### Fixed

- **Two methods with the same name and parameter types in one `extension`
  block compiled clean and crashed later with an internal compiler error
  (`I0000`)** — a JVM `ClassFormatError` ("Duplicate method name ...") when
  the synthesized extension container class was loaded, since
  `TypingDuplicationPass` checked classes, interfaces, records, top-level
  functions, and global variables for duplicates but never visited
  `AST.ExtensionDeclaration`. Found by the mutation fuzzer duplicating a
  method in `run/TaskPlanner.on`'s `extension Double` block. Added a new
  `E0084` diagnostic (`DUPLICATE_EXTENSION_METHOD`) so this is now a normal
  compile error instead of a crash.

- **Tail-call optimization miscompiled recursive functions that declared
  body-local variables** (e.g. `val x = items[idx] as Item`), either
  crashing with `I0000 BytecodeGeneration` or silently producing wrong
  results (an accumulator parameter would read the same element every
  iteration). Three related bugs in `TailCallOptimization`: the loop-variable
  offset fallback for top-level/synthetic methods collided with body-local
  slots, the temp-variable offset assumed loop variables always started at
  `paramCount`, and the parameter-reference rewrite pass didn't recurse into
  several compound AST node types (`AsInstanceOf`, `RefArray`, `RefField`,
  `NewObject`, `ListLiteral`, and others), so `RefLocal` nodes nested inside
  them kept reading stale JVM slots. Added `run/RecipeManager.on` (365
  lines, 19th large sample) as additional end-to-end coverage.

- **`onion --help` silently omitted three working script-runner flags** —
  `--effects`, `--stacktrace`, and `--watch` were all parsed and honored by
  `ScriptRunner.runMain`/`run`, but `printUsage()` never listed them, so
  they were undiscoverable outside reading the source. Added them to the
  help text, documented them in `docs/tools/script-runner.md` and its ja
  counterpart, and extended `OnionCliSpec`'s help-text assertion so the gap
  can't silently reopen.

- **Writing `{ (k, v) => ... }` for a trailing lambda produced a bare
  "Encountered `{`, but expecting..." token dump**, with no indication that
  the actual mistake was the parentheses. Trailing lambdas (`m.filter { k, v
  => ... }`) never take parenthesized parameters — that syntax is reserved
  for the non-trailing `(k, v) -> ...` form — and it's an easy slip since
  both forms are documented side by side. The parser now recognizes this
  specific shape and names the fix directly: "Hint: a trailing lambda's
  parameters aren't parenthesized — write `{ k, v => ... }`, not `{ (k, v)
  => ... }`."

### Added

- **`run/TaskPlanner.on`, a 425-line sprint task planner sample.** Combines
  two ADT case-enums (`Priority`, `TaskStatus`) with a plain enum (`Tag`),
  records with `example` clauses, typed `ArrayList[Task]`/`List[Task]`
  collections, collection pipelines (`filter`/`sortedBy`/`groupBy`/
  `partition`/`any`/`count`/`distinct`/`take`/`fold`/`map`/`zip`/`find`),
  `foreach (k, v)` map iteration, extension methods on `Int`/`Double`/
  `String`, `do[Option]` chaining, nullable fields, and exhaustive `select`
  type-pattern matching (24th large sample, 81 total corpus programs).

- **`run/PlaylistManager.on`, a 502-line music playlist manager sample.** Adds a
  large end-to-end program to the corpus (18th at ≥100 lines), combining an
  ADT case-enum (`Genre`, 8 cases) and a plain enum with methods
  (`PlaybackMode`), records with `example` clauses, a class implementing an
  interface via `conforms`, `ArrayList[T]` generics, collection pipelines
  (`filter`/`fold`/`sortedBy`/`groupBy`/`partition`/`find`/`zip`/`map`/
  `distinct`), `foreach (k, v)` map iteration, range `foreach`, exhaustive
  `select` type-pattern matching, nullable types, extension methods on `Int`
  and `String`, closures, string interpolation, and `try`/`catch` in one
  coherent scenario.

- **`run/BankLedger.on`, a 299-line bank account ledger sample.** Combines an
  ADT case-enum (`TxKind`: `Deposit`/`Withdrawal`/`Transfer`), a record with
  `example` laws, `extension Int` for currency formatting, nullable-safe
  account lookups, `Map`-backed accounts with `foreach (k, v)` iteration,
  collection pipelines (`sortedBy`/`filter`/`map`/`fold`/`partition`), and
  `try`/`catch` in one end-to-end scenario (21st large sample, 78 total
  corpus programs).

### Fixed

- **CI's `Build and Test` job could hit a real JVM `OutOfMemoryError`** on the
  GitHub Actions runner as the `run/` corpus and test count grew, then wedge
  for several minutes with dying background threads before being reported as
  stuck rather than failing cleanly. Raised the job's heap headroom
  (`SBT_OPTS=-Xmx6G`, up from the `.jvmopts` default of 4g) to give the
  growing suite room to run without GC thrashing.

## [0.10.17] - 2026-08-01

### Fixed

- **Internal compiler error (operand-stack underflow) when an ADT-enum method
  body used an explicit `return` in every branch of a `select this`
  expression**, e.g. `def describe(): String = select this { case f is Found:
  return "found"; case n is None: return "none" }`. The typed AST collapses
  such a select into a `StatementTerm(BottomType)` once every branch already
  transfers control via an inner `Return`; codegen then emitted dead
  unboxing/return bytecode on an empty operand stack, which the JVM verifier
  rejected as `[I0000]`. Fixed in `AssignabilitySupport.processAssignable`
  and `ControlFlowEmitter.emitReturn` to recognize a bottom-typed
  `StatementTerm` and skip the redundant instructions.

### Added

- **`run/Inventory.on`, a 279-line shop inventory manager sample.** Adds a
  large end-to-end program to the corpus (16th at ≥100 lines), combining
  data-carrying ADT enums (`Category` and `TxKind`, four cases each),
  records with compiler-verified `example` clauses (`Item`, `Transaction`,
  `StockAlert`), extension methods on `String` and `Double`, `select` with
  type-pattern exhaustiveness, a nullable `Item?` lookup, collection
  pipelines (`filter`/`map`/`groupBy`/`sortedBy`/`find`), `foreach (k, v)`
  map iteration, closures, string interpolation, and graceful
  exception-free failure returns.

## [0.10.16] - 2026-08-01

### Added

- **`run/TournamentTracker.on`, a 473-line football world-cup simulator
  sample.** Adds a large end-to-end program to the corpus (15th at ≥100
  lines), combining an ADT case-enum (`Phase` with a data-carrying
  `GroupStage(round: Int)` case and singleton `Quarterfinal`/`Semifinal`/
  `Final` cases) matched via `select`/type patterns, plain enums with
  methods (`Division`, `SportType`), an interface (`Printable`) implemented
  through `conforms`, records with methods (`Player`, `MatchResult`,
  `Standing`), extension methods on `String` and `Int`, collection
  pipelines (`filter`/`fold`/`reduce`/`sortedBy`/`groupBy`/`partition`/
  `distinct`/`zip`), `foreach (k, v)` map iteration, range `foreach`, and
  `try`/`catch` in one coherent scenario.

- **`run/PayrollReport.on`, a 274-line employee payroll report sample.** Adds
  a large end-to-end program to the corpus (14th at ≥100 lines), combining an
  ADT case-enum (`ContractType` with `Permanent`/`Hourly`/`Freelance` cases)
  matched via `select`/type patterns, an interface (`PayrollItem`) implemented
  by a class, collection pipelines (`partition`/`zip`/`any`/`all`/`count`/
  `distinct`/`flatMap`/`sortedByDescending`/`groupBy`), `foreach (k, v)` map
  iteration, extension methods on `Int` and `String`, a recursive salary
  projection, and `try`/`catch` in one coherent scenario.

## [0.10.15] - 2026-08-01

### Added

- **`run/BudgetTracker.on`, a 308-line personal finance tracker sample.** Adds
  a large end-to-end program to the corpus (13th at ≥100 lines), combining an
  ADT case-enum (`TransactionKind` with `Income`/`Expense` cases) matched via
  `select`/type patterns, a homogeneous enum (`Month`) with methods, three
  records with methods (`Transaction`, `Category`, `MonthSummary`), a class
  with a `List[Transaction]` field, collection pipelines (`groupBy`/
  `sortedBy`/`filter`/`map`/`fold`/`partition`/`reverse`/`take`/`distinct`),
  `foreach (k, v)` map iteration, nullable-map-lookup null guards, and a
  tail-recursive compound-interest helper in one coherent scenario.

- **`run/LibraryCatalog.on`, a 329-line library management sample.** Adds a
  large end-to-end program to the corpus (12th at ≥100 lines), combining
  interfaces (`Lendable`, `Describable`) with `conforms`, class inheritance
  via primary-constructor super-args (`RareBook extends Book`), an ADT enum
  (`LoanStatus`) matched with `select`/type patterns, a homogeneous enum
  with an extension method, records, mutable class state, collection
  pipelines (`map`/`filter`/`sortedBy`/`groupBy`/`partition`/`distinct`/
  `zip`/`reduce`), `try`/`catch`, and recursion in one coherent scenario.

- **Execution coverage for `onion.Config::loadJson`.** It was implemented and
  documented in `docs/reference/stdlib.md` as the canonical entry point for
  loading a config file, but unlike `parseJson` and the accessors, never
  invoked by a `shell.run` test case in `ConfigSpec.scala` (only reached by
  an effect-table metadata assertion, never actually executed). Added a case
  covering the read-file-then-parse round trip and a case covering the
  missing-file error path.

- **Execution coverage for `onion.Http::put`/`Http::delete`.** Both were
  implemented and documented in `docs/reference/stdlib.md`'s Http "Other
  Methods" section, but unlike `get`/`post`, never invoked by a `shell.run`
  test case in `HttpSpec.scala`. Added cases against a local echo server
  covering `put`'s body passthrough, `put`'s null-body-becomes-empty
  behavior, and `delete`'s method/empty-body request.

- **Execution coverage for `onion.Future::all`/`Future::first`.** Both were
  implemented and documented in `docs/reference/stdlib.md`'s "Combining
  Futures" section right next to `zip`/`race`, but unlike those, never
  invoked by a `shell.run` test case in `FutureSpec.scala`. Added cases
  covering `all`'s in-order result list and `first`'s first-completed value.

- **Execution coverage for `onion.Option::of`.** It was implemented and
  documented in `docs/reference/stdlib.md` right next to `Option::some`/
  `Option::none`, but unlike those, never invoked by a `shell.run` test case
  in `OptionResultEnrichedSpec.scala`. Added a case covering both the
  non-null wrap and the null-collapses-to-`none` behavior that distinguishes
  it from `Option::some`.

- **Execution coverage for `onion.Result::mapError`.** It was implemented
  and documented in `docs/reference/stdlib.md` right next to `map`/`flatMap`/
  `fold`/`recover`, but unlike those, never invoked by a `shell.run` test
  case in `OptionResultEnrichedSpec.scala`. Added a case covering the `Err`
  transform and the `Ok` passthrough.

- **Execution coverage for `JInteger`/`JLong`/`JDouble`/`JBoolean` static
  methods.** `JInteger::parseInt`, `JInteger::MAX_VALUE`/`MIN_VALUE`,
  `JLong::parseLong`, `JLong::toString`, `JDouble::parseDouble`,
  `JDouble::toString`, `JBoolean::parseBoolean`, and `JBoolean::toString`
  were all implemented and documented in `docs/reference/stdlib.md`'s
  Wrapper Classes section, but — unlike `JInteger::toString`, which is
  exercised incidentally by unrelated specs — none were ever invoked
  directly by a `shell.run` test case anywhere in the suite. Added
  `WrapperClassesSpec` with one case per method.

- **Execution coverage for `onion.Config::get`.** It was implemented and
  documented in `docs/reference/stdlib.md` right next to the typed accessors
  (`getString`/`getInt`/`getLong`/`getDouble`/`getBoolean`) that all delegate
  to it, but unlike those, never invoked directly by a `shell.run` test case
  in `ConfigSpec.scala`. Added three cases: an existing dot-notation path, a
  missing path returning `null`, and a numeric path segment indexing into an
  array.

- **Execution coverage for `onion.Iterables::exists`, `forAll`, and `listOf`.**
  All three were implemented and documented in `docs/reference/stdlib.md`
  right next to `map`/`filter`/`foldl`/`sort`, but unlike those, never
  invoked by a `shell.run` test case in `IterablesSpec.scala`. Added cases
  covering `exists`/`forAll` in both the matching and non-matching case, and
  `listOf`'s varargs construction.

- **Execution coverage for `onion.Proc::captureIn` and `Proc::execIn`.** Both
  were implemented and documented in `docs/reference/stdlib.md` right next to
  `runIn`, but unlike `runIn`, never invoked by a `shell.run` test case in
  `ProcSpec.scala`. Added one case each, exercising the working-directory
  behavior via the process's own exit status/stderr.

- **Execution coverage for `onion.Json::stringifyPretty`.** It was implemented
  and documented in `docs/reference/stdlib.md` right next to `Json::stringify`,
  but unlike `stringify`, never invoked by a `shell.run` test case in
  `JsonSpec.scala`. Added two cases covering object indentation and a nested
  array inside an object.

- **Execution coverage for `onion.Strings::replaceRegex`.** It was implemented
  and documented in `docs/reference/stdlib.md` right next to `Strings::replace`,
  but unlike `replace`, never invoked by a `shell.run` test case in
  `StringsSpec.scala`. Added one case.

- **Execution coverage for `onion.Files::list`.** It was implemented and
  documented in `docs/reference/stdlib.md` right next to `Files::glob`, but
  unlike `glob`, never invoked by a `shell.run` test case anywhere in the
  suite. Added `FilesListSpec` with one case.

- **Execution coverage for `onion.Files::readBytes` / `Files::writeBytes`.**
  Both were implemented and documented in `docs/reference/stdlib.md` right
  next to `readText`/`writeText`, but unlike those, never invoked by a
  `shell.run` test case anywhere in the suite. Added `FilesBytesSpec` with a
  round-trip case.

- **Execution coverage for `onion.Rand::nextInt()` / `Rand::nextLong()`
  (no-arg overloads).** Both were implemented and documented in
  `docs/reference/stdlib.md` right next to their bounded counterparts, but
  unlike those, never invoked by a `shell.run` test case in `RandomSpec.scala`.
  Added one case each.

- **Execution coverage for `onion.Origin::spanning`.** It was implemented and
  documented in `docs/reference/stdlib.md` right next to `Origin::at` and
  `Origin::atLine`, but unlike those, never invoked by a `shell.run` test
  case in `OriginSpec.scala`. Added a case covering the normal span and one
  covering the `Math.max(1, span)` clamp for a non-positive span.

- **Execution coverage for the generic static `onion.Stats::sum`.** It was
  implemented and documented in `docs/reference/stdlib.md` as the first
  example in the Stats module section, but unlike `sumInt`/`sumLong` and the
  other aggregates, never invoked directly by a `shell.run` test case in
  `HashCodecStatsSpec.scala` (only reached transitively through
  `xs.sum()`). Added a case to `HashCodecStatsSpec`.

- **Execution coverage for `onion.Origin::hasColumn` and `Origin::inSource`.**
  Both were implemented and documented in `docs/reference/stdlib.md` right
  next to `onLine` (`inSource` in the same subsection, `hasColumn` in the
  module's intro example), but unlike `onLine`, never invoked by a
  `shell.run` test case in `OriginSpec.scala`. Added a case for
  `hasColumn()` distinguishing `at()` from `atLine()`, and a case for
  `inSource()` retargeting an Origin's source while keeping line/column.

- **Execution coverage for `onion.Config::getLong` and
  `Config::getWithEnvOverride`.** Both were implemented and documented in
  `docs/reference/stdlib.md` right next to `getInt`/`getDouble`/`getBoolean`
  and `getEnv`, but unlike those, never invoked by a `shell.run` test case in
  `ConfigSpec.scala`. Added two cases per method (value found / falls back to
  default).

- **Execution coverage for `onion.Option::orNull` and `onion.Result::orNull`.**
  Both were implemented and documented in `docs/reference/stdlib.md` right
  next to `getOrElse`/`orElseGet`, and even name-checked in
  `OptionResultEnrichedSpec`'s own doc comment as covered, but unlike their
  siblings, never actually invoked by a `shell.run` test case. Extended the
  existing "orElseGet, orNull and orElse" (Option) and "orElseGet, exists and
  toList" (Result) cases to also assert `.orNull()` on both the
  present/`Ok` and absent/`Err` sides.

- **Execution coverage for `onion.Future::race`.** It was implemented and
  documented in `docs/reference/stdlib.md` under "Combining Futures", but
  unlike `zip`, never invoked by a `shell.run` test case in `FutureSpec.scala`
  — the file had no "combining futures" block at all. Added one, racing two
  already-completed futures holding the same value so the winner is
  deterministic regardless of which completes first.

- **Execution coverage for `onion.Result::ofNullable` and `Result::trying`.**
  Both were implemented and documented in `docs/reference/stdlib.md` right
  next to `Result::ok`/`Result::err`, but unlike those, never invoked by a
  `shell.run` test case in `OptionResultEnrichedSpec.scala`. Added a case
  covering `ofNullable`'s non-null/null branches and `trying`'s
  success/throw branches.

- **Execution coverage for `onion.Http::post` and `Http::postJson`.** Both
  were implemented and documented in `docs/reference/stdlib.md`'s Http "POST
  Requests" subsection, but unlike `put`/`delete`, never invoked by a
  `shell.run` test case in `HttpSpec.scala`. Added cases against the local
  echo server covering `post`'s body passthrough, `post`'s null-body-becomes-
  empty behavior, `postJson`'s body passthrough, and `postJson`'s
  null-body-becomes-`"{}"` behavior.

- **`run/LibrarySystem.on`, a 374-line library management sample.** Adds a
  large end-to-end program to the corpus (10th at ≥100 lines), combining a
  plain enum matched via `select`, four records, a class with mutable `List`
  fields, `String`/`Int` extension methods, method chaining, string
  interpolation, bubble sort, and recursion in one coherent scenario.

## [0.10.14] - 2026-07-31

### Fixed

- **`errorMessage_ja.properties` had two diagnostics that were untranslated or
  half-translated English prose instead of Japanese text**: `error.parsing.syntax_error`
  (the most common parse-error message of all — only the "syntax error" lead-in was
  translated, with "Encountered ..., but expecting ..." left as raw English) and
  `error.semantic.lValueRequired` (E0028, entirely untranslated: "lvalue required.").
  `LawCheckMessageI18nSpec` only asserted that a key *resolves* in both locales, not
  that the Japanese text is actually Japanese, so this slipped through untested.
  Translated both messages and added `ErrorMessageJapaneseTranslationSpec` so a
  future untranslated/half-translated entry fails the build instead of shipping
  silently.

### Added

- **Execution coverage for `onion.Maps::filterKeys` and `Maps::forEach`.**
  Both were implemented and documented in `docs/reference/stdlib.md`, but
  unlike every other `Maps` method, never invoked by a `shell.run` test
  case — `MapsEnrichedSpec`'s own docstring claimed to cover `forEach`
  without actually calling it, and `filterKeys` wasn't mentioned at all.
  Added one case per method to `MapsEnrichedSpec`.

- **Execution coverage for `onion.Sets::newSet`, `Sets::containsAll`, and
  `Sets::forEach`.** All three were implemented and documented in
  `docs/reference/stdlib.md`, but unlike every other `Sets` method, never
  invoked by a `shell.run` test case (`isSubsetOf`/`isSupersetOf` exercise
  `containsAll` only transitively, not as a direct call). Added one case per
  method to `SetsEnrichedSpec`.

- **Execution coverage for `onion.Csv::stringify`.** It was implemented and
  documented (the direct inverse of `Csv::parse`) but, unlike its sibling
  `stringifyWithHeader`, never invoked by a `shell.run` test case in
  `CsvEnrichedSpec`. Added a round-trip case and a comma-quoting case.

- **Execution coverage for 8 `onion.Json` accessor methods that were
  documented in `docs/reference/stdlib.md` but never invoked by a
  `shell.run` test case.** `getLong`, `getFloat`, `getShort`, `getByte`
  (the boxed, null-on-missing accessors) and `getLongOr`, `getDoubleOr`,
  `getFloatOr`, `getBooleanOr` (the defaulted variants) had no coverage,
  unlike their siblings `getInt`/`getDouble`/`getBoolean`/`getIntOr`/
  `getStringOr`. Added `JsonAccessorCoverageSpec` with one case per method.

- **Execution coverage for `onion.Stats::variance` and `onion.Stats::sumLong`.**
  Both members were implemented and documented but, unlike every other
  `Stats` method, never invoked by a `shell.run` test case in
  `HashCodecStatsSpec`. Added one case per method.

- **Execution coverage for the 20 `onion.OnionMath` members `MathSpec` never
  exercised.** `asin`/`acos`/`atan`/`atan2`, `sinh`/`cosh`/`tanh`, `log10`/
  `cbrt`, `absFloat`/`absLong`, `minLong`/`maxLong`, `roundFloat`, `signum`/
  `signumFloat`, `toRadians`/`toDegrees`, `clampInt`, and `hypot` had never
  been run through the compiler/shell — only 18 of the module's 39 public
  methods had a `shell.run` case. Added one per method, following the
  existing pattern.

- **Execution coverage for 10 `onion.DateTime` members that were never run
  through the compiler/shell.** `addHours`/`addMinutes`/`addSeconds`,
  `diff`/`diffSeconds`, `dayOfYear`, `startOfDay`/`endOfDay`, and both
  `nowString` overloads had no `shell.run` case across `DateTimeSpec`,
  `DateTimeParseSpec`, or `DateTimeExtraSpec` — `DateTimeExtraSpec`'s
  docstring even claimed to cover `diffSeconds` without actually calling it.
  Added `DateTimeCoverageSpec` with one case per method, using fixed
  timestamps for determinism.

- **Execution coverage for `onion.Regex::groupsAll`** (both the `String`
  pattern and `re"..."`/`Pattern` overloads). It was documented and
  implemented but, unlike every other `Regex` method, never invoked by a
  `shell.run` test case. Added multi-match, no-match, and `re"..."` literal
  cases to `RegexSpec` and `RegexPatternInteropSpec`.

### Fixed

- **A `val (a, b) = expr` destructuring declaration as the trailing element of
  a block used in expression position (e.g. `val x = { val (a, b) = expr }`)
  crashed the compiler with a raw `MatchError`**, reported as an `[I0000]`
  internal compiler error instead of a clean diagnostic. `typeBlockExpression`
  only special-cased `LocalVariableDeclaration` for a block's last element;
  `DestructuringDeclaration` now follows the same path and reports the same
  clean type diagnostic a plain `val` declaration gets there. Added a
  crash-corpus regression (`031-destructuring-last-in-expr-block.on`).

- **`onion.OnionMath` — a genuine, default-imported stdlib module with 41 public
  members (`sin`/`cos`/`tan`, hyperbolic trig, `clamp`/`clampInt`, `hypot`,
  bounded `randomInt`, ...) — had no documentation in `docs/reference/stdlib.md`
  or `docs/ja/reference/stdlib.md`,** unlike every other stdlib module. Added an
  "OnionMath Module" section to both files (mirroring the existing "Math
  Module" style) and a row in "Modules at a glance", plus
  `OnionMathDocCoverageSpec` so a future addition to `onion.OnionMath` fails
  the build instead of silently staying undocumented.

- **`docs/ja/examples/basic.md` was missing the "CSV Processing" and "Regex Log
  Parsing" sections** present in the English `docs/examples/basic.md`.
  Translated the missing sections and added `BasicExamplesDocParitySpec` so a
  future stdlib call added only to the English doc fails the build instead of
  silently widening the gap again.

- **`docs/ja/guide/collections.md`'s "Map Iteration" section had an untranslated
  intro sentence and was missing the `[String, Integer]` generic type arguments
  on `Map.Entry`** that the English version's example uses, under-demonstrating
  nested-class-with-type-parameters usage in a `foreach` type position. Added a
  `CollectionsDocMapIterationParitySpec` drift guard.

- **`docs/ja/guide/scripting.md` only translated the back half of
  `docs/guide/scripting.md`,** missing the "Running Scripts", "Command-Line
  Arguments", "Running External Commands", "Files and Globs", and "JSON and
  HTTP" sections at the top, and the "Putting It Together" section at the
  end. Translated the missing sections and added `ScriptingDocParitySpec` so
  a future stdlib call added only to the English guide fails the build
  instead of silently widening the gap again.

- **`docs/ja/reference/stdlib.md`'s "Future Module" section trailed off partway
  through**, missing the "Error Handling", "Status Queries", "Combining
  Futures", and "Conversions" subsections entirely, plus the `asyncThrowing`,
  `filter`/`bind`, and `onComplete` calls documented within its existing
  subsections. Translated the missing content and added
  `StdlibDocFutureModuleParitySpec` so a future addition to the English
  section fails the build instead of silently widening the gap again.

- **`docs/ja/guide/overview.md` only translated the first three "Language
  Characteristics" subsections**, missing "JVM Target" and "Java
  Interoperability" entirely, and never translated the "Syntax Highlights"
  section (`val`/`var` fields, type annotations, `::` static access, `as`
  casting, `select` pattern matching) at all. Translated the missing
  subsections and added `OverviewDocParitySpec` so a future subsection added
  only in English fails the build instead of silently widening the gap
  again.

## [0.10.13] - 2026-07-30

### Fixed

- **`docs/ja/reference/stdlib.md` trailed off partway through the "Math Module"
  and "Function Interfaces" sections,** missing `Math::floor`/`Math::ceil`/
  `Math::round`, `Math::sin`/`Math::cos`/`Math::tan`, the Math Constants
  subsection, and `Function3` through `Function10` — all present in the
  English `docs/reference/stdlib.md`. Translated the missing subsections and
  added `StdlibDocMathAndFunctionInterfacesParitySpec` so a future subsection
  added only in English fails the build instead of silently widening the gap
  again.

- **`docs/ja/examples/index.md` was a stale 38-line stub that predated most of
  the "Example Index" table in `docs/examples/overview.md`,** missing 64 of
  the `run/*.on` example entries (and the Learning Path section) entirely.
  Brought it to full parity with the English overview, and added
  `ExamplesOverviewDocParitySpec` so a future example added only in English
  fails the build instead of silently widening the gap again.

- **`Deploy MkDocs to GitHub Pages` failed on every push since before v0.10.11.**
  `docs/design/roadmap.md` linked to `../../CLAUDE.md` — a real repo-root file,
  but one that lives outside mkdocs' `docs_dir` (`docs/`), so `mkdocs build
  --strict` reported it as broken and aborted the deploy even though the link
  resolved fine on GitHub. The link now points at the GitHub blob URL instead,
  which isn't subject to mkdocs' local-file link resolution. (#486)

- **`Yaml::stringify` silently corrupted a map whose key contained `: ` (or
  leading/trailing whitespace) instead of failing.** The value side already
  double-quoted a string that would be misread on parse-back (containing `:`,
  `#`, a newline, or looking like a number/boolean — the `[0.10.12]` fixes
  below hardened the same hazard for `lines()`/`sepBy()`/`config` shapes), but
  the *key* was written raw. A key like `"a: b"` rendered as `a: b: 1`, which
  `Yaml::parse` read back as key `"a"` with value `"b: 1"` (a String) — the
  original key silently disappeared with no exception. `Yaml::stringify` now
  quotes the key under the same rule it already applies to values, symmetric
  with how `Json::stringify` has always quoted keys; the parser already
  supported reading a quoted key, so no parser change was needed.

- **A `tool` declaration with no explicit return type failed to compile with
  `[E0051] return type is required`,** even though the grammar and the
  documented syntax (`tool name(args) [: T] [requires { caps }] { body }`)
  both treat the return type as optional. `TypingOutlinePass` required an
  explicit return type on every top-level function unconditionally; it now
  defaults a `tool`'s omitted return type to `void`, same as writing `: void`
  explicitly — matching how the parser already accepted the form.

- **`Deploy MkDocs to GitHub Pages` logged a warning on every build for the seven
  files under `docs/superpowers/**`** (agent-facing planning material, never part
  of the public site), because nothing told mkdocs they weren't meant to be in
  `nav`. `mkdocs.yml` now excludes `superpowers/` via `exclude_docs`, and a new
  `MkDocsNavCoverageSpec` fails the build if a future doc under `docs/` goes
  unreferenced by `nav` again without being excluded on purpose. (#486)

## [0.10.12] - 2026-07-29

### Fixed

- **The `config` lossless shape (`shape name = config`) silently corrupted a document
  when a printed or edited value's text contained a line break.** `key = value` lines
  run to the end of the line, so rendering such a value split it into a bogus extra
  line indistinguishable from a real entry — e.g. editing a `String` field to
  `"evil\nport = 9999"` injected a fake `port` entry that a later `parseLossless`
  either read back with the wrong value or rejected as a duplicate, with no error at
  the point of corruption. `ConfigShape.print` and `.printLossless` now throw
  `IllegalArgumentException` naming the offending field instead of misrendering,
  consistent with the shape's documented L1 guarantee (`parse(print(v)) == Ok(v)`)
  and the "refuse rather than pretend" handling already used for a foreign residue.
- **`Shape#lines()` and `Shape#sepBy(separator)` had the same silent-corruption bug as
  the `config` shape, one level down.** Printing a list where an element's own rendering
  contained a newline (`lines()`) or the separator text (`sepBy`) split that one element
  into two on read-back, indistinguishable from a real boundary — the shapes' own
  Javadoc already warned L1 depended on this not happening, but nothing enforced it.
  `LineShape.print` and `SepByShape.print` now throw `IllegalArgumentException` naming
  the offending element's index instead of misrendering.

### Documentation

- **`docs/guide/shapes.md` (en/ja) never documented the "refuse rather than corrupt"
  behavior of `lines()`, `sepBy(separator)`, and `shape name = config`.** The guide's
  "Writing" section described the L1 round-trip guarantee but said nothing about the
  `IllegalArgumentException` these shapes throw when a value's own rendering would be
  indistinguishable from a real delimiter on read-back — behavior shipped by the two
  fixes above. Added a short explanation with an example to both language versions.
- **`docs/ja/CLAUDE_ja.md`'s "テスト" (Testing) section was missing the
  "Locale-independence (IMPORTANT)" paragraph present in `CLAUDE.md`.** The paragraph
  warns that error messages are bilingual and release CI runs in an English locale, so
  its absence left Japanese-reading contributors without the one warning most likely to
  save them from a test that passes locally but fails only in CI. Added the translation
  and a new `ClaudeMdTestingLocaleIndependenceParitySpec` drift guard so a future
  omission fails the build instead of silently rotting.
- **`docs/ja/reference/stdlib.md` was missing the "Wrapper Classes" (`JInteger`,
  `JLong`, `JDouble`, `JBoolean`) and "Common Java Classes" (`String`,
  `StringBuilder`, `ArrayList`, `HashMap`, `File`, `BufferedReader`,
  `BufferedWriter`) sections present in `docs/reference/stdlib.md`, even though
  both languages' "at a glance" tables promise this coverage. `StdlibDocDriftSpec`
  couldn't catch it — those names are deliberately excluded there as JDK types /
  import aliases rather than `onion.*` stdlib classes. Added the translation and a
  new `StdlibDocWrapperAndCommonJavaClassesParitySpec` drift guard.

## [0.10.11] - 2026-07-29

### Fixed

- **`docs/ja/CLAUDE_ja.md`'s "既知の制限" (Known Limitations) section was missing
  the bullet about local (left-to-right) type inference that exists in the
  English `CLAUDE.md`.** Added a new `ClaudeMdKnownLimitationsParitySpec` drift
  guard that asserts the bullet counts match between the two files, so a future
  translation omission fails the build instead of silently rotting.
- **`onion.IO`'s stdin readers (`readLine`, `readln`, `readInt`, `readLines`,
  `eachLine`, ...) ignored a `System.setIn` swap made after the class had
  already loaded.** The reader was a `BufferedReader` cached once in a static
  field over whatever `System.in` was at class-init time, so redirecting
  stdin afterwards (embedding hosts, REPLs, or tests) silently kept reading
  from the original stream. It now re-wraps `System.in` whenever the current
  stream differs from the one last wrapped. `run/ReadLine.on` — which reads
  via `IO::readln`, unlike the samples that build their own reader — is now
  asserted on by `RunSamplesSpec` with a redirected stdin.

### Added

- **Execution coverage for `JsonApiClient.on`.** The only remaining `run/`
  sample that was compile-checked only (besides the Swing GUI `Calculator.on`,
  which needs a display); its `Http::get` call against a live URL falls back
  gracefully when the network is unreachable, so it is now asserted on by
  `RunSamplesSpec` alongside the other network-calling samples.

## [0.10.10] - 2026-07-29

### Added

- **Execution coverage for `GuessNumber.on`, `LineFilter.on`, and `TodoApp.on`.**
  These samples build their own `BufferedReader` over `System::in` at execution
  time (unlike `onion.IO`'s stdin reader, a static field bound to whatever
  `System.in` was when that class first loaded), so feeding a fixed
  `System.setIn` stream before each run makes them deterministic to assert on;
  now asserted on by `RunSamplesSpec` instead of only compile-checked by
  `SampleCompilesSpec`.

### Fixed

- **A `tool` CLI's own validation failures (missing argument, unknown option, a
  value that fails to parse) printed the right message but exited 0 instead of
  1**, so a shell script checking `$?` after a misused tool CLI saw success.
  `docs/guide/tools.md` documents these failures as returning `1` from `main`,
  and `ToolCli.dispatch` did return that `1` — but `ScriptRunner.run`, the real
  `onion` command-line entry point, discarded every script's returned `Int` and
  always reported exit code 0 for a successful (non-throwing) run
  (`case Shell.Success(_) => 0`). Any script using the `args: String[]` main
  shape to signal a specific exit code via its return value hit the same gap.
  `ScriptRunner` now uses `main`'s returned `Int` as the process exit code when
  one is returned, and still exits 0 for a `void` main.

- **`docs/design/roadmap.md` still read as an open plan for v0.6-v0.8**, describing
  `Shape[T]`, `Outcome`/`Defect`, `tool` declarations, effects, and lossless shapes as
  future work with links to open issues — but all 19 tracking issues (#346-#364) are
  closed and every item already ships in v0.10.9 (see `CLAUDE.md`'s "Tools, capabilities
  and effects" section). Marked the page as a shipped historical record, pointing readers
  at `CLAUDE.md` and `docs/reference/stdlib.md` for the current syntax; updated the
  mkdocs nav label to match.

## [0.10.9] - 2026-07-28

### Added

- **Execution coverage for `AsyncDownloader.on`.** Its futures aggregate via
  `Future::all`, which preserves input order regardless of completion
  timing, so the concurrent-download output is deterministic; now asserted
  on by `RunSamplesSpec` instead of only compile-checked by
  `SampleCompilesSpec`.

- **Execution coverage for `ShellPipeline.on`.** Its `Proc::capture`/`Proc::run`
  calls (`wc`, `sort | head` via `sh -c`, `echo`) only ever hit standard
  POSIX utilities present on the CI runner and act on a fixed, already
  alphabetically-sorted input list, so the output is deterministic; now
  asserted on by `RunSamplesSpec` instead of only compile-checked by
  `SampleCompilesSpec`.

- **Execution coverage for `Select.on` and `LineCounter.on`.** `Shell.run`
  reports `Success` on the reflected return value of `main`, not captured
  stdout, so both samples are deterministic to assert on (`Success(null)`)
  even though `Select.on` branches on `Math::random()` and `LineCounter.on`'s
  printed count depends on the current repo tree; now asserted on by
  `RunSamplesSpec` instead of only compile-checked by `SampleCompilesSpec`.

### Fixed

- **`break`/`continue` inside a lambda body crashed the compiler instead of
  reporting E0048/E0049.** A closure body is compiled into its own
  synthesized method with no enclosing loop, so a `break`/`continue`
  lexically inside a lambda that merely sits inside a loop in the source
  cannot actually reach that loop. Typing tracked loop/label nesting with a
  plain counter and label stack that a closure body never reset, so the
  check wrongly treated the lambda as being inside the loop; the program
  passed typing and only blew up later in codegen with a raw
  `[I0000] Internal compiler error ... Break statement outside of loop`.
  `break`/`continue` inside a closure now correctly report E0048/E0049 at
  the closure body, matching top-level break/continue diagnostics.

## [0.10.8] - 2026-07-28

### Added

- **Execution coverage for 2 more `run/` examples: `TodoManager.on`,
  `UnitConverter.on`.** Same treatment as prior batches — deterministic,
  no-stdin/file/network/GUI examples that were previously only compile-checked
  by `SampleCompilesSpec`, not asserted on runtime output by `RunSamplesSpec`.

- **Execution coverage for 3 more `run/` examples: `ConfigApp.on`,
  `JsonYamlShapeDemo.on`, `ShapeFirst.on`.** Same treatment as above, for the
  next batch of deterministic, no-stdin/file/network/GUI examples.

- **Execution coverage for `HttpJsonClient.on`.** Its no-args invocation hits
  the early usage-message return before any network call, so it is
  deterministic and now asserted on by `RunSamplesSpec` instead of only
  compile-checked by `SampleCompilesSpec`.

### Fixed

- **A malformed `onionc`/`onion` command line either crashed with a raw stack
  trace or failed with no message at all, instead of reporting the bad
  option.** `onionc` looked up the message key `error.command..noArgument`
  (a stray extra dot) for an option missing its required argument, so
  `Message.apply` threw an uncaught `MissingResourceException` — e.g.
  `onionc -classpath` with nothing after it crashed instead of printing a
  diagnostic. Separately, `onion`'s failure reporting only ever inspected
  lacked-argument options, never unrecognized ones, so a typo'd flag (e.g.
  `-maxErrorReports`, the exact misspelling this project's own docs used to
  carry — see below) failed silently with exit code -1 and empty stderr. Both
  frontends also leaked the option wrapper's `toString` into the message
  (`ValuedParam(-foo) is invalid argument.`) instead of the bare flag name.
  Fixed all three: the typo'd resource key, `onion` now reports unrecognized
  options the same way `onionc` does, and both print the plain flag name.

- **Docs and README documented a CLI flag that doesn't exist.** `-maxErrorReports`
  (plural) was never a real option — the actual flag is `-maxErrorReport`
  (singular) — so following the docs literally tripped the silent-failure bug
  above. Corrected in `CLAUDE.md`, `docs/ja/CLAUDE_ja.md`,
  `docs/tools/compiler.md`, `docs/ja/tools/compiler.md`,
  `docs/tools/script-runner.md`, `docs/ja/tools/script-runner.md`, and
  `README.md` (which also had "comiplation" for "compilation").

## [0.10.7] - 2026-07-28

New diagnostic E0083 catches a shadowed `catch` clause that previously compiled
silently and could never run, plus 9 more `run/` examples get execution coverage
instead of compile-checking only.

### Added

- **Execution coverage for 6 more `run/` examples that were compile-checked only.**
  `RunSamplesSpec` covered a first batch of previously compile-only examples in
  0.10.6, but `Bean.on`, `JavaGenerics.on`, `OrderReport.on`, `RegexLogParser.on`,
  `ResultValidation.on`, and `ShapeProcessor.on` were still only checked for
  successful compilation, not for correct runtime behavior. All six are
  deterministic with no stdin/file/network/GUI dependency (unlike `Calculator.on`'s
  Swing UI or `Select.on`'s `Math::random()`), so `RunSamplesSpec` now runs each
  and asserts on `Shell.Success`.

- **Execution coverage for 3 more `run/` examples: `SchemePrefix.on`,
  `TextAnalyzer.on`, `StatsApp.on`.** Same treatment as above, for the next batch
  of deterministic, no-stdin/file/network/GUI examples that were previously
  untested by `RunSamplesSpec`.

### Fixed

- **A later `catch` clause already covered by an earlier one compiled silently and
  could never run.** `catch e: RuntimeException { ... } catch e: IllegalArgumentException
  { ... }` accepted the program and ran only the first clause for every exception,
  since `IllegalArgumentException` is a `RuntimeException`. javac rejects this at
  compile time; Onion had no equivalent check. Now **E0083**, EN+JA, naming both the
  unreachable type and the earlier clause that shadows it. The alternatives of a single
  multi-catch clause (`catch e: A | B`) are exempt from shadowing each other. Found and
  fixed in both places `try`/`catch` is typed (`TryExpressionTyping` for try-as-expression,
  `BlockElementLowering` for try-as-statement — the two paths duplicate this logic today).

## [0.10.6] - 2026-07-28

Documentation and coverage: every diagnostic code is now looked-up-able, twelve more
`run/` examples are executed rather than only compiled, and a generic ADT enum's
singleton case accepts the expected-type constructor.

### Added

- **Every diagnostic code is now listed in the reference (EN+JA).** The page explained
  the common codes in prose and stopped there, so 59 of the 80 could not be looked up at
  all — including several added during the past week. A complete table was generated
  from `SemanticError` and its message bundle, and `ErrorCodeDocCoverageSpec` now fails
  the build when a code is declared without being documented, or documented after being
  retired.

- **Execution coverage for 12 `run/` examples that were compile-checked only.**
  `SampleCompilesSpec`/`SampleProgramsSpec` compile every file in `run/`, but
  `RunSamplesSpec` only *executes* a subset of them with an asserted result — a
  runtime regression (wrong output, an exception) in an example outside that
  subset could land undetected even though the file still compiled. `Factorial.on`,
  `Generics.on`, `List.on`, `Array.on`, `StringCat.on`, `Foreach.on`, `Hello.on`,
  `NullSafety.on`, `StaticImports.on`, `Delegation.on`, `Extension.on`, and
  `ExprEval.on` are deterministic and have no stdin/file/network dependency, so
  `RunSamplesSpec` now runs each and asserts `Shell.Success(null)`.

### Fixed

- **A generic ADT enum's singleton case rejected the expected-type constructor
  diamond.** `val o: Opt[String] = new Nothing()` reported `Nothing` as a raw
  type (`E0066`) even though the declared type fully determines its type
  argument — `Nothing` conforms to `Opt[T]` in `enum Opt[T] { case Some(value:
  T); case Nothing }` by passing its own type parameter straight through. The
  existing constructor-diamond inference (`val b: Box[String] = new Box("x")`)
  only matched when the constructed class was exactly the expected type's
  class, not a subtype of it, so any zero-arg case of a generic sum type
  needed explicit type arguments (`new Nothing[String]()`) even when the
  target's declared type already pinned them. The same gap applied to a
  hand-written `record Empty[T]() conforms Box[T]`.

### Documentation

- **`E0080` (a user-written `Shape` asserting no law) had no entry in
  `docs/reference/error-codes.md` / `docs/ja/reference/error-codes.md`.** The
  code has been implemented, tested, and referenced by name from
  `docs/guide/shapes.md` since issue #364, but the reference page jumped from
  `E0079` straight to `E0057`/`E0081`/`E0082` with no `E0080` section. Added it
  (EN + JA) next to the other shape/law codes (`E0074`-`E0076`), with the
  fixed-width `Shape[Person]` example already used in the guide and test suite.

## [0.10.5] - 2026-07-28

### Changed

- **An uncaught runtime error is now presented like a diagnostic (#450).** It used to
  reach the JVM's default handler, which printed the synthesized `start`/`main`
  wrappers, the reflective launcher, and — for a `StackOverflowError` — thousands of
  identical frames. A script error now reads:

  ```
  a.on:2: error: division by zero
    (run with --stacktrace for the full JVM trace)
  ```

  Frames the user did not write are dropped, the failure is named in the language's own
  vocabulary where there is one (`division by zero`, `array index out of range`, `null
  value used`), the call path is shown but capped with a count of what was elided, and a
  stack overflow explains what it usually means (non-terminating recursion, or recursion
  deeper than the JVM stack — TCO covers direct and mutual self-calls only). The exit
  code is unchanged, and **`--stacktrace`** restores the untouched trace. The error
  names and notes are localized (EN+JA) like every other diagnostic; which JVM messages
  are redundant is decided per exception type rather than by matching the headline text,
  since the headline is translated and the JVM message never is.

### Fixed

- **`Rand`'s methods were documented under old names in the Japanese reference and in
  `CLAUDE.md` (#449 follow-up):** `Rand::int`/`long`/`double`/`boolean` do not exist —
  the names are `nextInt`, `nextLong`, `nextDouble`, `nextBoolean`, as the English
  reference already said.

### Added

- **A drift guard for the whole standard-library reference (#449).**
  `AssertStdlibDocSpec` covered `Assert` after every documented name on that class
  turned out to be wrong; `StdlibDocDriftSpec` now checks *every* `Class::method` in
  both copies of `stdlib.md`, and the module list in `CLAUDE.md`, against the real
  classes by reflection. It found the `Rand` drift above on its first run. Aliases
  (`JInteger`, …), JDK types and example-local record types are excluded by an explicit
  list, since those are the false positives that made the original `Assert` breakage
  hard to see.

### Fixed

- **`docs/reference/stdlib.md` (EN+JA) and `CLAUDE.md` documented `Assert` methods that
  do not exist (#449).** `Assert::assertTrue`/`assertFalse`/`assertEquals`/`assertNotEquals`/
  `assertNotNull`/`assertNull` were never the real API — copying the example straight out
  of the reference failed to compile with **E0005**. The real names (`isTrue`, `isFalse`,
  `equals`, `notEquals`, `notNull`, `isNull`) are used everywhere else in the codebase; the
  docs now match, and a new drift-guard spec (`AssertStdlibDocSpec`) checks the documented
  names against `onion.Assert` via reflection so this can't rot silently again. Also
  renamed the `Yaml` module's example record from `Config` to `ServerConfig` in
  `stdlib.md`, since it shadowed the unrelated `onion.Config` module documented later in
  the same file and was a likely source of the confusion.
- **A duplicate bare top-level `var`/`val` went completely unreported (#445).** A
  modifier-qualified global (`static var x = ...` twice) already reported **E0011**, but
  a *bare* top-level `var`/`val` — which is promoted to a `public static` field of the
  script's synthetic class, so it's just as global — took a different code path that
  silently swallowed the redeclaration. Depending on the mix of `var`/`val` this either
  compiled clean with the second declaration quietly shadowing the first, or misreported
  the unrelated **E0007** (duplicate local). Both now report **E0011**, consistent with
  the modifier-qualified form.
- **`--dump-ast` and `--dump-typed-ast` were silently no-ops.** Both flags parsed into
  `CompilerConfig` in `onionc`/`onion`, but nothing ever read them back, so the
  documented "print parsed/typed AST to stderr" behavior never happened — the compiler
  ran normally and printed nothing extra. Both CLIs now render the AST captured in the
  pipeline's debug artifacts (`--dump-ast` even when a later phase fails, since the
  parsed AST is still available).

## [0.10.4] - 2026-07-27

The five issues found in the post-0.10.3 audit (#436-#440), fixed across #441-#442.

### Fixed

- **Two tools with the same name produced an unreachable second tool (#436).**
  Overloading is legal for functions, but the CLI selects a tool by name alone, so the
  second `tool same(...)` could never be invoked and `--contract` listed both under the
  same name — the unknown-tool message even read `Tools: same, same`. Now **E0082** at
  compile time, EN+JA.
- **A tool CLI could not be passed a value starting with `--`, and ignored the
  conventional `--` separator (#437).** `onion s.on -- --help` printed help instead of
  passing `--help` as the value. A bare `--` now ends the options — including for the
  built-in mode flags, whose pre-scan stops there too — so any string value is
  reachable. Negative numbers already worked and still do.
- **`--plan` and `--contract` together silently picked one (#439).** The precedence was
  an artifact of the order the checks were written in. Passing more than one mode flag
  is now an error naming the ones given.
- **All dependabot alerts in `vscode-onion` cleared (#440).** Six advisories
  (`brace-expansion`, `linkify-it`, `minimatch`, `markdown-it`) came in through the
  extension's dev toolchain. Rather than `npm audit fix --force` — which would have
  bumped four packages blind — the carriers were updated deliberately: eslint 8 → 10,
  typescript-eslint 6 → 8, `@vscode/vsce` 2 → 3, and `vscode-languageclient` 9 → 10
  (the only runtime dependency, and the last vulnerable path). **0 vulnerabilities.**

  Two latent problems surfaced while doing it. `npm run lint` had *never worked* —
  there was no ESLint configuration file at all, so the script always failed; a flat
  config was added and the extension now lints clean. And `engines.vscode` claimed
  `^1.75.0` while the shipped `vscode-languageclient` 9 already required `^1.82.0`; it
  is now `^1.91.0`, matching what the code actually needs. `tsconfig.json` moves to
  `Node16` module resolution, which languageclient 10 requires.
- **A repeated tool CLI flag silently took the last value (#438).** `ToolCli.dispatch`
  assigned into a flag's or switch's slot on every occurrence with no check for one
  already filled, so `--n 2 --n 3` accepted `3` without comment — the same class of
  mistake `E0044`/`DUPLICATE_ARGUMENT` already rejects for a named argument in source.
  A flag or switch given more than once now reports `option `--name` was given more
  than once` and exits 1.

## [0.10.3] - 2026-07-27

The six issues found in the post-0.10.2 audit (#424-#429), fixed across #430-#434.

Two of them were silent failures — a `tool` that compiled to a program doing nothing,
and a dry run naming a path the run would not touch — and two were drift: documented
figures and an effect table that nothing kept honest. Both kinds now fail the build.

### Fixed

- **A `tool` with a non-CLI-convertible parameter compiled to a silent no-op (#424).**
  A `tool`-only script has no `main` and no top-level statements, so when CLI synthesis
  bailed out on a parameter it could not read from a command-line string (a record,
  say), nothing ever called the tool — the script exited 0 having done nothing, with no
  diagnostic at all. It is now a compile error naming the offending tool and parameter
  and listing the supported types, as **E0081** with EN+JA messages and a reference
  entry, one report per offending parameter. Documented in `docs/guide/tools.md`
  (EN+JA).
- **`--plan` reported the bound parameter value as if it were the path touched
  (#425).** A capability binds an effect to a *parameter*, but a body may compute
  `dst + "." + i` from it — the shipped demo does exactly that, so the plan said
  `write dst = out.txt` while the run created `out.txt.0` and `out.txt.1`. The operand
  now reads `derived from dst = out.txt`, and the closing line says operands are the
  arguments the effects are derived from, not necessarily the exact paths or hosts
  touched. Guide updated EN+JA.
- **`E0043 UNKNOWN_PARAMETER_NAME` never fired; a misspelled named argument was
  reported as a generic not-found instead (#426).** Candidate filtering dropped every
  overload as soon as one named argument failed to match a parameter, so by the time
  resolution gave up it had lost track of *why* — the user saw `method ... is not
  found` without the offending name called out. Static, instance, unqualified and
  constructor calls now re-check the original candidate list when resolution comes up
  empty, and report E0043 naming the bad argument when that is the actual cause.

### Removed

- **Three diagnostic codes that could never fire (#427),** with their EN+JA messages:
  `E0017 CYCLIC_DELEGATION` (undecidable as designed — the delegate is chosen at
  runtime, so no static check can see the cycle), `E0024 UNIMPLEMENTED_FEATURE`
  (leftover) and `E0056 ENUM_CONSTANT_ARGS_UNSUPPORTED` (obsolete since data-carrying
  enums shipped). The coverage spec's dead-code registry is now empty and stays that
  way by test.

### Added

- **A drift guard for the effect table (#429).** `onion.Residue` shipped in v0.10.0 and
  was never added to `effect-table.txt`; the omission was latent only because it is a
  marker interface with no methods, but any unlisted stdlib class resolves to
  `Effect.Unknown` — which would make a future class silently require
  `requires { unknown }` with no indication that a missing table row, not unanalyzable
  code, was the cause. Added the entry, plus `EffectTableStdlibCoverageSpec`: every
  `onion.*` class must have an entry, no entry may name a class that is gone, and the
  whole table must parse.
- **A drift guard for the quality bar (#428).** `QualityBarSpec` measures the
  measurable rows of `docs/quality-bar.md` against the tree — sample count,
  large-program count *and names*, guide parity, diagnostic-code count — and checks the
  JA copy agrees. The file had drifted three times in three days because every figure
  was transcribed by hand; the spec caught two more stale figures on its first run.

## [0.10.2] - 2026-07-27

### Documentation

- **Five `run/` examples (`Primes.on`, `LogSummary.on`, `DeptReport.on`, `WordStats.on`,
  `AdtExpr.on`) were undocumented.** They existed in the repository but had no entry
  in the Examples Overview index, and no regression coverage confirmed they still ran.
  Added them to `docs/examples/overview.md`'s Example Index table and to
  `RunSamplesSpec` so drift between the docs and the scripts is caught automatically.

- **Five more `run/` examples (`SetOperations.on`, `PrimitivePredicate.on`,
  `PrimitiveFunctionalInterfaces.on`, `SortWithPrimitiveComparator.on`,
  `CollectionUtilities.on`) had the same gap.** Same fix: added to the Examples
  Overview index and to `RunSamplesSpec`, asserting each script's actual return
  value.

### Fixed

- **Semicolon-separated ADT enum case clauses didn't parse (#415).** The single-line
  form `enum Shape { case Circle(radius: Double); case Square(side: Double); ... }` is
  CLAUDE.md's own documented form for a Scala-3-style `case` enum, but the grammar only
  accepted a newline between `case` clauses. `;` is now accepted as a case-clause
  separator, matching how it already works as a statement separator elsewhere in the
  grammar. (Homogeneous, non-`case` enums are unaffected — they were never documented
  to accept `;` between constants and still don't.)

### Documentation

- **Tail call optimization docs (EN+JA) described a stale, pre-transformation state.**
  Both `docs/compiler/tail-call-optimization.md` and its `ja` counterpart said
  transformation was "planned" and showed a `--verbose` sample line that never
  appears in the source; `TailCallOptimization.scala` has performed the full
  `while(true)` rewrite (not just detection) for some time. Docs now describe the
  actual pipeline, the private/static/final eligibility rule, the `@TailRecursive`
  exclusion for mutual recursion, real trace output, and current file paths.

## [0.10.1] - 2026-07-27

A quality release: the diagnostic surface is now covered code by code, and the sweep
that built that coverage found and fixed three real defects.

### Fixed

- **Extending a final class crashed instead of erroring.** `class A extends
  java.lang.String` escaped the type checker entirely; bytecode was generated and the
  JVM rejected it at class-loading time (`IncompatibleClassChangeError` inside the
  law-check phase). It is now a proper **E0018** at the supertype reference, and the
  crash is pinned in the crash corpus (`030-final-superclass.on`).
- **E0053/E0054 rendered as `Unknown error: <NAME>`.** Cyclic and duplicate type
  aliases were reported through the normal reporter path but never wired to their
  message definitions, so users saw the internal enum name instead of the message
  (which existed, in both languages, all along). Both now render; the coverage spec
  asserts `Unknown error` never comes back.
- **Wrong type-argument arity was accepted in nullable annotations (#413).**
  `val b: Box[String, String]? = null` compiled silently while the same arity failed
  on `new` — the validator did not look through the nullable wrapper. Both now report
  **E0031**.

### Added

- **Per-code diagnostic coverage** (continuing #407-#412, which covered E0036, E0016,
  E0038, E0068, E0004 and E0007): `SemanticErrorCodeCoverageSpec` adds one trigger per
  previously-unasserted reachable code — 27 more codes, from E0008 to E0076 — plus a
  drift guard that scans the sources and fails when a code's report-site status
  changes: the three genuinely dead codes (E0017, E0024, E0056) are registered as
  dead, and a code losing its last report site is flagged instead of rotting silently.
  E0043 is documented as shadowed by candidate filtering; E0075 as reachable only
  through an environment failure.

### Documentation

- `docs/quality-bar.md` (EN+JA) re-measured: 2744 tests, 64/64 samples, 7 large
  programs, 81 diagnostic codes.
- `CLAUDE.md` catches up with v0.10: the narrowed default static imports (bare
  `readText`/`get`/`now`/`exit` no longer resolve), `tool`/`requires`/`--contract`/
  `--plan`, lossless `config` shapes and the lens, user-written `Shape` instances with
  the E0080 law gate, and top-level `example`.

## [0.10.0] - 2026-07-26

- **`Shape` opened to user-written instances, with a law gate (#364).** A class
  conforming `onion.Shape[T]` now brings a format the compiler has never heard of —
  the demo is a fixed-width, COBOL-style record layout (`run/FixedWidthDemo.on`) —
  and inherits the whole combinator vocabulary (`eachLine`, `sepBy`, `xmap`,
  `orElse`) and the `Outcome`/`Defect` failure story. The deal that keeps the claim
  honest: a derived shape's laws hold by construction, a user-written one must
  *assert* them, so a concrete `Shape` implementation compiles only when its file
  states a machine-checked `law` or `example` — new **E0080** otherwise. Top-level
  `example [name] { expr }` is new too (the vehicle for those laws): it lowers to
  the same build-time check a record example is, and a false claim is E0065.

- **The lens: edit parsed data and write it back intact (#363).** `Lossless` carries
  its shape and becomes a lens — `edit { v => v.copy(port = 9090) }` focuses an
  update, `render()` reassembles the text through the residue — and
  `file(path).readLossless(shape)` supplies the byte-faithful read (`readText` was
  never byte-faithful: it rebuilds lines through the platform separator and drops a
  trailing newline, either of which would silently break L2). Editing one field
  changes exactly one line: comments, spacing, key order, unknown keys, other values'
  spellings, trailing-newline presence and CRLF terminators all survive, pinned by a
  corpus spec and by `run/ConfigEditDemo.on`, which runs the edit as a tool — so
  `--plan` shows the read and the write before anything happens, and the demo's own
  `example` clauses machine-check L2 and the one-slot-edit law at build time.

- **Lossless shapes and residue (#362).** L1 (`parse(print(v)) == Ok(v)`) is every
  printing shape's law; L2 (`print(parse(t)) == t`) is false in general — `"007"`
  prints back as `"7"` — and a shape satisfying it is *lossless*. `Shape` gains
  `isLossless` / `parseLossless` / `printLossless` (a lossy shape refuses instead of
  pretending with an empty residue), plus the `Lossless<T>` value+`Residue` pair. The
  first lossless implementation is **`shape name = config`** over commented
  `key = value` documents: comments, blank lines, key order, spacing, unknown keys and
  every value's original spelling ride in the residue, so an unedited document
  reproduces byte for byte, an edit re-renders only its own value slot, and an
  unchanged `007` stays `007`. L2 is machine-checkable as an `example` clause on the
  record (a false claim is E0065 at build time). Guide section EN+JA.

- **`run/ToolDemo.on` — the "Contracts and capabilities" demo (#361).** One `tool`
  declaration, four artifacts: CLI, `--help`, `--contract`, `--plan`. The demo digests
  an access log through a `shape` boundary (bad lines become positioned defects, never
  silence) inside a capability boundary (`requires { read(src), write(out), console }`
  — checked, so deleting `write(out)` stops the build with E0077 at the
  `Files::writeText` call). The spec runs the whole agent sequence: read the contract,
  `--plan` and prove nothing was written, then execute and check the report. New
  labeled examples page EN+JA (`docs/examples/tools.md`) with the three-command
  transcript.

- **BREAKING: the default static import set is narrowed to pure classes (#360).**
  `java.lang.System`, `java.lang.Runtime`, `onion.Files`, `onion.Http` and
  `onion.DateTime` are no longer imported into every file, so `readText(p)`,
  `get(url)`, `now()`, `getenv(...)` and `exit(...)` stop resolving bare — an effectful
  line now looks effectful, which is what makes `requires` clauses readable rather
  than mysterious. Qualified calls (`Files::readText`) keep working with no import, as
  before. `onion.IO` stays as the one pragmatic exception: a bare `println` is not
  what anyone is guarding against. Migration: qualify the call, or use the new
  explicit prelude import — `import { onion.Files::* }` brings back a whole class's
  static members, `import { java.lang.System::exit }` a single one (`Class::*` in an
  import clause is new in this release). No `run/` sample or compiled doc example
  needed migration.

- **`--plan`: a trustworthy dry run derived from the checked effect set (#359).**
  `onion tool.on <args> --plan` parses the arguments exactly as a real run would, then
  prints what that run *would* do — each declared capability instantiated with the
  bound argument value (`write   dst = /backup/access.log`), defaults shown from the
  contract — and exits without performing any effect. Ambient effects print bare; an
  operand the analysis cannot tie down prints as not statically known rather than
  guessed; and a body carrying `unknown` says so out loud, marking the plan a lower
  bound — a plan that quietly omits what it could not characterize would be worse than
  no plan. Verified by specs that assert the planned write never happens.

- **docs(reference): document E0077/E0078/E0079, and re-sync the quality-bar baseline.**
  The capability-checking pass (#357) added three diagnostic codes but
  `docs/reference/error-codes.md` (EN+JA) still stopped at `E0076`, leaving the tool
  boundary's own errors undocumented in the one place meant to explain every code. Also
  re-measured `docs/quality-bar.md` (EN+JA): the effect-table / tool-capability /
  tool-contracts work (#356-#358) had pushed the real numbers to 2644 tests (was 2590),
  15/15 guides (was 14/14, `docs/guide/tools.md` shipped with #357), and 80 diagnostic
  codes (was 77).

- **Machine-readable tool contracts, and a CLI derived from them (#358).** A script
  whose top level declares tools (and has no `main` of its own) is now a command-line
  program: the compiler builds a JSON contract from the declarations — parameters with
  types, roles and defaults, the return type, the checked capabilities — and a
  synthesized `main` hands argv plus that contract to the new `onion.ToolCli`.
  `--contract` prints the JSON verbatim (what an agent reads); `--help` describes every
  argument with its type and default and shows the capability line; parsing, typed
  conversion and error messages are all derived from the same contract, and failures
  are exit codes — no `System.exit` anywhere on the tool path, which is what makes the
  whole surface testable in-process. Several tools in one script dispatch
  subcommand-style. Also fixed: `--help` on the `def main` rest-collector path used to
  be silently consumed as a positional; `Cli.requireArgs` now recognizes it.

- **`tool` declarations with checked capabilities (#357).** A tool is a function with a
  boundary: `tool ingest(src: String, dst: String) requires { read(src), write(dst) }
  { ... }`. The compiler infers the body's effects (transitively, through ordinary
  functions, with no annotation burden anywhere else) and holds the declaration to
  them: an undeclared effect is **E0077**, reported at the exact call site and naming
  both the effect and the callee; a declared capability the body cannot perform is
  **E0078**; a capability outside the vocabulary or naming a non-parameter is
  **E0079**. `unknown` is a real effect — an unlisted Java call must be admitted
  explicitly (`requires { unknown }`), never assumed harmless. Effects are erased
  inside typing: a tool emits bit-identical bytecode to the equivalent function
  (pinned by `ToolErasureSpec`), so the boundary costs nothing at runtime. `tool` and
  `requires` are soft keywords. New guide chapter EN+JA.

- **Effect table, per-method effect inference, and `--effects` (#356).** The first layer
  of the capability work: every method now has a statically computed *effect set* over
  the vocabulary `read write net exec env clock rand console unknown` (empty prints as
  `pure`). Facts about existing code live out of band in a shipped resource
  (`onion/effect-table.txt`) — annotations cannot carry them here — with the whole
  effectful stdlib surface classified, a pure JDK baseline, and the JDK's known effect
  points. For methods defined in the program, the compiler joins the table's verdicts
  transitively through user calls (fixed point over recursion; constructors and the
  superclass chain included; a closure's body is charged to its creation site). An
  unlisted Java call is `unknown` — not known pure — never silently dropped, and a
  drift-guard spec fails the build if a typed-AST node exists that the effect walker
  does not handle. `onionc --effects` / `onion --effects` print every compiled method's
  set to stderr. Reference: `docs/reference/effects.md` (EN+JA).

## [0.9.0] - 2026-07-26

- **docs(guide): sync `docs/ja/guide/basic-syntax.md` with the English guide.**
  The Japanese Basic Syntax guide had fallen behind its English counterpart: it was
  missing the Character/Boolean/Null Literals subsections, the underscore digit-grouping
  note, binary/octal/byte/short numeric literal forms, the Assignment Operators and
  Increment/Decrement subsections, the entire Expressions section (arithmetic, string
  concatenation, method calls, object creation), and the entire Block Elements section —
  and its "Next Steps" links still pointed at the stdlib reference instead of the
  Variables and Types / Control Flow / Functions guides that now exist. Translated and
  added all of it, matching the existing terminology and code-example style. No test
  covers doc-file parity between languages, which is how it went stale silently.

- **docs(grammar): sync `docs/grammar.txt` with the `extends`/`conforms` rewrite.**
  The `extends`/`conforms` change (below) updated the parser, the hint messages, the
  API-doc generator and the VS Code grammar, but missed the informal EBNF sketch in
  `docs/grammar.txt`: `class_decl` and `interface_decl` still showed the old `[':' type]`
  and `['<:' type (',' type)*]` productions, describing syntax the parser no longer
  accepts. Updated both to `['extends' type]` / `['conforms' type (',' type)*]`. No test
  covers this file's prose against the grammar, which is how it went stale silently.

- **BREAKING: inheritance is spelled with words. `:` became `extends` and `<:` became
  `conforms`.** `class Dog : Animal <: Greeter` is now
  `class Dog extends Animal conforms Greeter`; the same change applies to `record`,
  `interface` and `trait` declarations. A symbol cannot be searched for, read aloud, or
  guessed at from the outside, and `:` in particular already meant three other things
  (type annotations, record components, `select` cases), so the one position where it
  meant inheritance was the odd one out. `conforms` is a **soft keyword** — recognized
  only in the supertype position — so `def conforms(..)`, a field or a local of that name
  all still parse. Old source gets a targeted hint naming the replacement rather than a
  bare list of expected tokens; `<:` is still lexed as a single token purely so that
  hint can fire. The generated API-doc signatures and the VS Code grammar follow.

- **`run/JsonYamlShapeDemo.on` — a standalone example for `shape name = json`/`yaml`.**
  The v0.8.0 named document-boundary shape had unit-test coverage (`FormatShapeSpec.scala`)
  but no `run/` sample demonstrating it end-to-end. Added one covering parsing, the
  round-trip law, a missing key, several bad fields reported at once, and a malformed
  document's line number — mirroring the existing `BrokenLogDemo.on` pattern for the
  regex-shape form.

- **docs(quality-bar): re-sync the test-suite count with develop HEAD.** Row 1 of
  `docs/quality-bar.md` / `docs/ja/quality-bar.md` still read `2579 pass`, last measured
  at commit `f6cf5e83`; `sbt -Duser.language=en test` at current HEAD reports `2590 pass /
  0 fail / 1 cancelled` (the `extends`/`conforms` rewrite's new `InheritanceKeywordSpec`
  and other tests landed since). Updated the figure and the baseline commit reference in
  both languages; no other row's ground truth had drifted.

- **docs(examples): fix a garbled "Multiple inheritance" bullet in `docs/examples/oop.md`.**
  The `extends`/`conforms` rewrite (#391) accidentally collapsed the English prose line
  to `Multiple inheritance (: JFrame, implements ActionListener)`, leaving a bare `:`
  where `extends` belonged and disagreeing with the `class Calculator extends JFrame
  conforms ActionListener` sample directly above it. The Japanese translation was
  unaffected. Fixed to `Multiple inheritance (extends JFrame, conforms ActionListener)`.

## [0.8.0] - 2026-07-25

The v0.8.0 tag was moved after the fact. As first cut it promised `eachLine` on
`http"…"` in the entry below without having implemented it, so the release documented an
API it did not contain. Rather than leave the tag pointing at a tree its own changelog
misdescribed, the tag was re-pointed and the artifacts rebuilt; the two corrections and
the compiler fix that had landed in the meantime are folded in here.

- **`http"…".eachLine(shape)` — v0.8.0 shipped only half of "the same on `http`".** The
  0.8.0 changelog entry for shape-applied resource reads promised `eachLine` on `http"…"`
  to match `file"…"`, but `HttpResource` only ever grew `read`; the line-oriented method
  didn't exist, so calling it reported `E0005` ("method applicable ... is not found").
  Added `HttpResource.eachLine(Shape[T])`, mirroring `FileResource.eachLine`: the response
  body is split and parsed per line, keeping the lines that read and the defects for the
  ones that didn't, each positioned by line number; a transport failure (bad URL, refused
  connection, ...) is a single defect rather than a thrown exception, consistent with `read`.

- **Constant narrowing now reaches ordinary method/function call arguments.** `val b: Byte = 100`
  and, since v0.7.0, `new R(..., -3)` against a `Short`/`Byte` component both narrow an
  in-range integer literal — but `takesShort(-3)` against a plain `def takesShort(x: Short)`
  still reported `E0005` ("method applicable ... is not found") and needed an explicit
  `(-3 as Short)` cast. Method overload resolution (`MethodResolutionSupport.applicable`)
  checked only boxing-aware assignability, never the `ConstantNarrowing` helper that
  constructor resolution and plain assignment already use. Added the same fallback check
  there, so a literal that fits a narrow integral parameter's range is accepted at any call
  site, not just constructors. `docs/quality-bar.md` row 5 ("known usability bugs") is
  updated to reflect that #374's underlying gap is now fully closed.

- **README rewritten around what Onion is for.** It opened with "an object-oriented and
  statically typed programming language" that "compiles into JVM class files" — a category
  shared with Kotlin, Scala and Java — while its most distinctive section sat 171 lines
  down. It now leads with the boundary, and with a runnable example whose last two lines
  are the point. Type classes, shipped since v1 and mentioned nowhere in the README, are
  there too.

- **`shape name = re"..."` on a record.** A named, first-class boundary, synthesized as a
  static method returning a `Shape[R]`. A record may carry **several** — a v1 and a v2 log
  format can coexist — which `from re"..."` structurally cannot do, since it allows one
  pattern and bolts fixed-name statics on. The regex checks are the same ones `from` gets
  (**E0059** malformed, **E0060** group/component mismatch). What it adds: `parse` returns
  an `Outcome`, so a non-match and a broken field are distinguishable (`from` returns
  `null` for both) and every defect carries a position; every bad field is reported at
  once; `eachLine` keeps the lines it could not read instead of dropping them; and
  `canPrint()` answers whether the pattern is invertible rather than the printing method
  silently not existing. `from re"..."` is unchanged and still works.

- **`shape name = json` / `yaml`.** The same named boundary over a structured document,
  with the component names as document keys. This is what `derive!(Json, Yaml)` was
  reaching for: that form bolts fixed-name statics on, so a record gets one shape per
  format and no name for it, and it reports failure as `null` — including the case where a
  missing `String` key produced a *successfully constructed* record with a `null`
  non-nullable field. A shape reports the missing key, reports every missing key at once,
  reports a wrongly-typed value rather than coercing it, and gives a malformed document the
  line it failed on (the position `Json.JsonParseException` carries and the `derive!` path
  discarded). An unrecognised format name is **E0076**. `derive!` is unchanged.

- **`file"…".read(shape)` / `.eachLine(shape)`, and the same on `http"…"`.** The resource
  literals exposed a fixed menu (`text`, `lines`, `json`, `csv`, `csvRows`) where the parse
  step was chosen by which getter you called, so the set of things a resource could be read
  as was closed to users. Applying a shape opens it, and carries the path or URL into every
  defect so a failure says which resource. An unreadable file or a failed request is a
  defect rather than an exception. (`read`, not `as`: `as` is the cast keyword.)

- **`onion.Cli.tryParse` reports argument errors instead of exiting.** Every failure path in
  `Cli` called `System.exit`, so a CLI error was uncatchable, unrecoverable and untestable
  in-process — the specs for that file say so and route around it, covering only the paths
  that succeed. `tryParse` is the same parse returning an `Outcome`, so the error paths are
  finally reachable; `parse` remains as the thin exiting wrapper the generated entry point
  uses, with the exit now in one place rather than seven.

## [0.7.0] - 2026-07-25

- **New: `run/BrokenLogDemo.on` — the "broken log" story, runnable (partial #355).** A
  concrete demo exercising `onion.Shape`, `onion.Shapes::regex` and `onion.Outcome`
  end to end: an access-log shape reads six lines, two of them malformed, and
  `Outcome::values`/`Outcome::defects` split the four good rows from the two reported
  defects instead of the parse either crashing on the first bad line or silently
  dropping it. Also exercises the `sepBy`, `xmap` and `orElse` combinators. This covers
  the `run/` sample half of #355; the EN/JA guide pages, doc examples and crash-corpus
  entries are still open.

- **Constant narrowing now reaches constructor arguments (#374).** `val b: Byte = 100` has always
  narrowed an in-range integer literal to `Byte`/`Short`/`Char`, but `new All("hi", -3, -4)` for a
  `Short`/`Byte`-typed constructor parameter reported `E0021` ("constructor applicable ... is not
  found") and needed an explicit `(-3 as Short)` cast. Constructor overload resolution matched
  arguments only via boxing-aware assignability, never consulting the same constant-narrowing
  rule `AssignabilitySupport` already applied to plain assignment. Extracted the literal-narrowing
  check into a shared `ConstantNarrowing` helper and applied it to `ConstructionTyping`'s
  boxing-fallback constructor resolution, both when selecting the matching constructor and when
  adapting the chosen literal argument's type.

- **`law`/`example` compile-time check failures (E0064/E0065) are now bilingual.** `LawCheckPhase`
  built these two diagnostics as hardcoded English string interpolation instead of going through
  the `errorMessage`/`errorMessage_ja` resource bundles like every other error, so a Japanese
  locale never got a translated message for a falsified law or a failed example. Added the message
  keys to both bundles and switched the phase to format through `toolbox.Message`.

- **`law`/`example` can no longer silently not run.** A law whose parameter type had no sample
  generator — an array, a `Map`, an enum, an interface, an ordinary class with two constructors —
  was skipped with no diagnostic, which made it indistinguishable from a law that held. It is now
  **E0074**, and a class that declares checks but fails to load is **E0075**. Law diagnostics also
  gained the file and line they were written on (they reported neither), and the sample count and
  RNG seed became `--law-samples`/`--law-seed` and are reported alongside every counterexample so
  the run that produced it can be repeated. The language server no longer executes laws: it
  validates on every keystroke, so it was running whatever the buffer said, in a half-typed state.
  `--no-check-laws` turns them off elsewhere.

- **Every diagnostic caret now underlines the whole token.** `Location` has carried `endLine`/
  `endColumn` since it was written and the renderer has known how to draw a range, but no production
  code ever set them, so every caret was a single `^` at the first character.

- **New: `onion.Shape` — the data boundary as a value.** A partial, potentially bidirectional
  correspondence between external text and a typed value, with the two laws kept apart: `parse ∘
  print == id` is guaranteed wherever `print` exists, while `print ∘ parse == id` is false in
  general (`"007"` is a perfectly good `Int` that prints back as `"7"`). A shape that satisfies
  both is *lossless*, which is rare and is what a lens needs. `canPrint()` says up front when a
  shape is read-only — a regex with a `\s+` separator has no unique rendering — instead of the
  printing method simply never appearing. Component failures accumulate: reading `"abc,def"` as
  two `Int`s reports two defects, not the first.

  Combinators: `eachLine` keeps the rows it could read *and* the reasons the rest could not, each
  positioned on its line — a thousand-line log with five bad lines gives 995 values and 5 defects,
  where `parseAll` today returns 995 values and no trace the other five existed. `lines` is the
  all-or-nothing form, `sepBy` repeats with a literal separator, `xmap` transports a shape along an
  isomorphism (both directions, so `print` survives), and `orElse` reports both spellings' defects
  when neither reads. Nothing in the language lowers to these yet; that is the next step.

- **New: `onion.Origin`, `onion.Outcome`, `onion.Defect`.** Where a value came from, and the result
  of trying to read one. `Outcome` is not `Result[T, List[Defect]]` because its `zip` *accumulates*:
  a record with three malformed fields reports three defects in one pass, where a monadic `bind`
  would report only the first. Nothing produces them yet; they are the vocabulary the shape work
  will speak.

- **One table for the scalar types a boundary derivation can read.** The same eight types were
  enumerated in five places (`cliKindOf`, `convertCliValue`, `convertCapturedValue`, `jsonGetterOf`,
  and `onion.Cli`'s helpers) plus a sixth type-level copy in typing, so adding one meant five edits
  in two languages that had to agree, with nothing checking that they did. `E0061`/`E0062` now list
  the supported types from that table rather than from a hard-coded sentence that could go stale.

- **A `Boolean` field read from text is now validated (breaking).** `java.lang.Boolean.parseBoolean`
  never fails: it maps everything that is not `"true"` to `false`. Both boundary paths used it, so
  `record Flag(on: Boolean) from re"(\w+)"` turned `"maybe"` into a valid-looking `false` instead of
  a parse failure, and `--loud=maybe` was silently `false` while `--count=maybe` exited with an error.
  Both now reject anything that is not `true`/`false` (in any case). This is the one failure mode a
  parser must never have — turning malformed input into a plausible value.

## [0.6.0] - 2026-07-25

- **Fixed wrong `Http` method names in the stdlib docs (en/ja).** `docs/reference/stdlib.md`
  documented `Http::urlEncode`/`Http::urlDecode`, but the implemented (and tested) names are
  `encodeUrl`/`decodeUrl` — following the doc raised a "method not found" error. Corrected both
  names, documented the previously-missing `Http::buildUrl`, added `Http` to the "Modules at a
  glance" summary table (English and Japanese), and added the Japanese `Http` module section that
  was missing entirely. The header-argument example was also wrong — it showed a
  list literal against an overload that only took a `String[]` — which is what prompted the
  List-over-arrays change below; the header parameter now genuinely takes the documented list form.

- **The standard library takes and returns `List`, not arrays (breaking).** A user following the docs
  had to know, per method, whether a result was a `List` (use `.size`, index with `[]`) or an array
  (use `.length`) — and had to build `new String[]{...}` to pass HTTP headers or query parameters,
  because those overloads did not accept the `["a", "b"]` literal that looks like it should work.
  Converted: `Strings::split`/`splitRegex`/`lines`/`words`/`join`, all of `Regex`'s
  `matchGroups`/`findAll`/`groups`/`groupsAll`/`split` (both String- and Pattern-pattern forms),
  `Files::readLines`/`writeLines`/`listFiles`/`joinPath`, `Http`'s header and query-parameter
  arguments plus `Response.headers`, every `Rand::choice`/`shuffle`/`sample` (the per-primitive
  array overloads are gone — one generic `List` signature covers them, since a primitive boxes into
  the element type), and `Future::all`, which now completes with a `List[Object]`.
  Arrays remain exactly where the JVM requires them: `main(args: String[])`, `byte[]` binary I/O
  (`Files::readBytes`/`writeBytes`), argv-consuming `Args`/`Cli`, and varargs constructors like
  `Colls::listOf(...)` — the cases the language calls Java, not the cases a user writes.
  `Colls::toList(args)` crosses from an array into list-land.

## [0.5.0] - 2026-07-25

- **A throw-only lambda no longer pins a type argument to `Object` (#314).**
  `val f = Future::async(() -> { throw ... })` inferred `Future[Object]`, so `val b: Int = f.await()`
  was rejected and the target had to be annotated. A closure that never completes normally now keeps
  a bottom return in its static type, so the call infers `Future[Nothing]` and its result is
  assignable anywhere. Bottom is still not a usable JVM return type — the synthesized closure method
  continues to return `Object` — so the widening moved to exactly that one place instead of leaking
  into inference. Two codegen paths needed to follow: assigning a bottom-typed (erased) result to a
  primitive now inserts the unboxing the verifier expects, and discarding such a value as a statement
  pops the reference the call actually leaves on the stack, which a bottom type previously skipped
  because it only ever came from `throw`/`return`/`break`/`continue`.

- **One broken declaration now reports one error (#333).** `val bad = xs.noSuchMethod()` reported the
  real error *plus* "local variable bad is not found" for every later use of `bad` — misleading,
  because `bad` is declared and only its type is unknown, and loud enough to bury the root cause in a
  real file. A declaration whose initializer fails to type is remembered, and later references to it
  stay silent. Binding the name at a placeholder type was tried and rejected: it traded this noise
  for wrong-type noise on the placeholder (`Object.map() is not found`, suggesting `wait`), which is
  worse for the common case of using the variable as a receiver. A genuinely undeclared name still
  errors, and each distinct one is still reported.

- **Generic ADT enums (#311).** `enum Opt[T] { case Some(value: T); case Nothing }` was a syntax
  error — `enum` accepted no type parameters — which kept the natural killer app for ADT enums
  (`Option[T]`, `Result[T, E]`) out of reach. Type parameters now flow onto the generated sealed
  interface and every case record, including each case's implemented supertype (`Opt[T]`, not a raw
  `Opt`). A *homogeneous* enum still cannot take them, and says why: it compiles to a
  `java.lang.Enum`, which the JVM forbids from being generic.
- **A type pattern recovers the scrutinee's type argument.** Matching `Some` out of an `Opt[String]`
  now binds `Some[String]`, so `s.value()` is a `String` rather than the bare type variable `T`. This
  applies to any parameterized sealed hierarchy, not just enums, so the hand-written `sealed
  interface Box[T]` + `record Full[T](..) <: Box[T]` form gets it too. Exhaustiveness checking works
  over generic hierarchies as well — a parameterized scrutinee previously skipped the sealed check
  entirely, so a `select` missing a case compiled and returned null at runtime.


- **A type pattern nests inside a destructuring pattern (#299).** `case Add(l, n is Num)` was a
  syntax error, even though a type pattern worked at the top level of a `case` and record patterns
  already nested — so `is` was the one pattern form that could not nest. It now can, and the nested
  binding is usable at the *narrowed* type (`n.v()` resolves because `n` is a `Num`, not the declared
  `E`). This also covers the case that had no clean workaround: narrowing a component whose declared
  type is not a record (`case Wrap(s is String)`), which a nested constructor pattern cannot express.

- **A not-found member now points at the member that exists.** Three everyday mistakes produced
  errors the compiler already had the answer to. `xs.length` on a `List` reported "field
  List[Int].length is not found" with no suggestion at all, because only *fields* were considered as
  candidates and a `List` has none — the fix, `size`, is a method. Field lookup now also offers
  no-argument methods (reachable through the same paren-less syntax), and `length`/`size`/`count` are
  treated as known cross-language aliases, since they are six edit distances apart and name
  similarity could never connect them. `p.name()` where `name` is a field (a natural mix-up with
  record component accessors, which really are methods) now says so and points at the parentheses
  instead of failing silently. An alias is only ever suggested when the receiver actually declares it.

- **`foreach` over a `Map` explains the `(k, v)` form (E0073).** `foreach k: String in someMap` used
  to fail with "method applicable for Map[String, Int].iterator() is not found" — leaking the
  desugaring by naming a method the user never wrote, with no hint that `foreach (k, v) in ...`
  is the form they wanted. It is now a dedicated diagnostic naming that form and the
  `.keySet()` / `.values()` / `.entrySet()` alternatives. The `(k, v)` form itself is desugared by
  the parser into an `entrySet()` walk, so only the bare form reaches this check.

- **A generic constructor infers its type arguments from the constructor arguments (#305).**
  Generic *methods* already inferred from their arguments (`id("hi")` binds `T = String`), and a
  constructor already inferred from an expected type (`val b: Box[String] = new Box("x")`), but
  `val b = new Box("hi")` — where the argument alone determines `T` — was rejected as a raw type
  (E0066), so everyday generic code needed a redundant `new Box[String]("hi")` or an annotated
  target. It is now inferred, using the same engine that binds a generic method's type variables,
  so both paths follow identical rules (nested generics, primitive boxing into the type argument,
  and multi-parameter classes all work). Inference only applies when *every* type parameter is
  pinned by an argument and all matching constructors agree on the result; a bare generic that
  nothing determines (`new Empty()`) is still an E0066 raw type, and an inferred argument is a real
  type — `val wrong: Box[Int] = new Box("str")` is still rejected.
- **Documented nine more stdlib modules in Japanese (`docs/ja/reference/stdlib.md`).**
  The Japanese "モジュール一覧" (modules at a glance) summary table already listed
  `System`, `Files`, `Proc`, `Args`, `Iterables`, `Colls`, `Csv`, `DateTime`, and
  `Regex`, but the detailed sections for all nine were missing from the body of the
  document — present in the English `docs/reference/stdlib.md` but absent from its
  Japanese counterpart, so a Japanese reader following the table's promises hit a
  dead end. Added the same worked examples the English doc carries for each module.
- **Documented the `Config` stdlib module (en/ja).** `onion.Config` (`loadJson`/`parseJson`,
  dot-notation `get`/`getString`/`getInt`/`getLong`/`getDouble`/`getBoolean` with array
  indexing and defaults, `getEnv`/`getWithEnvOverride`, `hasPath`) has been fully implemented
  and tested since its introduction but was missing from `docs/reference/stdlib.md` and
  `docs/ja/reference/stdlib.md` entirely, including the "Modules at a glance" summary table.
  Added a `Config Module` section to both, with the same worked examples the module's own
  tests cover.
- **A duplicate top-level function definition reported the wrong diagnostic (E0012 fix).**
  Two top-level `def foo(...)` functions with the same name and parameter types reported
  "duplicated global variable definition foo." (`error.semantic.duplicatedGlobalVariable`,
  which only takes one placeholder) instead of the dedicated `duplicatedFunction` message,
  silently dropping the parameter types from the diagnostic. `SemanticErrorReporter` now maps
  `SemanticError.DUPLICATE_FUNCTION` (E0012) to `error.semantic.duplicatedFunction`, and both
  the English and Japanese message templates were fixed to show the parameter types (the
  Japanese template referenced a nonexistent `{2}` placeholder).
- **A duplicate `law`/`example` clause name on a record crashed the compiler instead of
  being rejected (internal-error fix, found by the mutation fuzzer).** Two clauses that
  mangle to the same synthesized method — e.g. two `law roundtrip(p: Point) { ... }` on the
  same record — compiled all the way to bytecode and only then blew up as a JVM
  `ClassFormatError` ("Duplicate method name ... in class file ..."), surfaced as an
  internal compiler error (`I0000`). `TypingDuplicationPass` checked a class's methods for
  duplicates but never walked a record's user-written body or its synthesized
  `from`/`derive!`/`law`/`example` methods the same way. Both are now checked, and a
  collision reports the existing `E0026` (duplicated generated method) diagnostic naming
  the actual `law`/`example` clause instead of its mangled internal name.
- **A boxed platform value unboxed to a non-null primitive now warns (W0015, #318).**
  `Json::getInt(obj, key)` (and the other `Json::get*` accessors) return a boxed Java value
  (`Integer`, `Long`, ...) that is `null` when the key is missing. Assigning or passing it where a
  non-null primitive (`Int`, `Long`, ...) is expected silently unboxed it, so a missing key threw a
  raw `NullPointerException` instead of a clear diagnostic. `processAssignable` now emits `W0015`
  at that unboxing site, mirroring the existing `W0012` null-to-non-nullable trade-off (values from
  Java stay unchecked but get a warning); use `Json::getIntOr(obj, key, default)` or check the
  boxed accessor's result for null to avoid it. Suppressible with `--Wno W0015` /
  `--Wno platform-unboxing`.
- **A convention-over-configuration project workflow: `onion new/build/run/test/clean`.**
  `onion new hello` scaffolds a manifest (`onion.toml`), `src/`, and `tests/`; `onion build`
  compiles and caches the result under `target/` (invalidated by a SHA-256 fingerprint over the
  manifest, every source, the compiler version, and the Java version); `onion run` and `onion test`
  build first and then execute the single discovered entry point or every `tests/*_test.on` file in
  sorted, sequential order; and `onion clean` removes exactly the canonical `target/` directory. All
  five commands discover the project by walking upward from the current directory, so they also work
  from a nested subdirectory. A failed rebuild never destroys the previous successful output — new
  output is staged and only promoted after every artifact is written. Existing
  `onion [options] file.on [args...]` and `onion repl` behavior is unchanged; project commands are
  reserved only when they are the exact first argument. See the
  [Project CLI guide](https://onion-lang.org/tools/project-cli/) for the full manifest, layout, and
  test conventions.
- **`install.sh --from-source` now finds the built jar (regression fix).** `assembly /
  assemblyJarName` has produced `onion-<version>.jar` for a while, never the literal `onion.jar`
  the installer globbed for, so `--from-source` always failed with "onion.jar not found after
  build" right after a successful build. The lookup now picks the most recently built
  `onion-*.jar` under `target/scala-*/`, which also avoids picking up a stale jar from an earlier
  local build.
- **`++`/`--` work on a static field and a top-level `var` (regression fix).** Post-increment/decrement
  typing had no case for a static field, so `C::count++` — and, after the top-level-var single-storage
  change, `var x = 0; x++` in a script — failed with E0028 "lvalue required". `typePostUpdate` now
  handles a static field (reads the old value, writes old+1, yields old), so both work; a `final`
  static field is still rejected.
- **`Type::member()` wins over a like-named local before E0071.** The new E0071 (":: on a local")
  fired whenever the `::` receiver was a name bound to a local, even if a real type of that name was in
  scope — so `val Helper = ...; Helper::greet()` with a class `Helper` was wrongly rejected. E0071 now
  fires only when the name does NOT also resolve to a type (probed with reporting suppressed), so a
  genuine static call wins; the pure-typo case (`s::length()` with only a local `s`) still gets E0071.

- **An inferred lambda parameter binds like an explicit one over a generic SAM (#306).** For
  `apply(f: Function1[Int, Long], x: Int)`, `apply((n) -> (n as Long), 5)` failed with `Long expected ...
  Int used` (caret on `n`) while the explicit `(n: Int) -> (n as Long)` worked. A generic SAM erases its
  parameter to the boxed wrapper (`Integer`), and the inferred parameter bound as that `Integer` while the
  explicit form binds primitive `Int`, so `n as Long` on an `Integer` was rejected. The inferred path now
  unboxes a boxed-primitive SAM parameter slot to the primitive, so both forms bind identically; reference
  parameters are untouched.

- **The LUB of a primitive branch and a reference branch boxes to a common supertype (#308).** `if b { 1 }
  else { "s" }` (declared or assigned to `Object`) reported `Int expected ... String used` — the merge
  kept the first branch's `Int` and checked `String` against it. `leastUpperBound` now boxes the
  primitive side (`Int`->`Integer`) and takes the reference LUB, so the branches merge to `Object`
  (`Integer`|`String`) — and `Integer`|`Number` merges to `Number`. Completes the LUB work of #307
  (reference siblings) and #309 (arrays) for the primitive+reference case.

- **An `abstract` method with a body is now an error (E0072) instead of silently dropping the body.**
  `abstract def foo(): Int { return 99 }` compiled and then discarded the body at codegen (an abstract
  method has no bytecode), so the code was silently ignored. It is now rejected, matching Java/Kotlin.
  Only an explicit `abstract` triggers it — an interface default method (a body with no `abstract`
  keyword) and a bodiless abstract method are unaffected.

- **`s::method()` on a local variable gets a targeted error (E0071).** Using the static-member
  operator `::` on a local — the Java/Kotlin habit of `.` for everything — reported the generic
  `type s not found` (E0003). It now says `s` is a variable, use `.` for instance calls (`s.method()`);
  `::` is only for a type's static members. A genuine static call on a type name is unaffected.

- **Rewriting-phase errors now show a `file:line:col` location.** Desugar-time diagnostics (an enum
  mixing shared params with `case` cases, an unsupported `main` signature, empty/ill-formed `do`
  notation, instance-coherence conflicts) were built with an empty source file, so they printed the
  message alone — unlike parser/semantic errors. `processBody` now fills in the unit's source file, so
  they render with a location and caret like every other diagnostic. Surfaced by the release gap-probe.

- **A mutable top-level `var` shared between top-level statements and a `def` no longer silently
  desyncs (miscompilation fix).** `var calls = 0; def bump() { calls = calls + 1 }; bump(); bump();
  println(calls)` printed `0` instead of `2`: #165's local-first promotion gave a top-level `var` two
  backing stores — later top-level statements wrote a local, defs wrote the mirrored static field — and
  they diverged. A `var` is now promoted field-only (single static field, no local binding), so
  top-level statements and defs share one storage. `val` keeps local-first promotion (its one-time
  mirror is sound), so top-level `val` smart-cast (#165) is unchanged.

- **User-definable scheme-prefixed literals.** A raw literal `prefix"..."` desugars to `prefix("...")`
  for ANY identifier prefix, not just the built-in `re`/`file`/`http` — so you can define your own by
  defining a function of that name, with no new machinery:

  ```onion
  def sql(query: String): String = "[SQL] " + query.trim()
  sql"SELECT * FROM t"      // -> sql("SELECT * FROM t")
  money"$19.99"             // -> money("$19.99")
  ```

  A general `SCHEME_STRING` lexer token routes any identifier-prefixed raw string through the same
  desugaring; `re`/`file`/`http` keep their own tokens (so `re`'s select patterns and `from re"..."`
  are unaffected). **Keyword-safe**: a reserved keyword immediately followed by a string (`return"x"`,
  `throw"x"` — no space) is re-lexed as the keyword plus a string literal, never a scheme call. A space
  (`prefix "..."`) is an identifier plus a string, and an undefined prefix is a normal method-not-found.

- **`map` on a raw List works like `filter`.** `(x as List).map { e -> ... }` reported a
  self-contradictory `Function1[T, Int] expected ... Function1[T, Int] used` — the SAM return position
  (`R`, erased to boxed `Integer`) was compared against the closure's primitive `int` result with a
  non-boxing-aware terminal check in `structurallyAssignable`. It now uses `isAssignableWithBoxing`
  (as the #317 fix did), so a raw-List map returns `[2, 3, 4]` (element `Object`), matching `filter`
  ([#319]).

- **`Colls::sortedByDescending`** — sort a list by a key selector in descending order
  (`people.sortedByDescending { p -> p.age() }`), the common "rank/top-N by field" operation that
  otherwise needed a negated key. Registered as a builtin extension, so it chains off a list.

## [0.4.5] - 2026-07-08

- **A lambda whose explicit parameter type mismatches the expected function type gives a clean error.**
  `val f: Function1[Int, Int] = (x: String) -> x` used to leak the SAM desugar internals — E0005
  `Function1[Int, Int].call(String) not found, candidate call(A)` (the synthetic `call` method and the
  raw interface's unsubstituted type variable). It now reports a clean E0000 ("Int expected ... String")
  at the parameter, matching the return-type path. The check is boxing-aware and skips unbound type
  variables, so exact matches like `(x: Int) -> x * 2` (primitive vs the boxed `Integer` slot) still
  compile ([#317]).

- **A failed trailing-closure body no longer triggers a misleading "method not found".** `xs.map { x => x.noSuchMethod() }`
  used to report both the real `Int.noSuchMethod()` error and a bogus `List[Int].map() not found` (`map`
  exists — only the lambda body was broken). The trailing-lambda resolver now checkpoints the error
  count around typing the closure body and suppresses the outer method-not-found when the body itself
  reported an error. A genuinely absent method with a well-typed closure (`xs.nonExistentMethod { x => x + 1 }`)
  still errors — the closure leaves the count unchanged, so only a broken body suppresses ([#316]).

- **F-bounded self-inheritance (CRTP) works for user-defined generics.** `class Sub : Base[Sub]` where
  `class Base[T extends Base[T]]` (and the interface form `class Item <: Cmp[Item]` where
  `interface Cmp[T extends Cmp[T]]`) used to fail with E0000, because the generic type-argument bound
  check on a class's own supertype ran before its supertype chain was established, so the self subtype
  test `Sub <: Base[Sub]` saw an empty chain. The bound checks over a type's supertype references are
  now deferred until after `setSuperClass`/`setInterfaces`, then flushed. Bound soundness is preserved
  — `class Bad : Base[String]` (a genuine bound violation) is still rejected ([#312]).

- **`Json` gains defaulted accessors** — `getStringOr`/`getIntOr`/`getLongOr`/`getDoubleOr`/`getFloatOr`/
  `getBooleanOr(obj, key, default)` return a primitive with an explicit fallback when the key is missing
  or wrong-typed, so a missing key no longer needs the boxed `getInt(...)` (which is null on a miss and
  throws `NullPointerException` when assigned to a non-null primitive).

- **`Rand::choice`/`shuffle`/`sample` accept primitive arrays.** A primitive array (`Int[]`, `Long[]`,
  `Double[]`, `Boolean[]`) is not assignment-compatible with the generic `T[]` (`Object[]`) on the JVM,
  so `Rand::shuffle(new Int[3])` used to fail with E0005. Explicit primitive-array overloads close the
  gap (`shuffle`/`sample` return a boxed list, matching the `T[]` forms).

- **Direct field access on a nullable value gives a clean null-safety error (E0070).** `x.length` where
  `x: String?` reported a misleading `INCOMPATIBLE_TYPE` ("Object expected") — the member-selection
  path had no nullable branch and fell through to a generic fallback. It now reports E0070 pointing at
  `?.`/`?:`/`!!`/a null check, matching the clean message the method-call path already gave for
  `x.length()`.

- **`arr?.length` on a nullable array no longer crashes the compiler.** The array `length`
  pseudo-field has no affiliation class, and the safe-field-access codegen dereferenced it
  unconditionally (I0000 internal error). It now emits `ARRAYLENGTH`, so `val n: Int? = a?.length`
  returns the length (or null).

- **A `main` with an unsupported parameter list is now a clear error instead of a silent no-op.**
  `def main(args: String[], flag: Boolean = false)` (a `String[]` that is neither the sole parameter
  nor the trailing rest collector) previously compiled cleanly and then did nothing — the body landed
  on an unreachable overload. It is now rejected: a `String[]` parameter must be the only parameter
  (raw argv) or the last parameter (rest collector), and every other parameter must be a
  command-line-parsed scalar.

- **A generic record can implement a generic interface parameterized by its own type variable.**
  `record Foo[T](v: T) <: Bar[T]` used to fail with E0003 (`type Bar[T] not found`) because the record's
  supertype clause was resolved outside its type-parameter scope, so `T` was unknown; a generic class
  in the same shape already worked. The record's type parameters are now in scope while its supertypes
  are resolved, so `val b: Bar[String] = new Foo[String]("hi")` dispatches correctly.

- **Enums can be algebraic data types with heterogeneous `case` cases (Scala 3 style).** An `enum`
  whose cases use the `case` keyword may give each case its OWN fields, so a sum-of-products no longer
  needs a hand-written `sealed interface` + `record`s:

  ```onion
  enum Shape {
    case Circle(radius: Double)
    case Square(side: Double)
    case Origin
  public:
    def area(): Double = select this {
      case c is Circle: c.radius() * c.radius() * 3.14
      case s is Square: s.side() * s.side()
      case o is Origin: 0.0
    }
  }
  ```

  It desugars to a `sealed interface` + one `record` per case (the enum body methods become interface
  default methods), so exhaustiveness (E0042) and pattern matching work for free. Product cases carry
  typed fields with accessors; singleton cases (`case Origin`) are zero-field records used via
  `new Origin()`. Homogeneous enums (bare constants / shared-param enums with
  `values()`/`valueOf()`/`ordinal`) are unchanged; mixing enum-level shared params with `case` cases
  is a clean error.

- **Primitive-component arrays are now correctly invariant (soundness fix, #310).** `int[]` is not a
  `long[]` on the JVM, but array assignability delegated to the general element check, which accepted
  `val x: Long[] = new Int[2]` via numeric widening — then codegen inserted a checkcast to the wrong
  array type and it failed at runtime with a `ClassCastException`. Array covariance now applies only to
  reference components (`String[] <: Object[]` still holds); a primitive element must match exactly.
  This also removes the wrong checkcast when merging incompatible primitive-array branches
  (`if b { new Int[2] } else { new Long[2] }` types as `Object` and runs).

- **The type merged from two array branches is the array of their component LUB, not `Object`** —
  `if b { new Dog[2] } else { new Cat[2] }` is now typed `Animal[]` (the array of the components'
  common ancestor), so `xs.length` and element member calls stay available after the merge; unrelated
  reference-element arrays (`String[]`/`Integer[]`) merge to `Object[]`. Completes the LUB work of #307
  (reference siblings) for array types. JVM array covariance makes the merge codegen-safe (no
  conversion); primitive-component arrays with no common array supertype still fall back to `Object`
  ([#309]).

- **Records can have method bodies.** A `record` may now carry a `{ access-section* }` body of
  methods, just like an `enum` already could — `record Fraction(num: Int, den: Int) { public: def plus(o: Fraction): Fraction = ...; static def of(...): Fraction ...; private: static def gcd(...) }`.
  Methods see the generated accessors (`num()`), operator methods back `+ - * /`, and static factories
  / private helpers work. Closes the product-type asymmetry with enums; all existing record features
  (accessors, `from re"..."`, `derive!`, `law`/`example`, `<:` interfaces) are unchanged.

- **`oniondoc` — a documentation generator.** A new javadoc/scaladoc-style tool
  (`onion.tools.doc.OnionDoc`) that reads `.on` sources, extracts `/** ... */` doc comments attached
  to types (class/interface/record) and their members, renders a markdown-like comment body to HTML
  (headings, bold/italic, inline and fenced code, lists, links, with HTML escaping), and writes a
  static site — an index plus one page per type with signatures and `@param`/`@return`/`@throws`
  tags. It documents classes, interfaces, enums (with their constants), and records — including a
  record's or enum's user-declared method body. Run: `oniondoc -d <outdir> <source.on...>`.

- **`Hash`, `Codec`, `Text`, `Stats` and `Format` helpers are callable as method chains.** Registered
  as builtin extension methods on their receiver type, so `"pw".sha256()`,
  `"x".base64Encode().base64Decode()`, `text.wrap(40)`, `nums.sum()`/`nums.average()`/`nums.median()`,
  and — via a boxed primitive receiver — `(1536L).bytes()`, `(21L).ordinal()`, `(0.756).percent(1)`
  work alongside the static forms (`"secret".base64Encode().sha256()` and `nums.sum().fixed(1)`
  compose). Builtin extensions on a primitive first parameter now register on the boxed class, the
  same way a user `extension Int { ... }` does.

- **Separate compilation of generics.** `onionc` now emits JVM generic `Signature` attributes for
  generic classes, methods, fields and constructors, so a generic type compiled in one unit is seen
  as generic from another: compile `class Container[T]` to a `.class`, then `new Container[String](x)`
  in a separately-compiled unit (with that class on `-classpath`) resolves instead of failing with
  E0030. Non-generic bytecode is unchanged (no `Signature` emitted). Non-generic separate compilation
  (classes, interfaces, records, inheritance, statics) already worked; this closes the generics gap.

- **New practical modules: `Hash`, `Codec`, `Stats`, and `Format`.** `onion.Hash` computes `md5`/
  `sha1`/`sha256`/`sha512` digests (hex); `onion.Codec` does `base64Encode`/`base64Decode`,
  `hexEncode`/`hexDecode`, and `urlEncode`/`urlDecode`; `onion.Stats` aggregates any `List` of
  numbers — `sum`/`sumInt`/`sumLong`, `average`, `min`/`max`, `median`, `variance`, `stddev` (the
  generic aggregates accept `List[Int]`/`List[Long]`/`List[Double]` alike via a `Number` bound); and
  `onion.Format` renders locale-independent human-readable output — `integer` (comma grouping),
  `number`/`fixed`/`percent`, `bytes` (1.5 KB), `duration` (1h 1m 1s), and `ordinal` (21st); and
  `onion.Text` lays out console text — `wrap` (word wrapping), `indent`/`dedent`, and `table`
  (columns aligned to their widest cell).

- **`Files` gains `ext`, `stem`, and `withExtension`** — file-extension handling
  (`Files::ext("a/b.txt")` -> `"txt"`, `Files::stem(...)` -> `"b"`, `Files::withExtension(p, "md")`),
  common in file-processing scripts (`ext` rather than `extension`, which is a keyword).

- **`DateTime` gains `diffHours`/`diffMinutes`/`diffSeconds` and `dayName`/`monthName`** —
  completing the `diffDays` duration set, and English (locale-independent) day/month names for
  display.

- **`Colls` gains `chunked`, `windowed`, `slice`, and selector aggregation** — batching
  (`items.chunked(100)`), sliding windows (`items.windowed(3)`), bounds-clamping sublists
  (`items.slice(from, to)`), and `sumBy`/`averageBy`/`maxBy`/`minBy` over a numeric selector
  (`people.maxBy { p -> p.age() }`, `orders.sumBy { o -> o.total() }`) — all chainable. `onion.Rand`
  gains `sample(list, n)` (n distinct elements drawn without replacement).

- **Richer practical standard library.** `onion.Maps` (6 → 20 methods) gains
  `keys`/`values`, `mapKeys`, key+value `filter`/`count`/`anyEntry`/`allEntries`, `forEach`,
  `toList`, `invert`, `groupBy`/`countBy`, `update`, `mergeWith`, and lazy `getOrElse`; `onion.Sets`
  (6 → 19) gains `fromList`/`toList`, `symmetricDifference`, `isSubsetOf`/`isSupersetOf`/`isDisjoint`,
  `map`/`filter`/`forEach`/`count`/`any`/`all`/`find`, with null-safe set algebra; and `onion.Strings`
  gains case helpers (`capitalize`/`capitalizeWords`), `equalsIgnoreCase`/`containsIgnoreCase`,
  `count`, `removePrefix`/`removeSuffix`, `truncate`, `center`, `ifBlank`, `words`, `chars`, and
  null-safe parsers (`toIntOrNull`/`toLongOrNull`/`toDoubleOrNull`/`toIntOr`). Result maps/sets
  preserve insertion order. `onion.Option` gains `orElseGet`/`orNull`/`orElse`/`contains`/`exists`/
  `fold`/`toList`, and `onion.Result` gains `fold`/`recover`/`recoverWith`/`orElseGet`/`orNull`/
  `exists`/`toList`. `onion.Csv` gains `stringifyWithHeader` (the inverse of `parseWithHeader`,
  closing the record round-trip) and `column`/`columnByName` extractors. `onion.IO` gains
  line-oriented helpers `readLines`/`eachLine`/`printLines`/`printAll`, plus `flush` and
  `tryReadLong`.

- **`Maps`, `Sets` and `Strings` helpers are callable as method chains.** Like the `Colls`/`Iterables`
  list pipelines, the `onion.Maps`, `onion.Sets` and `onion.Strings` helpers are registered as builtin
  extension methods on their receiver type, so you can write `m.filter { ... }.mapValues { ... }`,
  `s.union(other).map { ... }`, or `"text".trim().truncate(20, "...")` instead of only the static
  `Maps::` / `Sets::` / `Strings::` forms. A user-declared `extension` of the same name still shadows
  the builtin.

- **An overloaded builtin extension resolves to the overload that actually accepts the receiver.**
  The bidirectional (trailing-closure) extension path now checks that a candidate overload's receiver
  parameter is assignable from the receiver and picks the most specific such overload, so `set.map { ... }`
  resolves to the `Set` overload (returning a `Set`) instead of erroring against a sibling `map(List)`.
  Fixes a latent failure where `Set` values could not use the collection pipeline.

- **A no-closure extension call works on an array receiver**, so `Strings::split(...)` /
  `Strings::words(...)` results flow into the collection pipeline with `arr.toList()` (previously only
  the closure-taking array extensions like `arr.map { ... }` resolved; `arr.toList()` failed with E0005).

## [0.4.4] - 2026-07-07

- **The type of an `if`/`select`/`try` merged from two siblings is their common ancestor, not `Object`**
  — `if b { new Dog() } else { new Cat() }` is now typed `Animal` (their shared superclass; or a
  shared interface), so members declared there stay callable without an annotation ([#307]).

- **A `for` body and a select `when` guard now narrow like `if`/`while`** — two more
  condition/guard→body narrowing sites, mirroring #294/#302/#303. The for-body runs only
  when the loop condition is true, so `for var i: Int = 0; x != null && i < n; i = i + 1 { x.length() }`
  narrows `x` in the body (flow-sensitive: a var reassigned in the body clears the narrowing from
  that point on; a var reassigned in the condition or update is not narrowed). A select case body
  runs only when its `when` guard is true, so `case s when s != null: s.length()` narrows `s` (and
  the scrutinee var the guard tests). Soundness preserved: no narrowing leaks past the loop or to
  another case/else ([#304]).

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

[Unreleased]: https://github.com/onion-lang/onion/compare/v0.4.4...develop
[0.4.4]: https://github.com/onion-lang/onion/releases/tag/v0.4.4
[#307]: https://github.com/onion-lang/onion/issues/307
[#304]: https://github.com/onion-lang/onion/issues/304
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
