# Onion Project CLI v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:executing-plans to implement this plan task-by-task. Follow
> superpowers:test-driven-development for every behavior change. Steps use
> checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a convention-over-configuration Onion project workflow whose
complete first-use journey is `onion new → build/run → test → clean`, while
preserving script and REPL behavior.

**Architecture:** `onion.tools.OnionCli` dispatches to small project services
under `onion.tools.project` or delegates to the existing script runner and
REPL. Project compilation uses `OnionCompiler.compileDetailed`, writes complete
output through a staging transaction, and validates a content-addressed
build-state file. Runtime and tests select source-level entry metadata instead
of searching compiler-synthesized JVM mains.

**Tech Stack:** Scala 3.3.7, Java NIO, TOML via
`org.tomlj:tomlj:1.1.1`, existing `onion.Json`, ScalaTest 3.2.19.

## Global Constraints

- Use Scala 3 indentation style, two spaces, and no tabs.
- Add no configuration beyond `[package] name` and `version`.
- Preserve `onion [options] file.on [args...]` and `onion repl`.
- Never follow symbolic links during project discovery, cleanup, or output
  validation.
- Sort normalized project-relative paths before hashing, compiling, listing,
  or testing.
- Never replace the last successful classes or build state after a failed
  compile or failed promotion.
- Use SHA-256 over length-delimited bytes; never use timestamps as cache input.
- Render compiler diagnostics with `DiagnosticRenderer` to the injected error
  stream.
- Keep project tests sequential and restore `System.out`, `System.err`, and the
  thread context class loader in `finally` blocks.
- Run targeted tests after each task and the full English-locale suite before
  publishing.

---

### Task 1: Strict project manifest model

**Files:**

- Modify: `build.sbt`
- Create: `src/main/scala/onion/tools/project/ProjectError.scala`
- Create: `src/main/scala/onion/tools/project/ProjectManifest.scala`
- Test: `src/test/scala/onion/tools/project/ProjectManifestSpec.scala`

**Interfaces:**

```scala
package onion.tools.project

import java.nio.file.Path

final case class ProjectError(message: String, cause: Option[Throwable] = None)

final case class ProjectManifest(
  name: String,
  version: String,
  path: Path,
  bytes: Array[Byte]
)

object ProjectManifest:
  def load(path: Path): Either[ProjectError, ProjectManifest]
  private[project] def validName(value: String): Boolean
  private[project] def validVersion(value: String): Boolean
```

- [x] **Step 1: Write failing manifest tests**

Cover:

- the exact minimal manifest;
- missing `[package]`, `name`, or `version`;
- non-string values;
- invalid package names;
- the SemVer 2.0 valid and invalid examples;
- unknown root keys, root tables, and package keys;
- duplicate keys and malformed TOML; and
- line/column rendering from `TomlParseError` and `inputPositionOf`.

Use a temporary directory and assert error message text, not exception classes.
Ensure the loaded `bytes` equal the exact file bytes.

- [x] **Step 2: Run the test and observe dependency/implementation failure**

Run:

```bash
sbt -Duser.language=en 'testOnly onion.tools.project.ProjectManifestSpec'
```

Expected: FAIL because the model does not exist.

- [x] **Step 3: Add TOML dependency**

Add to `libraryDependencies`:

```scala
"org.tomlj" % "tomlj" % "1.1.1"
```

Do not mark it test-only; the distribution needs it at runtime.

- [x] **Step 4: Implement strict loading**

Use `Toml.parse(path)`, collect all parse errors in stable position order, and
inspect `keySet()` at the root and package table. Validate values only after
structure validation so malformed shape does not cause a cast exception.

Use an anchored SemVer 2.0 regex with numeric leading-zero checks:

```scala
private val SemVer =
  raw"""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""" +
  raw"""(?:-((?:0|[1-9]\d*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*)""" +
  raw"""(?:\.(?:0|[1-9]\d*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*))*))?""" +
  raw"""(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$$"""
```

Read manifest bytes before parsing and preserve them in the result.

- [x] **Step 5: Run tests**

Run the targeted suite and `git diff --check`.

