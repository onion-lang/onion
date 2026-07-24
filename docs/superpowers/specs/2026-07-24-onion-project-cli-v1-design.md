# Onion Project CLI v1

- **Date:** 2026-07-24
- **Status:** Approved design
- **Milestone:** Convention-over-configuration project workflow

## Purpose

Onion already works well as a script language, but a user who wants to keep
more than one source file must currently choose and configure an external build
tool. The first project workflow should make the ordinary path require no build
definition and no build-tool knowledge:

```text
onion new hello
cd hello
onion run
onion test
```

This milestone adds that path without replacing the existing script runner or
REPL. A project is defined by one small manifest and a conventional directory
layout. Compilation stays in the Onion process and reuses the public compiler
pipeline.

The goal is not to create a general-purpose build system. It is to make the
smallest useful Onion project pleasant, deterministic, safe, and fast on the
second invocation.

## Principles

1. **Convention before configuration.** Source roots, test discovery, output
   paths, and the default entry source are fixed in v1.
2. **One command surface.** `onion` dispatches project commands, scripts, and
   the REPL without requiring another launcher.
3. **Backward compatibility.** Existing `onion [runner-options] file.on
   [arguments...]` and `onion repl` behavior remains available.
4. **Safe output.** A failed compilation may not destroy the last successful
   project output.
5. **Deterministic behavior.** Source discovery, test order, diagnostics, and
   cache inputs do not depend on filesystem enumeration order.
6. **Honest cache hits.** Cached output is accepted only when its complete
   fingerprint and every recorded class file agree with the current project.
7. **Useful failure boundaries.** Usage errors, project failures, compilation
   failures, and runtime failures have concise messages and stable exit codes.

## User Experience

### Project shape

`onion new hello` creates:

```text
hello/
├── onion.toml
├── src/
│   └── main.on
└── tests/
    └── main_test.on
```

Generated files live under `hello/target/`. The command does not initialize a
Git repository and never overwrites an existing path.

The generated manifest is:

```toml
[package]
name = "hello"
version = "0.1.0"
```

The generated program prints a greeting. The generated test is an ordinary
Onion program that uses `Assert` and passes.

### Command grammar

The unified command surface is:

```text
onion new <name>
onion build [--verbose]
onion run [--verbose] [-- <arguments...>]
onion test [--verbose]
onion clean
onion repl [repl-options...]
onion [script-runner-options...] <source.on> [arguments...]
```

`new`, `build`, `run`, `test`, and `clean` are reserved only when they are the
first argument. All other invocations delegate unchanged to the existing
script runner. This preserves script arguments that resemble project commands.

Project commands reject unknown options and misplaced arguments. `--` is
required before arguments passed by `onion run`; this keeps future command
options unambiguous.

Exit codes are:

- `0`: command completed successfully;
- `1`: project discovery, manifest, build, test, or runtime failure; and
- `2`: invalid command-line usage.

The process entry point calls `System.exit` only after the testable dispatcher
returns one of these codes.

### Project discovery

`build`, `run`, `test`, and `clean` begin at the supplied working directory and
walk upward until they find `onion.toml`. The first match is the project root.
The root is canonicalized before layout or deletion logic uses it.

If no manifest is found, the command fails with:

```text
error: no onion.toml found in <start> or its parents
```

This permits commands from `src/`, `tests/`, and other project subdirectories
without configuration.

### Manifest contract

The v1 manifest accepts exactly one table, `[package]`, with exactly two string
keys:

- `name`: matches `[A-Za-z][A-Za-z0-9_-]*`;
- `version`: a valid SemVer 2.0 version.

The table and both keys are required. Unknown root keys, unknown tables,
unknown package keys, duplicate keys, invalid TOML, wrong value types, invalid
names, and invalid versions are errors. Diagnostics include
`onion.toml:<line>:<column>` whenever the TOML parser exposes a position.

The implementation uses `org.tomlj:tomlj:1.1.1`. It is a runtime dependency
and is therefore included in distribution `lib/` by the existing `dist` task.
Onion's own JSON implementation is used for the internal build-state file; a
second JSON dependency is not added.

