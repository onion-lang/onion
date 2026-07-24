# Onion Practical Readiness Foundation

- **Date:** 2026-07-24
- **Status:** Approved design
- **Primary first-milestone workload:** scripts, command-line tools, and data
  automation

## Purpose

Onion already has a substantial language implementation: a multi-phase JVM
compiler, a runtime library, a script runner, a REPL, an LSP server, a VS Code
extension, release automation, hundreds of test suites, a crash corpus, a
mutation fuzzer, and English and Japanese documentation. The next step is not
to add features indiscriminately. It is to make the implementation measurably
usable, fast, documented, installable, and reliable for a defined workload.

The current evidence is not sufficient to make that claim:

- `docs/quality-bar.md` is a dated hand-maintained snapshot. It has no
  compilation-latency thresholds and some of its documented counts are stale.
- `BenchmarkRunner` labels a JVM-warmed, fresh-compiler operation as a warm
  compile, uses `DataClass.on` as a large input even though it is 73 lines, and
  reports only arithmetic means.
- The documentation contains 819 `onion` code fences, while
  `DocExamplesCompileSpec` checks only filename-labelled complete examples
  under `docs/examples/` and `docs/ja/examples/`.
- Installation and tooling documentation already contradicts repository
  capabilities, including the status of the VS Code extension and language
  server.
- The main CI workflow runs tests but does not enforce documentation example
  correctness, release-install journeys, or performance regression limits as
  first-class readiness dimensions.

This design establishes trustworthy readiness evidence before compiler
optimization and documentation repair. It is the first independently
shippable milestone in the broader practical-language program.

## Program Decomposition

The complete practical-language objective is split into five ordered
milestones:

1. **Readiness foundation.** Establish executable correctness, documentation,
   performance, packaging, and reporting gates.
2. **Compilation speed.** Profile realistic workloads, optimize measured
   bottlenecks, and meet the practical latency budgets in this document.
3. **Documentation truth and learning path.** Repair contradictions and make
   install-to-use journeys executable against distribution artifacts.
4. **Practical developer experience.** Verify compiler, runner, REPL, LSP, VS
   Code integration, diagnostics, and release archives on supported systems.
5. **Application reliability.** Exercise representative multi-file
   applications and establish compatibility and versioning policy.

Each milestone gets its own implementation plan and completion audit. Finishing
the readiness foundation does not complete the broader objective; it creates
the evidence required to complete and verify the later milestones.

## Approach

Three approaches were considered:

1. **Readiness gates first (selected).** Make quality and performance claims
   executable before optimizing or expanding the language.
2. **User journey first.** Polish installation and tutorials immediately, but
   risk hiding compiler latency and correctness gaps behind presentation.
3. **Compiler first.** Optimize immediately, but risk tuning misleading
   microbenchmarks and leaving documentation claims unverifiable.

The selected approach minimizes speculative work. Performance changes are
chosen from profiles, documentation work is driven by a complete inventory,
and every later claim has an authoritative check.

## Scope of This Milestone

The readiness foundation will:

- define stable benchmark workloads and honest execution semantics;
- retain raw timings and compute robust summary statistics;
- produce versioned, machine-readable readiness evidence;
- classify every Onion documentation fence and verify all declared outcomes;
- express the practical-quality policy as typed, executable rules;
- provide deterministic checks separately from noisy performance measurements;
- add pull-request, nightly, and release CI lanes;
- smoke-test a built distribution in an isolated directory; and
- capture a trustworthy performance baseline.

This milestone will not:

- introduce compiler caches or incremental compilation before profiles justify
  them;
- claim that Onion meets the performance targets merely because measurement
  exists;
- rewrite all user documentation before the verifier identifies its exact
  failures;
- add unrelated language features; or
- replace the crash, fuzzing, soundness, and code-generation tests that already
  exist.

## Architecture

### Package and Artifact Boundaries

Readiness implementation code lives under
`src/main/scala/onion/tools/readiness/`. It is split into independent units:

- `scenario` owns workload descriptions and execution semantics;
- `benchmark` owns raw timing collection and statistics;
- `docs` owns Markdown directive extraction and example execution;
- `policy` owns readiness requirements and evaluations; and
- `report` owns versioned JSON and human-readable rendering.

Tests mirror those boundaries under
`src/test/scala/onion/tools/readiness/`. Dedicated, stable performance inputs
live under `benchmarks/fixtures/`; they are not mixed with illustrative
`run/` samples. Reports are written under `target/readiness/` and are never
treated as source files.

`onion.tools.BenchmarkRunner` remains as a compatibility entry point and
delegates to the new benchmark package. The existing `bench` task remains an
alias for the new `benchmark` task during the transition.

### Scenario Catalog