- [x] **Step 6: Commit**

```bash
git add build.sbt src/main/scala/onion/tools/project \
  src/test/scala/onion/tools/project/ProjectManifestSpec.scala
git commit -m "Add strict Onion project manifest"
```

---

### Task 2: Project discovery and conventional layout

**Files:**

- Create: `src/main/scala/onion/tools/project/ProjectLocator.scala`
- Create: `src/main/scala/onion/tools/project/ProjectLayout.scala`
- Test: `src/test/scala/onion/tools/project/ProjectLocatorSpec.scala`
- Test: `src/test/scala/onion/tools/project/ProjectLayoutSpec.scala`

**Interfaces:**

```scala
final case class ProjectPaths(
  root: Path,
  manifest: Path,
  target: Path,
  classes: Path,
  onionState: Path,
  buildState: Path
)

object ProjectLocator:
  def locate(start: Path): Either[ProjectError, ProjectPaths]

final case class ProjectSource(path: Path, relative: String)

final case class ProjectLayout(
  productionSources: Vector[ProjectSource],
  testSources: Vector[ProjectSource]
)

object ProjectLayout:
  def discover(paths: ProjectPaths): Either[ProjectError, ProjectLayout]
```

- [x] **Step 1: Write failing locator tests**

Prove:

- a manifest in the starting directory is found;
- discovery walks upward;
- the nearest nested manifest wins;
- a relative starting path is accepted;
- the root is canonical; and
- a missing manifest reports the absolute normalized starting path.

- [x] **Step 2: Write failing layout tests**

Prove:

- production `.on` files are recursive and sorted;
- only `*_test.on` files are tests;
- non-Onion files are ignored;
- missing `src` produces an empty production set for the builder to reject;
- missing `tests` produces an empty test set;
- normalized relative paths use `/`; and
- directory and file symbolic links are ignored.

- [x] **Step 3: Run and observe failure**

```bash
sbt -Duser.language=en \
  'testOnly onion.tools.project.ProjectLocatorSpec onion.tools.project.ProjectLayoutSpec'
```

- [x] **Step 4: Implement discovery**

Walk parent paths iteratively. Once found, call `toRealPath()` on the root and
derive every generated path from that canonical root.

Use `Files.walk(root)` without `FOLLOW_LINKS`. Retain only
`Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)` and explicitly reject
`Files.isSymbolicLink(path)`. Close every walk stream with `Using.resource`.

- [x] **Step 5: Run tests and commit**

```bash
git add src/main/scala/onion/tools/project \
  src/test/scala/onion/tools/project
git commit -m "Add conventional Onion project layout"
```

---

### Task 3: Safe project scaffolding

**Files:**

- Create: `src/main/scala/onion/tools/project/ProjectScaffolder.scala`
- Test: `src/test/scala/onion/tools/project/ProjectScaffolderSpec.scala`

**Interface:**

```scala
object ProjectScaffolder:
  def create(parent: Path, name: String): Either[ProjectError, ProjectPaths]
```

- [x] **Step 1: Write failing scaffold tests**

Assert exact UTF-8 content for:

- `onion.toml`;
- `src/main.on`, which prints `Hello, <name>!`; and
- `tests/main_test.on`, which executes a passing `Assert::assertEquals`.

Also prove invalid names fail without creating a path, existing files or
directories are never overwritten, a partially failed creation is cleaned up,
and no `.git` directory is created.

- [x] **Step 2: Run and observe failure**

```bash
sbt -Duser.language=en \
  'testOnly onion.tools.project.ProjectScaffolderSpec'
```

- [x] **Step 3: Implement**

Create the project root with `Files.createDirectory`, then create fixed child
directories and files. Track whether this invocation created the root. On any
failure, delete only that newly-created tree without following links.

Use these program shapes:

```onion
def main(args: String[]): void {
  println("Hello, hello!")
}
```

```onion
Assert::assertEquals(4, 2 + 2)
```

- [x] **Step 4: Run tests and commit**

```bash
git add src/main/scala/onion/tools/project/ProjectScaffolder.scala \
  src/test/scala/onion/tools/project/ProjectScaffolderSpec.scala
git commit -m "Add Onion project scaffold"
```

