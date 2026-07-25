# Onion v0.5 Audit (evidence-based)

Status: **audit only.** This document records what Onion v0.5 *is*, with citations
to the implementation. It proposes nothing; the design that follows from it lives in
[typed-boundaries.md](typed-boundaries.md), and the schedule lives in
[roadmap.md](roadmap.md).

Every claim below names a file and line range. Where a finding is "nothing does X",
the search that established it is given so the claim can be re-run. Line numbers are
as of `develop` @ `e4a3033c` (2026-07-25).

---

## 1. What Onion already does well

These are load-bearing and should survive any repositioning.

**A compile-time verification phase that actually runs user code.** `law` and `example`
clauses on records are desugared into static boolean methods
(`Rewriting.scala:583-596`, named `onion$$law$$<name>` / `onion$$example$$<name>`) and
executed by a real pipeline phase after bytecode generation
(`verification/LawCheckPhase.scala:32-58`), which loads the freshly compiled classes
through `OnionClassLoader` and invokes them reflectively. A falsified law fails the
build with a counterexample (`LawCheckPhase.scala:89-91`). Very few languages check
properties as part of `compile`. §4 covers what is wrong with it; the *idea* is sound
and is the natural place to machine-check a round-trip law.

**A derivation that produces both directions.** `record R(...) from re"..."` synthesizes
`parse` (`Rewriting.scala:667-670`) and — when the pattern is invertible —
`format` (`Rewriting.scala:704-727`). The invertibility analysis (`formatSegments`,
`Rewriting.scala:862-900`) is genuinely conservative: it rejects escapes, non-capturing
groups, nesting, quantified groups and bare metacharacters rather than guessing.

**Derivation reuses the type checker instead of duplicating it.** `synthesizeFromMethods`
emits an ordinary `AST.RegexPattern` with one generated binding per component
(`Rewriting.scala:644-645`), so the regex select-pattern's existing checks apply to it
for free: bad pattern → `E0059`, group-count ≠ component-count → `E0060`
(`typing/SelectExpressionTyping.scala:467-479`). "Capture groups must match components"
is enforced with no dedicated code.

**An open scheme-literal mechanism.** `re"…"` / `file"…"` / `http"…"` are not special:
the parser rewrites `prefix"raw"` into the unqualified call `prefix("raw")`
(`grammar/JJOnionParser.jj:255-261`, dispatched at `:2755-2760`). The generic
`SCHEME_STRING` token (`:1124-1139`) means **any** identifier prefix works, so a user
registers a scheme just by defining a function of that name — verified by
`src/test/scala/onion/compiler/tools/UserSchemeLiteralSpec.scala`. New boundary syntax
does not require grammar changes.

**A no-crash bar with teeth.** `MutationFuzzSpec.scala` runs a deterministic mutation
fuzzer (seed `20260610L`, 60 mutations × 59 `run/*.on` seeds = 3,540 mutants) and treats
both escaped `Throwable`s and `I0000` internal-error diagnostics as failures
(`:30-38`). `CompilerCrashRegressionSpec.scala` pins a 29-file crash corpus under
`src/test/resources/crash-corpus/`, named so the corpus reads as a history.

**A single choke point for assignability.** Every "does this value fit this expected
type" decision in the compiler funnels through `AssignabilitySupport.processAssignable`
(`typing/AssignabilitySupport.scala:60-190`) via the forwarder
`TypingBodyPass.processAssignable` (`typing/TypingBodyPass.scala:75-76`); argument
positions reach it through an injected function value in
`typing/CallArgumentTypingSupport.scala:13`. 38 references, one implementation. Any
future subsumption rule has exactly one place to go.

**`Result` and `Option` are complete and wired into `do` notation.** `Result.java:161-312`
and `Option.java:115-237` carry `map`/`flatMap`/`fold`/`recover`/`orElse`, plus the
`successful`/`bind` pair that `do[Result]` / `do[Option]` desugar to.

---

## 2. Four boundary mechanisms that share nothing

Onion has four ways to turn external text into typed values. They were built
independently and have no common vocabulary.