The catalog contains the following distinct scenario kinds:

1. **Process-cold command.** An outer launcher starts a new JVM and invokes the
   built `onion` or `onionc` command. The duration includes JVM startup,
   compiler initialization, source I/O, compilation, and command shutdown.
   Process-cold time cannot be measured by code already running inside that
   JVM.
2. **Steady-state fresh compiler.** A warmed benchmark JVM creates a fresh
   compiler invocation for each measured iteration. This measures JIT-warmed
   compilation without implying persistent compiler state.
3. **Persistent session.** Consecutive snippets are submitted to a real REPL
   session. Setup is excluded and the measured operations include the growing
   session state.
4. **Multi-file compilation.** A dedicated 20-file, approximately 2,000-line
   fixture is compiled as one project to expose scaling and shared-symbol
   costs.

The single-script catalog uses:

- `run/Hello.on` as the minimal script;
- `run/TodoManager.on` as the medium practical script; and
- `run/StatsApp.on` as the large practical script.

Names in reports describe the execution model and workload explicitly, for
example `process-cold:onion:hello` and
`steady-fresh:onionc:stats-app`. Terms such as `warm` and `cold` may not appear
without the corresponding lifecycle definition in scenario metadata.

### Measurement Engine

Every scenario records raw iteration durations in nanoseconds. It also records:

- discarded warmup iterations;
- median, nearest-rank p95, minimum, and maximum;
- compiler phase timings where the execution model exposes them;
- source-file, line, byte, and generated-class counts;
- exit status and captured failure information;
- JVM vendor, JVM version, configured heap and garbage collector;
- operating system, architecture, and assigned processor count;
- Git commit, dirty-worktree flag, workload hash, and report schema version.

The median is the middle ordered observation, using the mean of the two middle
observations for an even sample count. The p95 is the observation at
`ceil(0.95 * n)` in one-based ordered indexing. No sample is removed as an
outlier. Warmup samples are retained in the report but excluded from summary
statistics.

Normal in-process scenarios use 8 warmups and 25 measured iterations.
Process-cold scenarios use 3 discarded launches and 25 measured launches.
Each iteration has a 30-second timeout. A timeout is a scenario failure, not a
slow numeric sample.

Timing and execution boundaries are injectable. Unit tests use fake clocks and
executors to prove lifecycle semantics without depending on wall-clock timing.

### Documentation Verifier

Every fenced block whose language is `onion` must have one HTML directive on
the immediately preceding non-blank line. The supported forms are:

```text
<!-- onion-example: compile -->
<!-- onion-example: run -->
<!-- onion-example: reject code=E0059 -->
<!-- onion-example: fragment reason="illustrates an expression in an existing scope" -->
```

Their meanings are:

- `compile`: the block is a complete program and must compile successfully.
- `run`: the block is a complete deterministic program and must compile, run,
  exit successfully, and match its declared output.
- `reject`: compilation must fail and include the exact declared diagnostic
  code.
- `fragment`: the block is intentionally incomplete and has a non-empty,
  human-readable reason.

A `run` block must be followed by an output declaration and a `text` fence:

````text
<!-- onion-output: stdout -->
```text
expected output
```
````

The output fence compares normalized LF line endings exactly. A second
`onion-output: stderr` block may follow when stderr is part of the example.
Absent stderr means stderr must be empty. The declared process exit is zero;
nonzero examples use `reject` and are compile-time examples in this milestone.

Examples execute in a new temporary directory with a five-second timeout.
They may not depend on an external network service and may access only fixtures
explicitly copied into that directory. A network example must use a
test-owned loopback service; the CI documentation job disables non-loopback
network access. A failure reports the Markdown path and fence line as well as
the Onion diagnostic location.

Unclassified Onion fences, unknown directives, empty fragment reasons,
duplicate directives, missing run output, and unsupported output streams are
validation errors. Nothing is inferred from a filename label or silently
skipped.

### Documentation Parity

Every published Markdown page in `mkdocs.yml` receives a stable
`onion_topic` identifier in YAML front matter. The English and Japanese
navigation trees must contain the same topic identifiers, parent
relationships, and sibling order. File names may differ. A deliberately
language-neutral artifact, such as generated API documentation, may be
referenced by both trees with the same identifier.

The parity check covers the complete published navigation, including
contributor and design pages. A page cannot be exempted by omission; a
language-specific note must have a corresponding page that explains the
language-specific content to the other audience.

Parity proves structure and coverage, not translation quality. Translation
quality remains a human review responsibility in the documentation milestone.

### Readiness Policy and Commands

`ReadinessPolicy` is the typed authority for thresholds and required checks.
Human documentation renders stable policy tables from this authority. It does
not commit fluctuating benchmark results as if they were current forever.