---

### Task 4: Unified CLI routing

**Files:**

- Create: `src/main/scala/onion/tools/OnionCli.scala`
- Create: `src/main/scala/onion/tools/project/ProjectCommands.scala`
- Modify: `src/main/scala/onion/tools/ScriptRunner.scala`
- Test: `src/test/scala/onion/tools/OnionCliSpec.scala`

**Interfaces:**

```scala
object OnionCli:
  def run(
    args: Array[String],
    cwd: Path,
    out: PrintStream,
    err: PrintStream
  ): Int

private[tools] trait LegacyCommands:
  def script(args: Array[String]): Int
  def repl(args: Array[String]): Int

private[tools] def run(
  args: Array[String],
  cwd: Path,
  out: PrintStream,
  err: PrintStream,
  legacy: LegacyCommands,
  projects: ProjectCommands
): Int
```

`ProjectCommands` initially implements `new` and returns a clear
not-yet-implemented project error for the other recognized commands. Those
temporary errors are replaced before the branch is published.

- [x] **Step 1: Write failing routing tests**

Use fake `LegacyCommands` and a temporary cwd. Cover:

- no arguments and project help;
- exact reservation of `new/build/run/test/clean`;
- `repl` delegation with remaining args;
- script path and leading runner-option delegation unchanged;
- `new` success and usage failures;
- `run` parsing before/after `--`;
- unknown project options;
- stdout/stderr separation; and
- exit `0`, `1`, and `2`.

- [x] **Step 2: Run and observe failure**

```bash
sbt -Duser.language=en 'testOnly onion.tools.OnionCliSpec'
```

- [x] **Step 3: Refactor script main without behavior change**

Extract:

```scala
def runMain(args: Array[String]): Int
```

from `ScriptRunner.main`. It must retain `--help`, `--version`, `--watch`,
verbose filtering, `ScriptException` unwrapping, and the existing runner exit
code. `main` calls `runMain` and exits only when nonzero.

- [x] **Step 4: Implement dispatcher and command parser**

Do not use a general parser dependency. There are five commands and only
`--verbose` plus the run separator. Centralize usage text and normalize all
project usage failures to exit `2`.

`OnionCli.main` passes real process dependencies and exits only after `run`.

- [x] **Step 5: Run targeted compatibility suites**

```bash
sbt -Duser.language=en \
  'testOnly onion.tools.OnionCliSpec onion.compiler.tools.ScriptRunner*'
```

Use `rg --files src/test/scala | rg 'ScriptRunner'` to substitute the exact
existing suite names when necessary.

- [x] **Step 6: Commit**

```bash
git add src/main/scala/onion/tools/OnionCli.scala \
  src/main/scala/onion/tools/ScriptRunner.scala \
  src/main/scala/onion/tools/project/ProjectCommands.scala \
  src/test/scala/onion/tools/OnionCliSpec.scala
git commit -m "Add unified Onion command routing"
```

---

### Task 5: Correct shared class-file writer

**Files:**

- Create: `src/main/scala/onion/tools/CompiledClassWriter.scala`
- Modify: `src/main/scala/onion/tools/CompilerFrontend.scala`
- Test: `src/test/scala/onion/tools/CompiledClassWriterSpec.scala`
- Test: the existing compiler frontend suites located with
  `rg -l 'CompilerFrontend' src/test/scala`

**Interface:**

```scala
final case class CompiledClassWriteError(
  message: String,
  cause: Option[Throwable] = None
)

object CompiledClassWriter:
  def relativePath(
    className: String
  ): Either[CompiledClassWriteError, Path]
  def writeAll(
    binaries: Seq[CompiledClass]
  ): Either[CompiledClassWriteError, Vector[Path]]
```

- [x] **Step 1: Write failing writer tests**

Cover:

- `demo.App` writes to `<outputPath>/demo/App.class`;
- default-package classes;
- `$` in synthetic/inner class names;
- rejected empty, absolute, slash-containing, or traversal class names;
- deterministic returned paths; and
- deletion of files created by the current call after a later write failure.