| Mechanism | Implementation | Component types |
|---|---|---|
| `record R(...) from re"..."` | `Rewriting.scala:636-730` | 8 scalars |
| `derive!(Json, Yaml)` | `Rewriting.scala:772-851` | the same 8 scalars |
| auto-CLI from `main`'s signature | `Rewriting.scala:292-436` + `src/main/java/onion/Cli.java` | the same 8 scalars |
| scheme literals (`file""` / `http""`) | `grammar/JJOnionParser.jj:2755-2760` → `src/main/java/onion/Resources.java` | untyped; fixed method menu |

### 2.1 The same eight scalars are switched on in five places

`Int, Long, Double, Float, Boolean, Short, Byte, String` — enumerated separately at:

- `Rewriting.scala:401-415` — `cliKindOf`, AST type → tag string
- `Rewriting.scala:421-436` — `convertCliValue`, tag → `onion.Cli.parseX` call
- `Rewriting.scala:438-458` — `convertCapturedValue`, tag → `java.lang.X.parseX` call
- `Rewriting.scala:733-742` — `jsonGetterOf`, tag → `Json.getX` name
- `src/main/java/onion/Cli.java:101-157` — the `parseX(name, value)` runtime helpers

and a sixth, type-level copy of the same set in
`typing/TypingOutlinePass.scala:297-305` (`isFromDerivableType`, with
`isDataDerivableType` delegating to it at `:304-305`).

Adding one component type — `java.time.LocalDate`, say — means six edits in two
languages. This is not a missing feature; it is a missing abstraction.

### 2.2 The four mechanisms disagree about what a "kind" is

`cliKindOf` returns a `String` tag that three other functions re-match by string
equality. `Rewriting.scala:444-458` (`convertCapturedValue`, for `from re"…"`) and
`Rewriting.scala:421-436` (`convertCliValue`, for auto-CLI) deliberately generate
*different* conversion calls for the same tag: the former uses raw
`java.lang.Integer.parseInt` so a `NumberFormatException` can be caught and turned into
`null` (`Rewriting.scala:663-665`), the latter uses `onion.Cli.parseInt` so a failure
prints and exits. Two conversion policies, selected by which desugaring you are in.

---

## 3. Five failure conventions for one concept

"The external data did not match what we expected" is expressed five different ways.

| Situation | Behaviour | Citation |
|---|---|---|
| Regex does not match | returns `null` | `Rewriting.scala:660` |
| Numeric conversion fails | returns `null` — **indistinguishable from no-match** | `Rewriting.scala:663-665` |
| A line in `parseAll` is malformed | **silently dropped** | `Rewriting.scala:683-688` |
| JSON/YAML is malformed | returns `null`, discarding the parser's position | `Rewriting.scala:832-834` |
| A CLI argument is invalid | **`System.exit(1)`** | `src/main/java/onion/Cli.java:186-189` |
| A CSV row is ragged | does not fail; the row is accepted as-is | `src/main/java/onion/Csv.java:76-79` |

### 3.1 `parseAll` drops bad lines with no trace

The synthesized loop guards the accumulator with a plain null check and **no else
branch** (`Rewriting.scala:683-688` — `AST.IfExpression(..., NotEqual(__r, null), add, null)`).
The signature is `parseAll(text: String): List[R]`, so a caller cannot learn how many
lines were dropped, which ones, or why. `docs/reference/specification.md:252-253`
documents this as intended ("parses each, drops the `null`s") and
`RecordFromRegexSpec.scala:84` locks it in.

### 3.2 A missing String key silently constructs a broken record

This is the sharpest instance. For a record component of type `String`, the synthesized
`fromMap` calls `Json.getString`, which returns `null` both when the key is absent and
when it holds a non-string (`src/main/java/onion/Json.java:139-142`). That `null` is
passed straight to the record constructor, and the generated constructor performs **no
validation** (`typing/TypingOutlinePass.scala:196-217` creates fields, accessors and the
`ConstructorDefinition` with no null check).

So `R::fromJson("{}")` for a record with a `String` component returns a *successfully
constructed* record whose non-nullable field is `null` — not `null`, not an error. The
numeric case is caught only incidentally, by the unbox NPE being trapped at
`Rewriting.scala:802-804`. **There is no test covering the String case.**

### 3.3 Boolean conversion cannot fail