The public development commands are:

- `sbt readinessCheck`: run deterministic tests, documentation verification,
  sample checks, distribution smoke checks, parity checks, and policy-document
  freshness checks without modifying tracked files;
- `sbt benchmark`: run the performance suite and write a versioned JSON report
  under `target/readiness/`;
- `sbt readinessReport`: run `readinessCheck` and `benchmark`, then write
  versioned JSON plus a human-readable report under `target/readiness/`; and
- `sbt readinessPolicyDocs`: explicitly regenerate the stable policy portion
  of `docs/quality-bar.md`.

`readinessCheck` renders the expected policy documentation in memory and fails
when the committed file differs. CI never repairs tracked documentation
automatically.

`benchmark` and `readinessReport` exit nonzero for invalid measurements and
scenario failures. On a non-reference machine, absolute budgets are recorded
as `not-applicable`, the report is informational, and successful measurement
exits zero. On the detected reference lane, an absolute budget breach exits
nonzero. Relative comparison always enforces its regression policy when two
compatible commits were requested.

### Report Data Flow

The authoritative flow is:

```text
scenarios + documentation + tests + distribution
  -> isolated runners
  -> structured component results
  -> policy evaluation
  -> versioned JSON + human-readable report
```

The JSON top level contains:

- `schemaVersion`;
- `commit`;
- `dirty`;
- `generatedAt`;
- `environment`;
- `correctness`;
- `documentation`;
- `distribution`;
- `performance`;
- `policy`;
- `failures`; and
- `overallStatus`.

All durations use integer nanoseconds in JSON. Renderers may show milliseconds.
A breaking shape or semantic change increments `schemaVersion`. Readers reject
unsupported newer versions instead of guessing.

`overallStatus` is `pass`, `fail`, or `informational`. `informational` is valid
only when every executed check succeeded and the current environment is not
eligible for the absolute reference policy.

## Practical Performance Policy

### Reference Lane

Absolute performance is evaluated on:

- Ubuntu 24.04 x86-64;
- Temurin JDK 21;
- two assigned processor cores;
- 4 GiB available memory; and
- a 2 GiB maximum heap for in-process scenarios.

Process-cold scenarios use the distribution launcher's normal JVM settings.
In-process scenarios use fixed heap and garbage-collector settings recorded in
the report. Reports from other environments remain useful but cannot satisfy
the absolute reference policy.

### Absolute Ceilings

The first practical milestone requires:

| Scenario | Median | p95 |
|---|---:|---:|
| Fresh `onion Hello.on` process | 1.5 s | 2.5 s |
| Steady-state compile of `Hello.on` | 150 ms | 300 ms |
| Steady-state compile of `StatsApp.on` | 750 ms | 1.2 s |
| Subsequent REPL snippet | 100 ms | 250 ms |
| 20-file/~2,000-line project | 2.0 s | 3.0 s |

These are user-experience ceilings, not a description of the current
implementation. Baseline results determine the work required; they do not
weaken the ceilings.

### Relative Regression Rule

Head and base run as alternating pairs on the same worker. A scenario is a
regression when either condition holds:

- head is slower by more than both 10 percent and 20 ms at the median; or
- head is slower by more than both 20 percent and 50 ms at p95.

A breached scenario runs a second complete round. Repeating the same breach is
a performance failure. A non-repeating breach is an unstable-environment
failure and is reported with that category rather than attributed to the code.

Base and head reports are comparable only when schema version, workload hash,
JVM vendor and major version, OS, architecture, assigned processor count, heap,
and garbage collector match.

## Failure Semantics

The system uses four failure categories:

1. **Invalid measurement:** malformed configuration, missing workload, empty
   sample set, incompatible report schema, dirty required checkout, or mixed
   environments.
2. **Scenario failure:** compiler crash, unexpected exit, timeout, missing
   output, or incorrect program result.
3. **Unstable environment:** a threshold breach does not reproduce in the
   confirmation round.
4. **Budget failure:** valid, stable measurements exceed an absolute or
   relative limit.

The benchmark writes a partial report containing every completed observation
and failure before exiting nonzero. It never substitutes zero, discards a
failed iteration, or presents instability as success.

The readiness reporter refuses to merge results from different commits,
schemas, or incompatible environments. Missing evidence produces `unknown`,
which fails a required policy item; it never becomes `pass`. A policy item is
`not-applicable` only when its applicability rule is present and false, such as
an absolute performance ceiling evaluated on a non-reference machine.

## CI Design

### Pull Requests

Every pull request runs:

- the full ScalaTest suite with zero failed, canceled, ignored, or pending
  tests;
- sample compilation and deterministic execution;
- documentation directive and example verification;
- English/Japanese topic parity;
- `mkdocs build --strict`;
- distribution assembly and isolated smoke checks; and
- benchmark protocol unit tests.