Use `CompiledClass` values with temporary output paths and known bytes.

- [x] **Step 2: Run and observe failure**

```bash
sbt -Duser.language=en 'testOnly onion.tools.CompiledClassWriterSpec'
```

- [x] **Step 3: Implement and share**

Validate every binary-name segment with Java identifier-compatible ASCII plus
`$`, resolve segments below the normalized output root, and verify
`target.startsWith(root)`.

Replace `CompilerFrontend.generateFiles` with `CompiledClassWriter.writeAll`.
This intentionally fixes package output layout instead of retaining the old
simple-name-only path. Keep this general writer independent of
`onion.tools.project`; `ProjectBuilder` translates a write error into its own
`ProjectError` at the boundary.

- [x] **Step 4: Run writer and frontend tests, then commit**

```bash
git add src/main/scala/onion/tools/CompiledClassWriter.scala \
  src/main/scala/onion/tools/CompilerFrontend.scala \
  src/test/scala/onion/tools/CompiledClassWriterSpec.scala
git commit -m "Write compiled classes by qualified name"
```

---

### Task 6: Fingerprint and strict build-state codec

**Files:**

- Create: `src/main/scala/onion/tools/project/BuildFingerprint.scala`
- Create: `src/main/scala/onion/tools/project/BuildState.scala`
- Test: `src/test/scala/onion/tools/project/BuildFingerprintSpec.scala`
- Test: `src/test/scala/onion/tools/project/BuildStateSpec.scala`

**Interfaces:**

```scala
final case class EntryPoint(
  className: String,
  source: String,
  line: Int,
  column: Int
)

final case class BuildState(
  schemaVersion: Int,
  fingerprint: String,
  classes: Vector[String],
  entryPoints: Vector[EntryPoint]
)

object BuildFingerprint:
  val SchemaVersion = 1
  def compute(
    manifestBytes: Array[Byte],
    sources: Vector[(String, Array[Byte])],
    compilerVersion: String,
    javaFeature: Int
  ): String

object BuildState:
  def encode(state: BuildState): String
  def decode(text: String): Either[ProjectError, BuildState]
  def load(path: Path): Either[ProjectError, BuildState]
  def write(path: Path, state: BuildState): Either[ProjectError, Unit]
  def validatesOutputs(state: BuildState, classesRoot: Path): Boolean
```

- [x] **Step 1: Write failing fingerprint tests**

Use fake version/JDK inputs. Prove absolute roots and input enumeration order do
not matter after caller sorting, while every declared field, source addition,
removal, rename, and byte change does.

Include an ambiguity regression such as `("ab", "c")` versus `("a", "bc")`.

- [x] **Step 2: Write failing state tests**

Cover deterministic pretty JSON, round trip, malformed JSON, unknown/missing
fields, numeric range, wrong types, schema mismatch, duplicate/unsafe classes,
unsorted input normalization or rejection, invalid source paths, entrypoint
class membership, and missing class files.

- [x] **Step 3: Run and observe failure**

```bash
sbt -Duser.language=en \
  'testOnly onion.tools.project.BuildFingerprintSpec onion.tools.project.BuildStateSpec'
```

- [x] **Step 4: Implement fingerprint**

Use `MessageDigest.getInstance("SHA-256")` and a helper that feeds a four-byte
big-endian length followed by UTF-8 or raw bytes. Sort sources by relative path
inside `compute` defensively.

- [x] **Step 5: Implement state with `onion.Json`**

Construct `java.util.LinkedHashMap` and `java.util.ArrayList` values for stable
encoding. Decode into the JSON intermediate types and explicitly validate every
field. Do not catch `Throwable`; catch the JSON parse exception and I/O
exceptions separately.

Class output validation calls the same binary-name path helper as
`CompiledClassWriter`.

- [x] **Step 6: Run tests and commit**

```bash
git add src/main/scala/onion/tools/project/BuildFingerprint.scala \
  src/main/scala/onion/tools/project/BuildState.scala \
  src/test/scala/onion/tools/project/BuildFingerprintSpec.scala \
  src/test/scala/onion/tools/project/BuildStateSpec.scala
git commit -m "Add deterministic Onion build state"
```