`convertCapturedValue` maps the `Boolean` kind to `java.lang.Boolean.parseBoolean`
(`Rewriting.scala:453`), which never throws: `"maybe"`, `"yes"` and `"1"` all become
`false`. `Cli.parseBoolean` (`Cli.java:155-157`) has the same shape, and is the only
`parseX` in that file that does not validate — `--count=maybe` exits with an error while
`--loud=maybe` is silently `false`.

---

## 4. `law` can silently not run

The verification phase is the feature most at odds with its own promise.

**A law over a non-generatable parameter type is skipped with no diagnostic.**
`LawCheckPhase.scala:80-81`:

```scala
val perParam: Array[List[AnyRef]] = paramTypes.map(t => ArgGenerator.generateValues(t, loader).orNull)
if (perParam.exists(_ == null)) return // a parameter type isn't generatable — skip this law (MVP)
```

`ArgGenerator` can generate the 8 scalars plus their boxed forms and `String`
(`verification/ArgGenerator.scala:72-82`), and "flat records" — exactly one declared
constructor, non-empty parameters, every parameter recursively generatable, depth ≤ 3
(`ArgGenerator.scala:212-274`). Arrays, `List`, `Map`, enums, interfaces, generics and
any multi-constructor class are not generatable. A law over one of them is
indistinguishable from a law that passed.

**Two more silent-skip paths.** A class that fails to load takes all of its laws and
examples with it (`LawCheckPhase.scala:44` swallows `Throwable` into `null`), and record
instantiation failures are dropped entry-by-entry (`ArgGenerator.scala:261-271` catches
five exception types), which can cascade into a whole-law skip via the rule above.

**Nothing tests any of this.** There is no `ArgGeneratorSpec` and no
`src/test/scala/onion/compiler/verification/` directory. `LawExampleSpec.scala`'s six
cases assert only `Shell.Success("ok")` vs `Shell.Failure`; no test asserts an error
code, a message, or a counterexample value.

**Sampling is unreported and unconfigurable.** `N = 40` is hard-coded twice
(`LawCheckPhase.scala:26`, `ArgGenerator.scala:18`); the seed is fixed at `42L`
(`ArgGenerator.scala:20-22`) and appears in no message. `CompilerConfig` exposes only
`checkLaws: Boolean` (`CompilerConfig.scala:27`). There is no shrinking anywhere — the
first failing tuple is reported verbatim and the loop stops (`LawCheckPhase.scala:83-98`).

**Diagnostics lose their location.** `LawCheckPhase.scala:114-115`:

```scala
private def err(code: String, cc: CompiledClass, msg: String): CompileError =
  CompileError(cc.className, null, msg, Some(code))
```

The class name is put in the `sourceFile` slot and the location is `null`, even though
`AST.LawClause.location` was captured at `Rewriting.scala:586` and discarded. In
`DiagnosticRenderer.formatError` (`diagnostics/DiagnosticRenderer.scala:64-79`) this
yields no file, no line, no caret, no source excerpt; in the LSP it lands at line 0,
column 0 (`tools/lsp/OnionTextDocumentService.scala:622-627`).

**`E0064` / `E0065` bypass i18n.** They are declared at `SemanticError.scala:132-133`
but have **no entries in `src/main/resources/errorMessage.properties`** and no
`SemanticErrorReporter` registration; all four messages are hard-coded English string
interpolations at `LawCheckPhase.scala:69,72,90,95`. They are the only codes that do this.

**Law checking is always on, including in the editor.** `checkLaws` defaults to `true`
(`CompilerConfig.scala:27`) and no CLI flag sets it (`ScriptRunner.scala:266-278` and
`CompilerFrontend.scala:189-201` construct `CompilerConfig` without it). The language
server uses the defaults (`tools/lsp/OnionTextDocumentService.scala:574`), so **the LSP
executes user code on every document validation.**

**The documented behaviour does not match.** `docs/reference/specification.md:321-323`
promises "a message showing the expression that returned `false`"; the implementation
emits only the clause's label.

---

## 5. No runtime notion of where a value came from