No dependency declarations, source-root overrides, entrypoint overrides,
compiler flags, profiles, or scripts are accepted in v1.

### Source and test discovery

Production sources are regular, non-symbolic-link files under `src/` whose
names end in `.on`. Tests are regular, non-symbolic-link files under `tests/`
whose names end in `_test.on`.

Discovery:

- is recursive;
- does not follow symbolic links;
- produces paths relative to the project root with `/` separators;
- sorts by that normalized relative path; and
- never scans `target/`.

An empty production source set is a build error. A missing or empty `tests/`
directory is a successful test run with a zero-test summary.

## Entrypoint Convention

The existing compiler creates a JVM `main(String[])` method for each top-level
source container, even when a source contains only reusable declarations.
Consequently, classfile inspection alone cannot distinguish a real project
entry from a compiler-generated helper entry.

Project entrypoint discovery therefore uses the parsed source units captured by
`CompilationResult.debugArtifacts.parsedUnits`:

1. `src/main.on` is a candidate when it contains at least one top-level block
   element, including a top-level variable declaration or expression.
2. Any production source containing a top-level function named `main` is a
   candidate.
3. A source that satisfies both rules is one candidate.
4. Compiler-synthesized JVM mains in all other sources are ignored.

The compiler's top-level class naming rule derives the JVM class from the
source basename and optional module. Entrypoint discovery records that exact
class name, the project-relative source path, and the source location of an
explicit `main` when present.

`run` requires exactly one candidate:

- zero candidates: fail and explain that `src/main.on` needs executable
  top-level code or a source needs a top-level `main`;
- more than one candidate: fail and list every source and location in sorted
  order; and
- one candidate: invoke only its public static `main(String[])`.

This rule lets helper sources contain types, functions, aliases, extensions,
and global declarations without accidentally becoming runnable merely because
the compiler emitted a JVM compatibility method.

Each test file is itself the sole test entry source. The test compiler output
records and invokes that file's top-level class directly; helper JVM mains from
the already-built project are never searched.

## Architecture

The top-level package is `onion.tools.project`.

```text
OnionCli
├── legacy ScriptRunner / Repl delegation
└── ProjectCommands
    ├── ProjectLocator
    ├── ProjectManifest
    ├── ProjectLayout
    ├── ProjectScaffolder
    ├── ProjectBuilder
    │   ├── BuildFingerprint
    │   ├── BuildState
    │   ├── CompiledClassWriter
    │   └── BuildOutputTransaction
    ├── EntryPointDiscovery
    ├── ProjectClassRunner
    └── ProjectTestRunner
```

### Testable CLI boundary

The dispatcher has no dependency on process-global current-directory or
console lookup:

```scala
object OnionCli:
  def run(
    args: Array[String],
    cwd: Path,
    out: PrintStream,
    err: PrintStream
  ): Int

  def main(args: Array[String]): Unit
```

`main` passes `Paths.get("").toAbsolutePath`, `System.out`, and `System.err` to
`run`, then exits only for a nonzero result. Project components receive their
filesystem and output dependencies explicitly. Legacy delegation is wrapped
behind a small adapter so command-routing tests do not terminate the test JVM.

Unix and Windows launchers invoke `onion.tools.OnionCli`. The launcher-specific
REPL branch is removed because dispatch is now platform-independent. The
installer launcher uses the same entry point. `onionc` and `onion-repl` remain
unchanged.

### Build flow

`ProjectBuilder` performs:

```text
locate root
→ parse manifest
→ discover and sort production sources
→ compute fingerprint
→ validate cached state and class files
→ or compile to a staging directory
→ discover entrypoint metadata
→ write all classes and staged state
→ promote staged output transactionally
```

The compiler is configured with the staging classes directory as its output
directory. Compilation still returns `CompiledClass` values in memory.
`CompiledClassWriter` writes each fully qualified class name to its matching
directory path, for example `demo.App` to `demo/App.class`. The existing
compiler frontend delegates to the same writer so the project path does not
create a second class-file layout implementation.