---

### Task 7: Source-level entrypoint discovery

**Files:**

- Create: `src/main/scala/onion/tools/project/EntryPointDiscovery.scala`
- Test: `src/test/scala/onion/tools/project/EntryPointDiscoverySpec.scala`

**Interface:**

```scala
object EntryPointDiscovery:
  def discover(
    root: Path,
    units: Seq[AST.CompilationUnit]
  ): Either[ProjectError, Vector[EntryPoint]]

  def testEntry(
    root: Path,
    unit: AST.CompilationUnit
  ): Either[ProjectError, EntryPoint]
```

- [x] **Step 1: Write failing tests with real parser output**

Compile or parse small temporary sources and prove:

- executable `src/main.on` is conventional;
- declaration-only `src/main.on` is not executable;
- an explicit top-level `main` in another source is discovered;
- main convention plus explicit main in one file de-duplicates;
- helper-only sources are ignored despite generated JVM mains;
- zero and multiple candidates are represented deterministically;
- explicit-main line/column is retained;
- a module prefixes the top-level class; and
- same-basename class collisions are rejected with both sources.

- [x] **Step 2: Run and observe failure**

```bash
sbt -Duser.language=en \
  'testOnly onion.tools.project.EntryPointDiscoverySpec'
```

- [x] **Step 3: Implement from parsed units**

Match original `AST.FunctionDeclaration` nodes named `main` and
`AST.BlockElement` nodes. Reproduce the compiler top-class rule:

```scala
val simple = Paths.cutExtension(unit.sourceFile) + "Main"
val className =
  Option(unit.module).map(m => s"${m.name}.$simple").getOrElse(simple)
```

Normalize every `sourceFile` through the canonical root and reject any unit
outside it.

Do not inspect generated JVM methods to select an entrypoint.

- [x] **Step 4: Run tests and commit**

```bash
git add src/main/scala/onion/tools/project/EntryPointDiscovery.scala \
  src/test/scala/onion/tools/project/EntryPointDiscoverySpec.scala
git commit -m "Discover project entrypoints from source"
```

---

### Task 8: Transactional cached project builder

**Files:**

- Create: `src/main/scala/onion/tools/project/FileTree.scala`
- Create: `src/main/scala/onion/tools/project/BuildOutputTransaction.scala`
- Create: `src/main/scala/onion/tools/project/ProjectBuilder.scala`
- Test: `src/test/scala/onion/tools/project/BuildOutputTransactionSpec.scala`
- Test: `src/test/scala/onion/tools/project/ProjectBuilderSpec.scala`

**Interfaces:**

```scala
private[project] trait PathMover:
  def move(source: Path, target: Path): Unit

final case class ProjectBuild(
  paths: ProjectPaths,
  manifest: ProjectManifest,
  layout: ProjectLayout,
  state: BuildState,
  cached: Boolean
)

final class ProjectBuilder(
  compilerVersion: String = OnionVersion.value,
  javaFeature: Int = Runtime.version.feature,
  mover: PathMover = PathMover.system
):
  def build(
    paths: ProjectPaths,
    manifest: ProjectManifest,
    layout: ProjectLayout,
    err: PrintStream
  ): Either[ProjectError, ProjectBuild]
```

- [x] **Step 1: Write failing transaction tests**

Use a fake mover that fails on a configured move number. Prove:

- first promotion;
- successful replacement;
- rollback when old classes, old state, new classes, or new state move fails;
- stale output removal after success;
- backup/staging cleanup; and
- deletion does not traverse directory symbolic links.

- [x] **Step 2: Write failing builder tests**

Use real tiny Onion projects. Cover:

- empty production sources;
- successful compile and fully qualified output;
- state creation;
- exact cache hit;
- invalidation by every fingerprint input;
- missing recorded class invalidation;
- compile diagnostics to the injected stream;
- compile failure preserving old output/state;
- deterministic class and entrypoint order; and
- staging cleanup on all failures.

- [x] **Step 3: Run and observe failure**

