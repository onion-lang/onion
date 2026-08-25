# Maintainability refactoring baseline

This baseline records the repository state before production-code changes in the
maintainability program. Generated logs live under `target/refactoring-baseline/`
and are intentionally not tracked.

## Repository state

- Branch: `refactor/onion-maintainability`
- Base branch: `develop`
- HEAD: `78973f25228d7f3009e210fb27e5b96054f7b4ac`
- HEAD subject: `chore(release): cut v0.19.0`
- Worktree: isolated linked worktree
- Initial tracked-tree state: clean
- The user-supplied prompt remains untracked in the original checkout and is not
  part of this branch.

## Toolchain and host

- Host: Ubuntu 22.04.5 under WSL2, Linux 6.18.33.2
- CPU allocation: 16 logical processors
- Memory allocation: 30 GiB RAM, 8 GiB swap
- Java: Eclipse Temurin 25.0.1
- sbt: 2.0.6, runner script 1.11.3
- Scala: 3.3.7
- Java source level: 17
- JavaCC: 5.0
- ASM: 9.8

The host exports `XDG_RUNTIME_DIR=/run/user/1000`, but that directory does not
exist. sbt 2's experimental native thin client consequently fails before the
build starts. A writable temporary runtime directory fixes the first socket
error, but the native client still fails to connect. The JVM runner succeeds,
so all measurements use `sbt --server`. This changes only the launcher path;
the project still runs sbt 2.0.6 with the repository's `.jvmopts`.

## Fresh baseline

The sequence was:

```text
sbt --server shutdown
sbt --server -Duser.language=en testFull
sbt --server shutdown
sbt --server -Duser.language=ja testFull
sbt --server compile
git diff --check
git status --short --branch
```

| Command | Result | Wall time | Maximum RSS |
|---|---|---:|---:|
| English `testFull` | 4,132 passed, 0 failed, 1 cancelled | 2:35.60 | 8,919,052 KiB |
| Japanese `testFull` | 4,132 passed, 0 failed, 1 cancelled | 1:33.37 | 8,954,704 KiB |
| Warm `compile` | success | 0:05.62 | 521,056 KiB |

The cancelled test is
`ProjectDistributionSpec: an unpacked distribution completes the new -> run -> test -> clean journey`.
It is opt-in and requires `-Donion.dist.path=<unpacked dist dir>`. This is the
same deliberate distribution gate described by the existing quality bar, not a
locale-dependent cancellation.

English and Japanese suites have identical suite and test counts. The Japanese
run reused the compiled build graph but ran in a separate JVM, as required for
locale isolation.

`git diff --check` was clean and the tracked tree remained unchanged after the
baseline.

## Existing warnings

A fresh compile reports warnings before any refactoring:

- JavaCC: five choice conflicts in `grammar/JJOnionParser.jj`.
- Scala main sources: three unreachable pattern cases and five non-local
  returns.
- Java main sources: one unchecked raw `Function1` call in `Concurrent.java`.
- Scala tests: four deprecated `Array` implicit conversions in
  `OnionCliSpec.scala`.
- sbt lint: `repl / fork` and `run / connectInput` are not consumed by another
  setting or task.

These are baseline debt, not regressions introduced by this program. They must
be removed or explicitly accepted before warnings become a hard CI gate.

## Source and history snapshot

Run:

```text
scripts/maintainability/audit.py --output-dir target/maintainability --max-commits 200
```

The audit found 330 tracked hand-written main-source files and 54,630 LOC. It
excludes generated output under `target/`. Its history window contains 186
commits that touched current main-source paths.

| File | LOC | Branch proxy | Commits | Churn | Interpretation |
|---|---:|---:|---:|---:|---|
| `compiler/Rewriting.scala` | 1,682 | 389 | 11 | 568 | Largest behavior-heavy hotspot |
| `compiler/TypedAST.scala` | 1,868 | 196 | 2 | 26 | Large but comparatively stable definition hub |
| `compiler/Parsing.scala` | 744 | 158 | 36 | 638 | Highest recent edit frequency; parser I/O and hint policy are mixed |
| `backend/asm/AsmCodeGeneration.scala` | 1,032 | 227 | 8 | 275 | Large backend implementation behind an existing facade |
| `typing/TypingOutlinePass.scala` | 865 | 148 | 11 | 257 | Active typing hotspot with broad semantic reach |
| `SemanticErrorReporter.scala` | 805 | 66 | 19 | 195 | Frequently changed diagnostic hub |

The score in `audit.json` is a ranking heuristic, not a quality gate. Raw LOC,
branch proxy, dependency fan-in/out, commit count, churn, and co-change counts
remain available separately so decisions do not depend on one opaque number.

## Hygiene findings

- `project/.build.properties.un~` is a tracked Vim binary undo file containing
  obsolete `sbt.version=0.13.x` edit history. It is not referenced by the build.
- `.gitignore` does not currently reject the general `*.un~` backup pattern.
- Generated parser sources correctly live under `target/` and are not tracked.