Diagnostics are rendered through `DiagnosticRenderer` to the command's
injected error stream.

### Transactional output

Staged output is created below:

```text
target/.onion/staging-<random>/
├── classes/
└── build-state.json
```

Promotion operates on the same filesystem:

1. move existing `target/classes` and build state into a unique backup;
2. move staged classes and state into their final locations;
3. if either promotion move fails, remove only the partially promoted new
   artifacts and restore both backups; and
4. after success, remove the backup and staging directory.

Moves request `ATOMIC_MOVE` and fall back to a normal move when the filesystem
does not support atomic directory moves. Compilation failure occurs before
promotion and therefore leaves the previous successful output untouched.

Temporary and backup paths are always descendants of the canonical project
`target/.onion` directory. Recursive deletion never follows symbolic links.

### Build fingerprint

The whole-project v1 fingerprint is SHA-256 over a length-delimited binary
encoding of:

- cache schema version;
- Onion compiler version;
- Java feature version;
- exact `onion.toml` bytes;
- every sorted project-relative source path; and
- the exact bytes of each source.

Length delimiting prevents concatenation ambiguity. Paths use `/` separators.
Filesystem timestamps, absolute project paths, locale, and enumeration order
are not inputs.

The cache is intentionally whole-project, not per-file incremental
compilation. A source addition, removal, rename, or content change invalidates
it.

### Build state

`target/.onion/build-state.json` contains:

```json
{
  "schemaVersion": 1,
  "fingerprint": "<sha256>",
  "classes": ["MainMain", "demo.Widget"],
  "entryPoints": [
    {
      "className": "MainMain",
      "source": "src/main.on",
      "line": 1,
      "column": 1
    }
  ]
}
```

Class names and entrypoints are sorted. The codec rejects malformed JSON,
unknown schema versions, missing fields, wrong types, duplicate class names,
unsafe class names, and entrypoints whose classes are absent.

A cache hit requires:

- matching schema and fingerprint;
- a nonempty class list;
- every recorded class file at its fully qualified path below
  `target/classes`; and
- no recorded path escaping the classes directory.

Extra files do not make a cache hit invalid because the output directory was
created transactionally from one prior complete build. A successful rebuild
replaces the directory and therefore removes stale classes.

### Running

`ProjectClassRunner` creates a fresh `URLClassLoader` containing
`target/classes`, with the Onion application class loader as parent. It loads
the selected class without searching other generated classes, verifies a
public static `main(String[])`, invokes it, restores the thread context class
loader, and closes the URL loader.

Return handling is:

- `void`/`null`: success;
- numeric zero: success;
- numeric nonzero: command failure; and
- any other returned value: success, matching current script-runner behavior.

An exception thrown by user code produces a concise
`error: <ExceptionType>: <message>`. `--verbose` additionally prints its stack
trace. Reflection and class-loading failures are project runtime errors rather
than raw crashes.

`run` builds first. A valid cache hit avoids compilation but still creates a
fresh loader and executes the program. Arguments after `--` are passed
verbatim.

### Testing

`test` first builds production sources once. It then processes every discovered
test in sorted order:

1. create a unique temporary classes directory below `target/.onion`;
2. compile only the test file with `target/classes` on the compiler classpath;
3. render a compile failure against that test and continue;
4. invoke the test file's generated top-level class in a fresh loader whose
   URLs contain test classes before production classes;
5. capture the test's `System.out` and `System.err` while restoring both in a
   `finally` block;
6. treat an assertion/other exception or numeric nonzero result as failure;
7. print captured output only for a failed test, unless `--verbose`; and
8. delete the test's temporary output without following links.

Tests are sequential because JVM-wide output capture is process-global. Each
test gets a new compiler invocation and class loader, so static project state
does not leak between tests.

The concise output is:

```text
test tests/alpha_test.on ... ok
test tests/beta_test.on ... FAILED

2 tests, 1 passed, 1 failed
```