```bash
sbt -Duser.language=en \
  'testOnly onion.tools.project.BuildOutputTransactionSpec onion.tools.project.ProjectBuilderSpec'
```

- [x] **Step 4: Implement safe tree utilities and transaction**

`FileTree.delete(path)` checks that the requested path was derived below the
validated project target, walks without link following, and deletes children
before parents.

The default mover first tries:

```scala
Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
```

and retries without `ATOMIC_MOVE` only for
`AtomicMoveNotSupportedException`.

- [x] **Step 5: Implement builder**

Main compilation uses UTF-8, no external project dependency classpath, staging
classes as `outputDirectory`, ten maximum errors, normal warnings, and law
checks enabled.

After a successful compiler result:

1. discover entries from `parsedUnits`;
2. write returned classes through `CompiledClassWriter`;
3. build and stage state;
4. promote; and
5. return the promoted state.

If parsed artifacts are unexpectedly absent after successful compilation,
return an internal project error rather than emitting an empty state.

- [x] **Step 6: Wire `build` command**

Replace the temporary `ProjectCommands.build` failure. It locates, loads,
discovers, and builds, then prints:

```text
Built <name> (<n> classes)
Built <name> (cached)
```

- [x] **Step 7: Run tests and commit**

```bash
git add src/main/scala/onion/tools/project \
  src/test/scala/onion/tools/project
git commit -m "Add cached transactional project builds"
```

---

### Task 9: Deterministic project execution

**Files:**

- Create: `src/main/scala/onion/tools/project/ProjectClassRunner.scala`
- Modify: `src/main/scala/onion/tools/project/ProjectCommands.scala`
- Test: `src/test/scala/onion/tools/project/ProjectClassRunnerSpec.scala`
- Test: `src/test/scala/onion/tools/project/ProjectRunIntegrationSpec.scala`

**Interface:**

```scala
sealed trait ProgramResult
object ProgramResult:
  final case class Success(value: Any) extends ProgramResult
  final case class Failure(message: String, cause: Option[Throwable])
    extends ProgramResult

object ProjectClassRunner:
  def run(
    classPath: Vector[Path],
    className: String,
    args: Array[String]
  ): ProgramResult
```

- [x] **Step 1: Write failing runner tests**

Compile fixture classes and cover:

- public static `main(String[])`;
- argument forwarding;
- void, numeric zero, numeric nonzero, and other return values;
- missing class/method;
- non-public or non-static method;
- user exception unwrapping;
- context-class-loader restoration; and
- class-loader closure after execution.

- [x] **Step 2: Write failing run integration tests**

Cover:

- build then execute;
- cached build still executes;
- no entrypoint;
- multiple entries listed in sorted source order;
- run from a nested cwd;
- arguments after `--`;
- concise runtime error;
- verbose stack trace; and
- normalized command exit codes.

- [x] **Step 3: Run and observe failure**

```bash
sbt -Duser.language=en \
  'testOnly onion.tools.project.ProjectClassRunnerSpec onion.tools.project.ProjectRunIntegrationSpec'
```

- [x] **Step 4: Implement runner**

Construct a fresh `URLClassLoader` for every call. Load exactly `className`.
Invoke with:

```scala
method.invoke(null, args.asInstanceOf[Object])
```

so the array is one reflection argument. Unwrap `InvocationTargetException`.
Use the returned method value for numeric exit semantics.

- [x] **Step 5: Wire `run`**

Build first, require exactly one `BuildState.entryPoints` item, and pass only
post-`--` arguments. Print entrypoint ambiguity to stderr with locations.

- [x] **Step 6: Run tests and commit**

```bash
git add src/main/scala/onion/tools/project \
  src/test/scala/onion/tools/project
git commit -m "Run Onion projects deterministically"
```

---

### Task 10: Ordinary-program test runner

**Files:**

- Create: `src/main/scala/onion/tools/project/ProjectTestRunner.scala`
- Modify: `src/main/scala/onion/tools/project/ProjectCommands.scala`
- Test: `src/test/scala/onion/tools/project/ProjectTestRunnerSpec.scala`
- Test: `src/test/scala/onion/tools/project/ProjectTestIntegrationSpec.scala`