- `onion.compiler.Location` (`Location.scala:18-42`) is compile-time only. Its
  `endLine` / `endColumn` span fields exist and are **never set by production code** —
  `grep -rn "withSpan|endLine|endColumn" src/main/scala` matches only `Location.scala`
  itself and an unrelated `RawDoc.endLine` in `tools/doc/DocModel.scala`. The only
  producers of a spanned `Location` are tests (`src/test/scala/onion/compiler/TestSupport.scala:25`,
  `.../diagnostics/DiagnosticRendererSpec.scala:23`). Consequently `spanLength` is always
  1 and every caret rendered by `DiagnosticRenderer.underlineAt` (`:129-138`) is a single `^`.
  The LSP works around this by re-deriving an end position from the line text
  (`OnionTextDocumentService.scala:628`).
- At runtime, position information exists in exactly two places and both are discarded
  by the generated code: `Json.JsonParseException.position` (a character offset,
  `Json.java:35-46`) and `Yaml.YamlParseException.line` (`Yaml.java:35-46`), both swallowed
  by the synthesized `fromXxx`'s `catch Exception → null` (`Rewriting.scala:832-834`).
- `grep -rniE "provenance|sourceSpan|valueOrigin" src/main src/test` → **0 matches.**
  There is no type, field or convention associating a runtime value with a syntactic origin.

## 6. `Result` / `Option` exist but the boundary layer ignores them

`Result` and `Option` are complete (§1) and are the declared `do`-notation carriers, yet
`grep` over `src/main/java/onion/` shows their only consumer in the standard library is
`Future.java:281,294`. `Json`, `Yaml`, `Csv`, `Regex`, `FileResource`, `HttpResource`,
`Config`, `Cli` and every synthesized `parse` return `null`, an empty collection, or exit.

`Result` also lacks `zip` / `product` / any error-accumulating combinator, so reporting
three simultaneously-bad fields in one pass is not expressible today.

---

## 7. The effect surface, and why capabilities are harder than they look

### 7.1 The surface itself is small and already segregated

12 of 45 classes in `src/main/java/onion/` perform effects, ~110 entry points total:

| Effect | Classes |
|---|---|
| filesystem | `Files` (352 L), `FileResource` (77 L) |
| process | `Proc` (144 L) |
| network | `Http` (281 L), `HttpResource` (65 L) |
| filesystem + env | `Config` (155 L) |
| console | `IO` (240 L), `Cli` (209 L) |
| clock | `DateTime` (2 effectful of ~40 methods: `now` :24, `nowString` :31,:38), `Timing` |
| randomness | `Rand` (every public method) |

The remaining 33 (`Json`, `Yaml`, `Csv`, `Regex`, `Strings`, `Colls`, `Maps`, `Sets`,
`OnionMath`, `Stats`, `Text`, `Format`, `Codec`, `Hash`, `Result`, `Option`, …) are pure.
`Resources.java:17-35` is already shaped like a capability factory: `file(String)` →
`FileResource`, `http(String)` → `HttpResource`, `re(String)` → `Pattern`.

That is a good starting position. The two problems are below.

### 7.2 Five effectful classes are ambiently imported

`src/main/resources/onion/default-static-imports.txt`, loaded by
`DefaultStaticImports.scala:15-34`, static-imports into **every** source file:

```
java.lang.System, java.lang.Runtime, java.lang.Math,
onion.IO, onion.Strings, onion.Files, onion.Iterables, onion.Regex,
onion.DateTime, onion.Http, onion.Resources, onion.Csv
```

So `println(...)`, `readText(...)`, `get(url)`, `now()` and `System::exit` are callable
unqualified, from anywhere, and are **indistinguishable at the call site from pure
calls**. Any capability story has to confront this: tracking the effect is possible, but
making it *visible to a reader* is not, while the most common effectful call in the
language is a bare `println`.

On top of that, `TypingHeaderPass.scala:42-73` implicitly on-demand-imports
`onion.*, java.lang.*, java.io.*, java.util.*, javax.swing.*, java.awt.event.*` into every
unit, and only single-class imports are validated (`:67-68`).

### 7.3 Java interop is completely unrestricted