All tests run after an individual failure. A build failure prevents test
execution. A project with no tests prints `0 tests, 0 passed, 0 failed` and
succeeds.

### Clean

`clean` resolves and validates the project first, then removes exactly its
canonical `target` tree without following symbolic links. It does not delete
any other path and succeeds when `target` is absent.

## Error and Output Policy

Project command status goes to stdout. Diagnostics, usage failures, manifest
errors, build errors, and runtime failures go to stderr. Output is plain text
and deterministic; v1 does not add terminal color.

The normal successful build messages are:

```text
Built hello (3 classes)
Built hello (cached)
```

`run` does not print a second success banner after user output. `test` prints
per-test status and a summary. `clean` prints the project-relative directory it
removed.

Absolute paths are used only when needed to diagnose a project outside the
current directory. Source diagnostics retain compiler locations.

## Testing Strategy

### Unit tests

- `ProjectLocatorSpec`: upward discovery, nearest manifest, missing manifest,
  and canonical roots.
- `ProjectManifestSpec`: valid minimal TOML, parse locations, required keys,
  wrong types, SemVer, name validation, unknown keys, and unknown tables.
- `ProjectLayoutSpec`: recursive sorted sources, `_test.on` filtering,
  ignored symbolic links, missing roots, and normalized paths.
- `BuildFingerprintSpec`: stability across absolute roots and changes for
  schema, compiler, Java, manifest, source path, source order, and content.
- `BuildStateSpec`: deterministic round trip and rejection of malformed,
  incomplete, unsafe, duplicated, or incompatible state.
- `CompiledClassWriterSpec`: fully qualified paths, nested directories,
  deterministic results, and cleanup after write failure.
- `BuildOutputTransactionSpec`: initial promotion, replacement, rollback after
  each simulated move failure, and no link traversal.
- `EntryPointDiscoverySpec`: conventional implicit main, explicit main,
  de-duplication, zero candidates, multiple candidates, module names, and
  source locations.
- `OnionCliSpec`: routing, command syntax, streams, and exit codes.

### Integration tests

Temporary projects exercise:

- `new → build → run → test → clean`;
- command discovery from nested directories;
- run argument forwarding;
- cache hit plus manifest, path, content, addition, and removal invalidation;
- missing cached class invalidation;
- failed rebuild preservation of previous output and state;
- no entrypoint and ambiguous entrypoints;
- passing, assertion-failing, runtime-failing, and compile-failing tests;
- continued execution after failure;
- output capture and verbose output;
- deterministic test ordering;
- successful no-test projects; and
- legacy script and REPL routing compatibility.

### Release checks

Before completion:

```text
sbt -Duser.language=en 'testOnly onion.tools.project.*'
sbt -Duser.language=en test
sbt dist
```

The produced distribution is unpacked into a temporary directory and its Unix
launcher performs the generated `new → run → test → clean` journey. Windows
launcher dispatch is covered by direct CLI tests in this PR; native Windows
process smoke belongs in the existing cross-platform release workflow rather
than being simulated by Linux.

## Delivery

This is one pull request because the value is the complete first-use journey;
landing only a manifest parser or only a scaffold command would expose an
unusable partial workflow. The implementation remains reviewable through
ordered commits:

1. design and implementation plan;
2. project model, discovery, layout, and manifest;
3. unified CLI routing and scaffold;
4. class writer, build fingerprint, state, and transactional build;
5. entrypoint execution;
6. test runner;
7. launchers, documentation, distribution smoke, and final hardening.

## Deferred Work

The following are deliberately outside v1:

- dependency resolution or publication;
- multiple modules or workspaces;
- configurable source, test, output, or entrypoint paths;
- per-file incremental compilation;
- parallel compilation or parallel tests;
- watch mode for projects;
- test annotations, discovery inside compiled types, or a new test framework;
- package/archive commands;
- formatter or linter integration;
- lifecycle hooks and arbitrary manifest scripts;
- terminal color; and
- `onion init` for adopting an existing directory.

Those features should be driven by actual project use after this convention is
stable, rather than pre-emptively turning `onion.toml` into another large build
language.
