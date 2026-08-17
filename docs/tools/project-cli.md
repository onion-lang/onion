# Project CLI (onion new/build/run/test/clean)

The `onion` command also understands a small, convention-over-configuration
project workflow. It sits alongside the script runner and REPL: `new`,
`build`, `run`, `test`, and `clean` are reserved only when they are the exact
first argument, so every existing `onion [options] file.on [args...]` and
`onion repl` invocation keeps working unchanged.

## Quick Start

```bash
onion new hello
cd hello
onion run
onion test
```

`onion new hello` creates:

```text
hello/
├── onion.toml
├── src/
│   └── main.on
└── tests/
    └── main_test.on
```

Generated output lives under `hello/target/`, which `onion clean` removes.
No Git repository is initialized and no existing path is ever overwritten.

The generated manifest:

```toml
[package]
name = "hello"
version = "0.1.0"
```

The generated program:

```onion
def main(): void {
  println("Hello, hello!")
}
```

The generated test:

```onion
Assert::equals(4, 2 + 2)
```

## Commands

```text
onion new <name>
onion build [--verbose]
onion run [--verbose] [-- <arguments...>]
onion test [--verbose]
onion clean
```

`build`, `run`, `test`, and `clean` start at the current directory and walk
upward until they find `onion.toml`, so any of them also work from `src/`,
`tests/`, or another nested subdirectory of the project — there is no need to
`cd` back to the project root first.

Exit codes are stable across every project command:

- `0` — the command completed successfully.
- `1` — a project, manifest, build, test, or runtime failure.
- `2` — invalid command-line usage.

## Manifest

The manifest requires `[package]`, with exactly two required string keys: `name`
(matching `[A-Za-z][A-Za-z0-9_-]*`) and `version` (a valid
[SemVer 2.0](https://semver.org/spec/v2.0.0.html) version). Unknown keys or tables,
duplicate keys, malformed TOML, and invalid names or versions are all reported as errors,
with a line and column when the TOML parser can supply one. No source-root overrides,
entrypoint overrides, compiler flags, or scripts are accepted.

### `[dependencies]`

Maven coordinates, one per line, as `"group:artifact" = "version"`:

```toml
[package]
name = "report"
version = "0.1.0"

[dependencies]
"org.postgresql:postgresql" = "42.7.3"
"com.fasterxml.jackson.core:jackson-databind" = "2.17.0"
```

The key has to be quoted, because a Maven coordinate contains a colon. Versions are
exact — no ranges, no `latest`. Transitive dependencies are resolved and land on the
classpath for `build`, `run` and `test` alike, so a library you compile against is also
there when the program runs.

The **resolved** set — transitives included — is part of the build fingerprint, not just
the manifest text. A transitive moving underneath you invalidates the cache and forces a
recompile, rather than serving classes compiled against the old classpath as current.

A coordinate that cannot be resolved fails the build with coursier's message, which names
the coordinate it could not find.

### `[[repositories]]`

Extra Maven repositories, searched **before** Maven Central and in the order written:

```toml
[[repositories]]
url = "https://nexus.example.com/repository/maven-public"
```

Maven Central stays available; these are added to it rather than replacing it. Only
absolute `http`, `https` and `file` URLs are accepted — a relative or misspelled entry is
rejected here rather than surfacing later as a resolution failure blamed on a dependency.

This is an array of tables rather than a plain `repositories = [...]` array on purpose. A
bare key-value pair belongs to whatever table header precedes it, so written after
`[package]` it would quietly become `package.repositories`; a table header can go anywhere
in the file.

### Not yet supported

There is no lock file and no offline mode. Resolution runs on every build, reading
coursier's cache (`~/.cache/coursier`) for anything already fetched, so a warm build does
not hit the network — but it is not *prevented* from doing so, and nothing pins a
transitive against a repository that republishes one. Onion embeds coursier's Java API,
which does not expose a cache policy, so an `--offline` flag would have to be faked; it is
left out rather than made to look like a guarantee it is not.

## Source Layout

Production sources are every `.on` file under `src/`; tests are every
`_test.on` file under `tests/`. Both are discovered recursively, sorted by
their project-relative path, and never follow symbolic links. An empty
production source set is a build error; a missing or empty `tests/`
directory is a successful test run with a zero-test summary.

## Build Cache

`build` compiles once and caches the result under `target/`. A second `build`
(or the build that `run` and `test` perform first) reuses that output when a
SHA-256 fingerprint over the compiler version, the Java version, the exact
manifest bytes, and every source path and its bytes still matches:

```text
$ onion build
Built hello (1 classes)
$ onion build
Built hello (cached)
```

Any source addition, removal, rename, or content change invalidates the
cache and triggers a full rebuild. A failed rebuild never destroys the
previous successful `target/classes` or build-state file — the new output is
staged and only promoted after every artifact is written successfully.

## Entrypoint Convention

Because the compiler always emits a JVM `main` for every top-level source, a
class file alone can't tell a real entry point from a helper source. Project
builds instead look at the parsed source:

1. `src/main.on` is a candidate whenever it has at least one top-level
   statement (including a bare expression or variable declaration).
2. Any production source that declares a top-level function named `main` is
   a candidate.
3. Every other compiler-generated `main` is ignored.

`run` requires exactly one candidate. Zero candidates fail with a hint to add
executable code to `src/main.on` or define a top-level `main`; more than one
candidate fails and lists every candidate's source and location so the
ambiguity can be resolved.

## Testing

`test` builds production sources once, then compiles and runs each discovered
test file in sorted order, sequentially:

```text
$ onion test
test tests/main_test.on ... ok

1 tests, 1 passed, 0 failed
```

A test's own `stdout`/`stderr` are shown only when it fails, unless
`--verbose` is given. One test's compile failure, assertion failure, runtime
exception, or nonzero numeric result is reported against that test alone —
every remaining test still runs, and a project with no tests at all succeeds
with a `0 tests, 0 passed, 0 failed` summary.

## Clean

`clean` removes exactly the project's canonical `target/` directory:

```text
$ onion clean
Cleaned target
```

It requires a valid, located project first, succeeds (printing the same
message) whether `target/` exists or not, and refuses to touch a `target`
that is itself a symbolic link rather than following it.

## Script and REPL Compatibility

`new`, `build`, `run`, `test`, and `clean` are reserved only as the exact
first argument. Anything else — including a file that happens to be named
`build.on`, or `--` prefixed options — is passed through unchanged to the
existing script runner, and `onion repl [options...]` still starts the
interactive shell.

## Deferred

The following are intentionally out of scope for this first version, to keep
`onion.toml` from becoming another large build language before real usage
justifies it: publishing, dependency lock files and offline resolution, multiple modules
or workspaces, configurable source/test/output/entrypoint paths, incremental
(per-file) or parallel compilation, a project watch mode, test annotations or
a new test framework, package/archive commands, formatter or linter
integration, lifecycle hooks or manifest scripts, terminal color, and an
`onion init` for adopting an existing directory.

## Next Steps

- [Script Runner (onion)](script-runner.md) - Run a single Onion file directly
- [Compiler (onionc)](compiler.md) - Compile to class files
- [REPL Shell](repl.md) - Interactive programming