`ClassTable.loadOrNull` (`ClassTable.scala:57-77`) tries the classpath and falls back to
`Class.forName` with no name filter, package allowlist, or module check. The parent-first
list in `ExplicitClasspathClassLoader.scala:50-51` (`java.`, `javax.`, `jdk.`, `sun.`,
`com.sun.`) is about *resolution order*, not permission.
`grep -rniE "whitelist|blacklist|allowlist|forbidden|restricted" src/main/scala` returns
one unrelated comment (`TypingOutlinePass.scala:40`).

### 7.4 The existing annotation mechanism cannot carry effects

- Storage is `MethodDefinition.annotations: Set[String]` (`TypedAST.scala:735`) — bare
  names, no arguments, no positions.
- **The `Method` trait itself has no `annotations` member** (`TypedAST.scala:1371-1395`
  declares `affiliation`, `arguments`, `returnType`, `typeParameters`, `isVararg`,
  `argumentsWithDefaults` — and nothing else). `AsmRefs.scala` and `ReflectionRefs.scala`
  never read annotations, so **no annotation on a Java method reaches the type checker.**
- Nothing is emitted to bytecode: `grep -rn "visitAnnotation" src/main/scala` → **0 matches.**
- `AST.Annotation` (`AST.scala:300`) attaches only to `FunctionDeclaration` (`:268`) and
  `MethodDeclaration` (`:302`) — not to classes, fields or parameters.
- There are exactly two readers, both testing one string:
  `optimization/TailCallOptimization.scala:93` and
  `optimization/MutualRecursionOptimization.scala:133`. The latter **drops annotations
  entirely** when it rebuilds a method (`:321`, `annotations = Set.empty`).

Effect information therefore cannot ride on annotations. It needs an out-of-band table
plus a new member on the `Method` trait.

---

## 8. Positioning is internally inconsistent

- **README contradicts itself.** `README.md:3-4` sells "an object-oriented and statically
  typed programming language… compiles into JVM class files", while `README.md:171-205`
  ("Shape-First Scripting") sells scheme literals, `record … from re"…"`, `|>` and an
  auto-derived CLI. The opening paragraph and the most distinctive section describe two
  different languages.
- **Type classes are invisible from the front door.**
  `grep -nE "trait|instance|type class" README.md` → 0 matches, although
  `docs/design/type-classes.md:1` records them as "implemented (v1)" and
  `docs/guide/type-classes.md` exists in both languages.
- **EN/JA feature lists have drifted.** `docs/index.md:9-21` lists 13 key features;
  `docs/ja/index.md` lists 11. The two missing from the Japanese list are
  **"Bidirectional Records" and "Compile-Time Specs"** — the two most distinctive claims
  in the set.
- **Neither guide index links `guide/type-classes.md`**, though the file exists in
  `docs/guide/` and `docs/ja/guide/` and both are in `mkdocs.yml`.
- **There is no central design document.** `docs/design/` contained exactly one file
  before this one (`type-classes.md`), and `docs/index.md` does not link it. The three
  design-ish documents that are linked (`docs/GENERICS_DESIGN.md`,
  `docs/parser-refactoring.md`, `docs/quality-bar.md`) live outside `docs/design/`.
- **`CHANGELOG.md` contains no positioning statement**, so there is no third source to
  arbitrate the README's self-contradiction.

---

## 9. Structural debt that constrains the design space

**There is no IR between the typed AST and code generation.** The pipeline
(`pipeline/PipelineRunner.scala:15-41`) runs
parsing → rewriting → typing → TCO → mutual-recursion → bytecode → law-check, and
`backend/BytecodeGenerationPhase.scala:11-12` takes `Seq[TypedAST.ClassDefinition]`
straight into ASM emission (`backend/asm/`, 2,905 lines across 13 files). `TypedAST` is
simultaneously the type checker's output, the optimizers' mutable working representation
(`var block` at `TypedAST.scala:731`, TCO metadata setters at `:756-765`) and the codegen
input. **There is nowhere to put a lowered, effect-erased form.** Any new type-level
concept must either be fully erased inside `Typing`, or be understood by the ASM backend.