**Interfaces:**

```scala
final case class TestCaseResult(
  source: String,
  passed: Boolean,
  stdout: String,
  stderr: String,
  failure: Option[String]
)

final case class TestRunResult(cases: Vector[TestCaseResult]):
  def passed: Int
  def failed: Int
  def successful: Boolean

final class ProjectTestRunner:
  def run(
    build: ProjectBuild,
    out: PrintStream,
    err: PrintStream,
    verbose: Boolean
  ): TestRunResult
```

- [x] **Step 1: Write failing unit tests**

Use injectable compile/run boundaries where useful. Prove sorted sequential
execution, summary counts, continue-after-failure, capture restoration after a
throw, failed-output rendering, verbose passed-output rendering, and cleanup.

- [x] **Step 2: Write failing real integration tests**

Temporary projects cover:

- a passing assertion;
- assertion failure;
- compile failure;
- runtime exception;
- numeric nonzero main;
- stdout/stderr shown only on failure;
- two tests where the first fails and second runs;
- project class use through `target/classes`;
- fresh static state between test loaders;
- missing/empty tests; and
- cached main build with tests still rerun.

- [x] **Step 3: Run and observe failure**

```bash
sbt -Duser.language=en \
  'testOnly onion.tools.project.ProjectTestRunnerSpec onion.tools.project.ProjectTestIntegrationSpec'
```

- [x] **Step 4: Implement**

For each test:

- compile that file alone with `target/classes` as compiler classpath;
- write classes below a unique `target/.onion/test-<random>/classes`;
- derive the test top-class from its parsed unit with
  `EntryPointDiscovery.testEntry`;
- capture global output only around runtime invocation;
- run with URLs ordered test classes, then project classes;
- restore globals and remove temporary output in `finally`.

Compile diagnostics belong to the test's failure detail. They must not abort
the rest of the suite.

- [x] **Step 5: Wire `test` and commit**

```bash
git add src/main/scala/onion/tools/project \
  src/test/scala/onion/tools/project
git commit -m "Add convention-based Onion tests"
```

---

### Task 11: Clean command and complete lifecycle integration

**Files:**

- Modify: `src/main/scala/onion/tools/project/ProjectCommands.scala`
- Create: `src/test/scala/onion/tools/project/ProjectLifecycleSpec.scala`

- [x] **Step 1: Write failing lifecycle tests**

Drive `OnionCli.run` with real services through:

```text
new → build → cached build → run → test → clean
```

Also prove:

- every command works from a nested directory after creation;
- clean is idempotent;
- clean removes only the canonical project target;
- a `target` symlink is rejected rather than followed; and
- an unrelated neighboring target remains untouched.

- [x] **Step 2: Run and observe failure**

```bash
sbt -Duser.language=en \
  'testOnly onion.tools.project.ProjectLifecycleSpec'
```

- [x] **Step 3: Implement clean and harden command composition**

Require a valid located project and valid manifest before deletion. Reject a
symbolic-link `target`. Print `Cleaned target` whether the directory existed or
not so output is stable.

Remove all temporary not-yet-implemented paths from `ProjectCommands`.

- [x] **Step 4: Run all project suites and commit**

```bash
sbt -Duser.language=en 'testOnly onion.tools.project.* onion.tools.OnionCliSpec'
git add src/main/scala/onion/tools/project \
  src/test/scala/onion/tools/project
git commit -m "Complete Onion project lifecycle"
```

---

### Task 12: Launchers, distribution, and user documentation

**Files:**

- Modify: `bin/onion`
- Modify: `bin/onion.bat`
- Modify: `install.sh`
- Modify: `README.md`
- Create: `docs/tools/project-cli.md`
- Create: `docs/ja/tools/project-cli.md`
- Modify: `docs/index.md`
- Modify: `docs/ja/index.md`
- Test: `src/test/scala/onion/tools/project/ProjectDistributionSpec.scala`

- [x] **Step 1: Write failing distribution test**