Changes under compiler, runtime, grammar, build, benchmark fixtures, or command
launchers also run alternating base/head performance comparisons. Documentation
only changes do not build two compiler revisions for timing.

### Nightly

Nightly CI runs the complete absolute reference suite, retains the JSON and
human report artifacts, and retains enough history to inspect trends. A failed
or unstable run is visible as a failed job with its explicit category.

### Release

Release CI requires all deterministic readiness checks, the absolute
performance policy, and clean distribution journeys for compiler, runner,
REPL, and LSP. Existing artifact checksums and fat-JAR smoke tests remain.
A release is not practical-ready when required evidence is missing, even if
the ordinary test suite is green.

## Test Design

### Pure Unit Tests

- Parse every supported documentation directive and reject malformed forms.
- Preserve Markdown source locations through extraction.
- Calculate median and nearest-rank p95 for odd and even sample counts.
- Evaluate absolute and relative thresholds at, below, and above boundaries.
- Encode and decode every report field and reject incompatible schemas.
- Compare topic identifiers, parents, and order for parity.

### Protocol Tests

- Fake clocks prove warmups are excluded from summaries but retained in raw
  data.
- Fake executors prove fresh-process and steady-state scenarios use different
  lifecycles.
- Failed and timed-out iterations remain visible and fail the scenario.
- Incompatible environments cannot be compared.
- Phase timing totals and compilation totals remain internally consistent.
- A first breach triggers confirmation; repeated and non-repeated breaches get
  different categories.

### Compiler-Backed Documentation Tests

- Repository fixtures cover `compile`, `run`, `reject`, and `fragment`.
- Every repository Onion fence has exactly one valid classification.
- Every declared example produces its required result.
- Diagnostics point back to the Markdown fence and underlying Onion location.
- Network-dependent and nondeterministic examples must be fragments or be
  rewritten around local fixtures.

### End-to-End Tests

- Build and unpack the distribution in a fresh temporary directory.
- Invoke `onionc` and execute its generated classes.
- Invoke `onion` on the practical script corpus.
- Feed deterministic commands to the REPL and verify state retention.
- Perform LSP initialize, open, diagnostic, and shutdown exchanges.
- Generate a complete report and validate it against the current schema and
  policy.

### Existing Reliability Tests

Mutation fuzzing, the crash-reproducer corpus, code-generation correctness,
soundness probes, sample tests, and the full existing suite remain required.
The readiness layer aggregates their results; it does not duplicate or weaken
them.

## Implementation Order

1. Introduce readiness domain types and pure statistics tests.
2. Refactor benchmark scenarios behind injectable timing and execution
   boundaries while preserving the `bench` entry point.
3. Add honest process-cold, steady-state, REPL, and multi-file workloads.
4. Add report schema, rendering, and policy evaluation.
5. Add documentation directive parsing and fixture tests.
6. Classify all existing Onion fences and make declared outcomes pass.
7. Add topic identifiers and make the complete English/Japanese navigation
   trees structurally equivalent.
8. Add distribution smoke checks and public sbt tasks.
9. Replace the dated quality snapshot with generated policy documentation.
10. Add pull-request, nightly, and release CI lanes.
11. Capture the reference baseline and publish it as a CI artifact.
12. Start the compilation-speed milestone by profiling the weighted practical
    scenarios and selecting the largest contributor.

## Readiness-Foundation Completion Evidence

The first milestone is complete only when all of the following evidence exists:

- `sbt readinessCheck` passes from a clean checkout.
- `sbt benchmark` emits a schema-valid report containing every scenario and all
  raw observations.
- `sbt readinessReport` produces internally consistent JSON and human output.
- Every Onion documentation fence is classified and every non-fragment
  declaration verifies.
- The complete published English and Japanese navigation trees have structural
  topic parity.
- `mkdocs build --strict` passes.
- A built distribution completes isolated compiler, runner, REPL, and LSP
  journeys.
- Pull-request, nightly, and release workflows implement their specified
  gates.
- A reference-lane baseline is captured with environment and workload hashes.
- `docs/quality-bar.md` contains the current generated policy and no stale
  hand-maintained status counts.

## Broader Program Completion

The practical-language objective is complete only after later milestone audits
also prove:

- every absolute compilation and interaction budget passes;
- the largest measured compile-time bottlenecks have been addressed without
  correctness regressions;
- installation, first program, CLI/data application, diagnostics, tooling, and
  release journeys are current and executable;
- supported release archives work on Linux, macOS, and Windows;
- representative multi-file applications pass compatibility and reliability
  suites; and
- the versioning and backward-compatibility policy is documented and enforced.