**`Rewriting.scala` is 1,416 lines carrying ten unrelated desugarings:** type-class
dictionary passing (`:129`), ADT enums (`:235`), auto-CLI (`:309`), `instance` lowering
(`:501`), `Trait[T]::` lowering (`:539`), `from re"…"` (`:636`), `derive!` (`:772`),
`law`/`example` (`:584`,`:591`), do-notation (`:1154`), and return-type hint propagation
(`:917`). Its header comment (`:23-77`) still documents only do-notation — stale by eight
features. `rewriteExpression` (`:999-1152`) is a ~90-case identity traversal that must be
extended by hand for every new AST node.

**Scheme-literal desugaring is split across two phases** — the parser rewrites the token
(`grammar/JJOnionParser.jj:255-261`) and `Rewriting` handles everything else.

**The typing package is 71 files / 13,943 lines**, of which call resolution alone is
spread over 13 files (`MethodCallTyping`, `MethodResolution`, `MethodLookupSupport`,
`MethodTargetTypingSupport`, `MethodInvocationBuilderSupport`, `InstanceMethodCallSupport`,
`StaticMethodCallSupport`, `UnqualifiedMethodCallSupport`, `StaticImportMethodCallSupport`,
`CallableValueCallSupport`, and three reporting/fallback helpers). Largest single files:
`TypingOutlinePass` 754, `BlockElementLowering` 732, `SelectExpressionTyping` 699,
`ConstructionTyping` 658, `ClosureTyping` 615.

**The one choke point is already overloaded.** `AssignabilitySupport.processAssignable`
(`AssignabilitySupport.scala:60-190`) is the right insertion point for any new subsumption
rule (§1), but that single ~130-line method already handles empty-collection retyping
(`:65`), bottom types (`:67-81`), constant narrowing (`:86-90`), boxing (`:92-102`), the
null-to-non-nullable warning (`:104-109`), primitive/null rejection (`:111-115`), platform
unboxing (`:117-133`) and structural type-variable assignability (`:138-179`).

---

## 10. The bar a new language feature must clear

From `docs/quality-bar.md` and the test infrastructure:

- an entry in **both** `docs/guide/` and `docs/ja/guide/` (parity is a measured row)
- a `**Xxx.on**`-labeled example under `docs/examples/` **and** `docs/ja/examples/`.
  Only labeled blocks are compile-checked, and blocks containing `...` are skipped
  (`src/test/scala/onion/compiler/tools/DocExamplesCompileSpec.scala:33,47`). The scan
  covers `docs/examples` and `docs/ja/examples` only (`:36`) — **`docs/design/` is not
  compile-checked**, which is why this document and `typed-boundaries.md` may show
  proposed syntax.
- usage inside a ≥100-line `run/*.on` program, which then also becomes a fuzz seed
  (+60 mutants)
- a `src/test/resources/crash-corpus/` entry per crash found during development
  (29 today; the type-class work added 3)
- dedicated `E00xx` codes with **both** EN and JA messages
- no regression in the ~2,450-test suite, which runs serially
  (`build.sbt:183`, `Test / parallelExecution := false`)

**The quality-bar baseline is stale.** `docs/quality-bar.md:7-12` records "1193 pass / 0
fail" and "36 / 36 compile" as of 2026-06-26; the suite is now ~2,450 tests and there are
59 files in `run/`. Row 3's list of large programs and row 7's "~24 codes" are likewise
behind.

**An unwired asset.** `src/main/scala/onion/tools/readiness/docs/`
(`MarkdownExampleExtractor.scala`, 347 lines, plus `DocumentationDirective.scala` and
`DocumentationExample.scala`) implements a richer doc-example framework supporting
`<!-- onion-example: compile | run | reject code=E0059 | fragment reason="…" -->` and
`<!-- onion-output: stdout -->` directives. Its specs exercise it against inline strings
only; **it is not connected to the documentation tree.** It is available as a foundation
for stricter doc verification.

---

## Summary

The recurring pattern is not missing features — it is **four parallel implementations of
one idea, each choosing its own failure convention, none of which can say where anything
came from.** The eight-scalar list is written out five times (§2.1); the same conceptual
failure is reported five ways (§3); the compile-time verification that would catch the
resulting bugs can silently not run (§4); and there is no runtime type that carries a
position, so even the failures that *are* reported cannot point at their cause (§5).

The design in [typed-boundaries.md](typed-boundaries.md) addresses this by naming the
missing abstraction rather than adding a fifth mechanism.
