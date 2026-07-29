# Changelog

All notable changes to Onion are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

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