The test runs `sbt dist` only through the dedicated final verification command,
not recursively from ScalaTest. Inside the suite, validate source launcher
contents and expose a helper that can smoke an unpacked distribution path
provided by a system property.

The external smoke script sequence is:

```bash
<dist>/bin/onion new hello
cd hello
<dist>/bin/onion run
<dist>/bin/onion test
<dist>/bin/onion clean
```

Assert the greeting, passing summary, exit statuses, and removal of `target`.

- [x] **Step 2: Switch all `onion` launchers**

Unix, batch, and installer-generated `onion` launchers invoke:

```text
onion.tools.OnionCli
```

Remove Unix-only REPL special casing. Preserve classpath assembly, user
`CLASSPATH`, Java lookup, CDS flags, and quoting.

- [x] **Step 3: Document the project journey**

The English and Japanese project CLI pages document:

- install-to-first-project commands;
- the fixed layout and manifest;
- build cache behavior;
- entrypoint convention;
- test discovery and failure rules;
- nested-directory discovery;
- clean safety;
- exit codes;
- script backward compatibility; and
- explicitly deferred build-system features.

Add concise links from English/Japanese landing pages. Keep code examples
compatible with the documentation-example directive policy already present in
the repository.

- [x] **Step 4: Update README**

Put the four-command project journey near the existing script quick start, not
in place of it.

- [x] **Step 5: Run targeted tests and commit**

```bash
sbt -Duser.language=en 'testOnly onion.tools.project.* onion.tools.OnionCliSpec'
git add bin/onion bin/onion.bat install.sh README.md docs \
  src/test/scala/onion/tools/project/ProjectDistributionSpec.scala
git commit -m "Ship the Onion project workflow"
```

---

### Task 13: Final verification, review, and pull request

**Files:**

- Modify only files required by failures or review.

- [x] **Step 1: Inspect scope before verification**

```bash
git status --short
git diff --check
git diff --stat develop...HEAD
git log --oneline develop..HEAD
```

Confirm there are no unrelated workspace changes and no temporary project
outputs tracked by Git.

- [x] **Step 2: Run focused project verification**

```bash
sbt -Duser.language=en 'testOnly onion.tools.project.* onion.tools.OnionCliSpec'
```

Expected: all targeted suites pass.

- [x] **Step 3: Run full regression suite**

```bash
sbt -Duser.language=en test
```

Expected: all suites and tests pass with zero failures.

- [x] **Step 4: Build and smoke the distribution**

```bash
sbt dist
```

Unpack the newly produced ZIP into a `mktemp -d` directory, run the exact
`new → run → test → clean` journey, and remove only that temporary directory.
Also verify the archive contains `tomlj` and its runtime dependencies in
`lib/`.

- [x] **Step 5: Check legacy launchers**

Using the unpacked distribution:

```bash
bin/onion --version
bin/onion <temporary-script.on>
bin/onionc -d <temporary-output> <temporary-script.on>
```

Expected: version output, script output, and class output all succeed.

- [x] **Step 6: Request code review**

Use the `superpowers:requesting-code-review` skill. Provide the design,
implementation plan, commit range, and verification evidence. Fix every
Critical or Important finding with a regression test, rerun the relevant
targeted suite, and request follow-up review when a material fix was needed.

- [x] **Step 7: Re-run verification after review fixes**

At minimum rerun:

```bash
git diff --check
sbt -Duser.language=en 'testOnly onion.tools.project.* onion.tools.OnionCliSpec'
sbt -Duser.language=en test
sbt dist
```

- [x] **Step 8: Publish a draft PR to `develop`**

Use `github:yeet` to inspect the final diff, push
`codex/project-cli-v1`, and open a draft pull request targeting `develop`.
The PR body includes:

- the complete user journey;
- compatibility behavior;
- cache and transaction guarantees;
- entrypoint and test conventions;
- deferred scope;
- exact verification commands and counts; and
- distribution smoke evidence.

- [ ] **Step 9: Monitor CI**

Inspect every required check. If a check fails, use
`superpowers:systematic-debugging` and `github:gh-fix-ci`, implement the
smallest evidence-backed correction, push it, and continue until checks are
green or a genuine external blocker is established.
