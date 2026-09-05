# Compilation-time halving work log

Objective: reduce Onion compilation time to half of the starting implementation,
without changing accepted programs, diagnostics, generated code, or correctness
checks. This objective is **not yet achieved**.

## PR checkpoint

The halving investigation is paused at the user's request. This PR preserves
bounded allocation improvements, regression tests, and the investigation record;
it does **not** claim a general compilation-time speedup or a twofold improvement.
The retained changes avoid exponential parameter-signature expansion, share fixed
ASM descriptors, avoid argument-array clones during method comparison, reject
impossible arities before specialization, lazily erase override implementations,
and store singleton local bindings inline. The singleton scope tradeoff is
explicit: fewer allocations for one binding, a larger object for other shapes,
and no established whole-workload improvement.

Historical sections below include rejected experiments and superseded hypotheses.
Scratch drivers, raw logs, JFR recordings, and experimental jars referenced under
`target/` are local investigation artifacts, not tracked or shipped by this PR.
The existing benchmark runner and regression tests remain the reproducible public
entry points. No launcher, GC policy, native-image toolchain, or distribution
change is included.

## Reference measurement

Starting commit: `c9e77b96af44ee32a429166171e06991d6022b5b`.
Measured on 2026-09-05 with JDK 25.0.1, using the existing schema-3 benchmark:

```sh
sbt --server 'runMain onion.tools.BenchmarkRunner --warmups 25 --iterations 25 --output target/compile-baseline.json'
```

Each in-process iteration creates a new compiler and rereads the source. The
existing platform-class metadata sharing remains enabled in both reference and
candidate. No result caching, skipped checks, changed input corpus, or extra
candidate-only warmup can count toward the target.

| Scenario | Baseline median | Half-time target |
|---|---:|---:|
| Fresh Hello | 3.169 ms | 1.584 ms |
| Fresh TodoManager | 18.621 ms | 9.310 ms |
| Fresh StatsApp | 17.037 ms | 8.519 ms |
| Fresh 20-file automation project | 31.287 ms | 15.643 ms |
| Process-cold Hello (benchmark driver) | 949.635 ms | 474.818 ms |
| Persistent growing REPL | 14.235 ms | 7.118 ms |

The CLI benchmark driver is not the installed shell launcher: launcher GC/CDS
settings must also be measured before claiming installed-command improvements.
The persistent REPL includes execution/protocol costs, so it is tracked
separately from fresh compilation, not substituted for it.

Typing accounts for about 70% of the nontrivial fresh workloads' mean phase time.
On the 20-file workload the means are typing 20.657 ms, parsing 4.708 ms,
bytecode generation 3.708 ms, rewriting 0.713 ms; other phases are smaller.

## CPU profile and next experiments

An ignored `target/CompileProfile.java` driver runs the unchanged multi-file
scenario with 200 warmups and 1,500 fresh compilations, recording JFR only after
warmup. This is a profiling probe, **not** a comparable acceptance benchmark:
its lower mean (13.987 ms) is not an implementation speedup.

The profile is distributed across name resolution, capture scanning, binding
registration, method comparison, duplication checks, parsing, and ASM. Two
specific avoidable computations deserve bounded experiments:

- `NameResolver.map` constructs alias-qualified names even with an empty alias
  registry. Skip only that absent-feature work, preserving alias resolution
  whenever the registry is populated.
- `TypingDuplicationPass.generateForwardedMethods` constructs all erased method
  signatures even when there are no forwarded fields. Gate this work without
  bypassing override, abstract-method, or erasure-collision checks.

Before retaining either change, run the relevant alias/forwarding regressions,
compare the same fixed benchmark protocol, and check generated classes. Final
acceptance requires repeated before/after measurements, both locale full suites,
and evidence across the representative workload set, not one lucky sample.

Raw reference JSON, JFR, and exploratory JVM-policy samples live under `target/`.

An alternating-order fresh-process probe compared default JIT policy with
`-XX:TieredStopAtLevel=1` over 10 measured pairs per workload (two pairs warmed
filesystem state first). Ratios were Hello 0.794, TodoManager 0.836, StatsApp
0.730, multi-file 0.819; emitted classes matched in each case. This used loose
class files, not the distribution's jars/CDS archive, and is exploratory only.
It neither reaches half-time nor justifies changing long-lived REPL/LSP/runtime
JIT policy; no launcher setting has been changed.

## Empty-alias probe (first paired run)

Both artifacts used the same jar packaging and 25 warmups / 25 measurements.
The candidate ran first, then the reference; both reports contain no scenario
failures. These are exploratory observations, not a confirmed speedup: order
effects and JIT variation still require repeated, counterbalanced runs.

| Scenario | Reference median | Empty-alias candidate median |
|---|---:|---:|
| Fresh Hello | 4.842 ms | 4.401 ms |
| Fresh TodoManager | 19.459 ms | 18.312 ms |
| Fresh StatsApp | 23.506 ms | 19.194 ms |
| Fresh 20-file automation project | 28.522 ms | 25.947 ms |
| Process-cold Hello | 997.694 ms | 1007.260 ms |
| Persistent growing REPL | 17.797 ms | 13.975 ms |

Raw reports: `target/alias-reference.json`, `target/alias-candidate.json`.
The reference is the pre-edit `target/compile-base.jar` (SHA-256
`f2049f64596201f2dcf1e0eb335b33e8351d78a929da7035bf2a453f59f78449`),
not the dirty checkout described by the reports' Git metadata. The candidate
is `target/compile-alias-candidate.jar`, containing only the empty-alias fast
paths as production changes. The half-time goal remains unmet.

## Empty-alias plus forwarding gate: warmed ABBA probe

A fresh-compile-only driver used the existing scenario implementations, 200
warmups and 100 measurements per scenario, in separate JVMs ordered reference,
candidate, candidate, reference. This is a different warmup protocol from the
acceptance baseline, so its smaller absolute times are not an improvement.

| Scenario | Reference A1 | Candidate B1 | Candidate B2 | Reference A2 |
|---|---:|---:|---:|---:|
| Hello | 2.601 ms | 3.035 ms | 2.874 ms | 2.475 ms |
| TodoManager | 7.330 ms | 7.726 ms | 6.428 ms | 7.062 ms |
| StatsApp | 5.884 ms | 6.671 ms | 5.719 ms | 7.148 ms |
| 20-file project | 9.755 ms | 9.135 ms | 9.576 ms | 10.674 ms |

The small workload is slower in both candidate runs, while the multi-file
workload is faster. TodoManager and StatsApp remain noisy. The candidates are
therefore not yet justified as a general compilation-speed improvement and
have not been committed or shipped. Raw TSVs are `target/fast-{base,candidate}-*.tsv`;
each includes workload hashes and all measured samples.

The forwarding guard passed all 15 alias/forwarding checks. An unconditional
return mutation failed six forwarding tests, including the new mixed-class
case; restoring the feature-sensitive gate returned the suite to green.
Independent review found no correctness blockers in either candidate.

The next algorithmic investigation is `allErasedParamDescriptors`: abstract
and override checks eagerly enumerate up to 2^k boxed/primitive descriptor
combinations for k convertible parameters. Comparing actual same-name,
same-arity candidates position-wise could remove this exponential work, but
must preserve the distinction between symmetric override matching and the
one-sided abstract-contract matching. A high-arity workload will supplement,
not replace, the existing representative benchmark set.

The empty-alias and no-forwarding candidates have now been removed from the
working implementation (including their optimization-specific name-resolution
test). Their benchmark artifacts and observations remain available. The
mixed-class forwarding regression remains relevant to abstract-contract checks.

## Position-wise parameter patterns

`ErasedParameterPattern` replaces complete signature products with separate
descriptor sets at each argument position. Override targets compare positional
intersection; abstract implementation checks keep the implementation raw and
test membership in the specialized contract pattern. Candidate lookup uses
name and arity; all eligible base names remain available for suggestions.

The first independent review found no correctness blockers. The new pattern
and wide-interface suites plus existing override/forwarding suites passed 25
tests. An always-match mutation failed six tests, including the real compiler's
rejection of a last-argument mismatch, and the restored implementation passed.

Six alternating-order cold-JVM compilations of `target/WideContractProbe.on`
(16 primitive arguments, interface implementation with `override`) produced
the following internal compiler timings, in milliseconds:

- Reference: 1289.631, 1293.223, 1248.389, 1363.779, 1403.905, 1342.537.
- Pattern-only candidate: 654.000, 605.730, 586.780, 626.344, 603.031, 803.576.

All invocations exited successfully and the three generated class files were
byte-identical. These timings exclude JVM startup; this deliberately stressful
workload does not substitute for the representative corpus or the half-time
goal. Raw reports are `target/wide-{base,pattern-only}-[1-6].json`.
The isolated candidate jar changes only DuplicationChecks and adds the pattern
class to the immutable reference jar; its SHA-256 is
`db23810d6f25e1b522b137d87c08b3f3ae2cb925c3529324c3ab59299f2849c4`.

The pattern-only warmed ABBA probe (200 warmups, 100 measurements) was
inconclusive on the representative corpus:

| Scenario | Reference A1 | Pattern B1 | Pattern B2 | Reference A2 |
|---|---:|---:|---:|---:|
| Hello | 5.202 ms | 4.549 ms | 4.579 ms | 2.613 ms |
| TodoManager | 10.698 ms | 11.220 ms | 10.584 ms | 8.230 ms |
| StatsApp | 9.901 ms | 9.725 ms | 9.855 ms | 6.623 ms |
| 20-file project | 14.264 ms | 14.853 ms | 14.700 ms | 9.556 ms |

The reference itself varied substantially between runs. This does not establish
either a general improvement or non-regression; further controlled measurement
is required. Raw samples and workload hashes are in `target/pattern-fast-*.tsv`.
A separate baseline/candidate comparison of equal-distance `get`/`got`
suggestions for erroneous `override def gat` produced identical diagnostics.

The retained source (without the two earlier micro-optimizations) passed fresh
English and Japanese `testFull` runs: 5,233 tests passed in each locale, zero
failures, and one opt-in distribution test canceled. A final independent
review found no findings and approved a checkpoint, not overall goal completion.

A diagnostic paired-classloader run used two independent compiler universes in
one JVM, alternating the invocation order on every iteration (500 warmups and
200 measurements). Candidate/reference median ratios were Hello 0.967,
TodoManager 1.071, StatsApp 1.010, multi-file 1.073. This is not the acceptance
protocol; a reversed-loader run is needed to check loader/JIT ordering bias.
Raw measurements are in `target/paired-pattern-probe.log`.

With loader roles reversed, the candidate/reference ratios (inverted from the
driver's printed second/first ratios) were Hello 0.985, TodoManager 1.019,
StatsApp 0.990, multi-file 1.023. The ordinary workloads still do not show a
general speedup; the algorithmic win is currently confined to wide contracts.
See `target/paired-pattern-reverse.log` for all samples.

A separate 3,000-iteration Hello profile after 200 warmups yielded a mean
1.529 ms, with typing 0.786 ms, code generation 0.348 ms, and parsing 0.211 ms.
This again is not an acceptance comparison. JFR's first Onion frame counts
put codegen construction (46), ImportItem construction (42), AstBindingIndex
construction (35), and TypingBodyPass construction (25) among the hot paths.
The next probes will target shared initialization cost, including immutable
default imports and oversized initial binding tables, rather than assume
that the wide-contract improvement applies to Hello.

The diagnostic paired-loader comparison of existing parallel class generation
against `-Donion.codegen.sequential=true` did not support disabling parallelism:
sequential/parallel median ratios were Hello 1.043, TodoManager 1.071, StatsApp
1.078, and multi-file 1.211. The source default remains unchanged. Raw samples
are in `target/paired-sequential-probe.log`.

Next implementation probe: move the fourteen immutable built-in `ImportItem`s
to the header pass companion while constructing a fresh per-unit buffer for
the module and explicit imports. The objects contain only immutable names,
segments, and precomputed matching strings; no resolved classes or unit state
may be shared. Regression tests first check cross-unit and cross-compilation
import isolation and repeated use of default aliases. Then compare the same
immutable baseline artifact before retaining this allocation optimization.

The strengthened import isolation tests passed (17 tests). Paired-loader
candidate/reference ratios with reference loaded first were Hello 1.089,
TodoManager 1.009, StatsApp 0.965, multi-file 0.935. With candidate loaded
first, the ratios were 0.913, 1.053, 0.980, 1.011, respectively. These mixed
results do not establish a general improvement; the import candidate remains
uncommitted and is not counted toward the goal. Logs: `paired-import-probe.log`
and `paired-import-reverse.log` under `target/`.

Allocation sampling in the same Hello JFR attributes about 46.5 MB over 3,000
iterations to `AstBindingIndex` construction. Its identity table is eagerly
presized to 1,024 entries. The next isolated experiment reduces initial size
without changing identity semantics or unlimited growth, checking both tiny
and multi-file workloads before choosing whether to retain it.

The 128-entry experiment reduced measured empty-index allocation from 16,480
to 2,144 bytes (JDK thread allocation counter, 10,000 warmups and 10,000
allocations). Identity/growth/view-copy and import-isolation tests passed,
five tests total. The isolated artifact is `compile-binding128-only.jar`,
SHA-256 `5d79da5566339fc075681581f56bb908526ab000265446b3ee30c8a852aef052`.

However, candidate/reference time ratios were 1.022 / 0.974 / 1.008 / 1.093
for Hello / TodoManager / StatsApp / multi-file. Reversed loader order gave
0.973 / 1.053 / 1.042 / 1.041. This is not a speed improvement: multi-file
regressed in both orders. Both the capacity experiment and default-import
sharing have been removed from production source. The boundary tests remain.
An unrelated agent process was active during this diagnostic measurement,
another reason not to interpret small differences as authoritative; neither
run supports counting this experiment toward halving compilation time.

Raw data: `target/paired-binding128-probe.log`,
`target/paired-binding128-reverse.log`; allocation failure evidence:
`target/binding-allocation-red.log`. The next work should target repeated
computation rather than further eager-allocation micro-optimizations alone.

After restoring both production files exactly to the checkpoint, a fresh
targeted run passed 19 tests across the binding/import boundaries, erased
parameter patterns, wide contracts, and forwarded generic interfaces.
Log: `target/restored-performance-contracts.log`. No benchmark process remains
running from these two experiments.

## Capture-scan upper-bound diagnostic (not an implementation)

A throwaway ASM transformer replaced only `CapturedVariableScanner.scanElements`
with an empty-set return in a copy of the reference jar. This intentionally
invalid compiler is named `target/compile-INVALID-no-capture.jar`; it must
never be shipped or counted as an optimization. Production source is untouched.
The diagnostic asks whether removing this entire traversal would be a large
enough win to justify parser-provided closure metadata.

With the same 500/200 paired protocol, invalid/reference median ratios were
Hello 1.022, TodoManager 1.002, StatsApp 0.951, multi-file 1.010. With reversed
loader order they were 1.065, 1.030, 1.008, 0.940. This fails to demonstrate
a large benefit even when the entire computation is omitted. It does not
justify a cross-parser metadata change as the next route to halving time.
Logs: `target/paired-no-capture-probe.log` and
`target/paired-no-capture-reverse.log`. Both processes exited successfully;
class-count checks are not a correctness proof for this deliberately invalid
variant.

Another repeated computation remains visible in the source/profile:
`AsmCodeGeneration` reparses twelve constant ASM method signatures in every
instance (even without records). This is a potential next probe, not a proven
win. Inspection of `MultiTable.values` ruled out another apparent hotspot:
its flattened method list is already cached and invalidated by `add`, so
`ClassDefinition.methods` must not be described as rebuilding it each time.

## Cache-local candidate filtering

Following the suggestion to improve CPU cache locality, inspection found
that ordinary static calls and static bidirectional inference rebuild a
`TreeSet` from an already sorted, deduplicated candidate array. The bounded
experiment filters that array into an immutable `ArraySeq` instead. It keeps
the existing first-wins representative, filters after deduplication, and does
not modify the cached array. Named-argument and other call paths are unchanged
for the initial measurement.

`FilteredMethodCandidatesSpec` first failed because the helper was absent.
After implementation, 39 targeted tests passed, including SAM disambiguation,
overload-bound isolation, primitives and varargs. Independent read-only review
found no Critical/Important/Minor issues and approved correctness, not speed.
The isolated jar is `target/compile-flat-static-only.jar`, SHA-256
`06d24fb2f7a0a45b8bf30f9755a59a1015f8a96773cf92f2d3ef1766fc4a1f24`.

The installed `perf` wrapper lacks a binary for the WSL2 kernel, so hardware
cache-miss counts are unavailable. Wall-time differences cannot be attributed
specifically to CPU cache locality without that evidence; this experiment
also removes repeated comparisons and tree-node allocations.

The first paired-loader observations gave candidate/reference ratios of
1.001 / 0.967 / 1.009 / 1.006 for Hello / TodoManager / StatsApp / multi-file.
Reversed roles gave 0.959 / 0.945 / 0.979 / 0.976. TodoManager improved in
both orders, but these small changes do not establish a general speedup.
In addition, JFR log inspection invoked a separate JVM briefly during the
diagnostic run; this is measurement interference, so a clean repeated timing
run is required before any performance acceptance. No result here establishes
halving. Raw logs: `target/paired-flat-static-probe.log` and
`target/paired-flat-static-reverse.log`.

A follow-up environment check found an installed older perf binary at
`/usr/lib/linux-tools/5.15.0-190-generic/perf`. Invoking it directly, bypassing
the kernel-name wrapper, successfully measured user-mode cycles, instructions,
and cache misses for a smoke command. Thus PMU measurement is available without
installing packages or changing kernel permissions. A throwaway
`CompileCounterProbe.java` will use perf's acknowledged control FIFOs to enable
counters only after warmup and disable them after the measured batch. Counts
will include compiler helper/JIT/GC threads active in that interval, not only
the invoking thread. The generic cache-miss event is not a direct measure of
all cache levels and cannot alone prove causality.

Fresh full validation of the flat-static source passed in English and Japanese:
5,240 tests each, zero failures, one opt-in distribution test canceled, 718
suites completed. Logs: `target/flat-static-full-en.log` and
`target/flat-static-full-ja.log`.

The perf control smoke initially failed the second acknowledgement check.
Instrumenting the raw response revealed `[0,97,99,107]`: this perf build emits
`ack` followed by newline and NUL, leaving the NUL at the start of the next
line read. The throwaway driver now consumes that optional framing NUL and
still requires an exact acknowledgement. The repeated smoke exited zero,
compiled ten classes in ten iterations, and collected all four PMU events
at 100% running time. This validates the measurement plumbing, not a speedup.
Evidence: `target/perf-control-debug.log`, `target/perf-control-green.log`,
`target/perf-control-green.stat`.

The eight PMU-controlled runs completed successfully, with 500 warmups and
500 measured fresh compilations per run. All four events reported 100%
running time (no multiplex scaling). ABBA results:

| Scenario / variant | Batch ms | Instructions (billions) | Cache misses (millions) |
|---|---:|---:|---:|
| Hello baseline A | 254.054 | 1.857 | 45.626 |
| Hello flat A | 256.461 | 1.872 | 44.442 |
| Hello flat B | 244.387 | 1.600 | 43.153 |
| Hello baseline B | 344.612 | 1.803 | 45.234 |
| 20 files baseline A | 4685.957 | 61.136 | 209.237 |
| 20 files flat A | 4825.155 | 59.772 | 227.256 |
| 20 files flat B | 4523.339 | 60.753 | 202.385 |
| 20 files baseline B | 4774.204 | 66.750 | 212.931 |

These are batch observations, not per-iteration medians. Hello misses were
lower in both candidate runs, but multi-file misses and wall time were mixed.
JIT/GC/helper-thread work is included. The evidence does not prove a general
cache-locality speedup or halving; the static candidate remains provisional.
Raw counts, cycles, and class-count checks are in `target/perf-flat-*.stat`
and `target/perf-flat-*.log`. No measurement process from this batch remains.

## JDK 25 AOT training probe

The JDK 25 [java command reference](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html)
documents AOT cache training and reuse for startup/warmup. A throwaway jar
containing only `CompileFastBench` trained the **unchanged reference compiler**
with 500 warmups / 100 samples across the existing four fresh scenarios.
`-XX:AOTMode=record -XX:AOTCacheOutput=target/compiler-training.aot` produced
a 39,940,096-byte cache. Training and assembly exited zero; unsupported
unlinked classes were explicitly skipped by the VM, not silently assumed
cached. This is JVM optimization metadata, not an Onion source/result cache.

The same reference compiler and classpath were then compared with/without
`-XX:AOTMode=on -XX:AOTCache=target/compiler-training.aot`, each with 25 warmups
and 25 measurements. Median milliseconds in ABBA order:

| Scenario | Baseline A | AOT A | AOT B | Baseline B |
|---|---:|---:|---:|---:|
| Hello | 4.722 | 6.287 | 5.645 | 4.446 |
| TodoManager | 20.655 | 16.031 | 16.930 | 17.919 |
| StatsApp | 23.582 | 13.989 | 14.289 | 19.112 |
| 20 files | 19.172 | 18.155 | 14.019 | 18.970 |

The more substantial workloads improve, but Hello regresses. This is not a
general-halving result and no launcher/default has changed. Cold ScriptRunner
execution is the next diagnostic; deployment would additionally need fallback,
JDK/classpath/archive compatibility and installed-launcher parity validation.
Raw evidence: `target/aot-training.log`, `target/aot-compare-*.log`.

Four alternating-order cold ScriptRunner pairs all exited zero with exact
`Hello\n` output. Reference wall times were 1.15 / 1.13 / 1.14 / 1.24 seconds;
AOT times were 0.75 / 0.73 / 0.73 / 0.68 seconds (external time resolution
0.01 seconds). Median 1.145 to 0.730 seconds is about 36% lower, not halved.
This baseline uses the benchmark's plain JVM path, **not** the already
CDS-enabled installed launcher. A classic-CDS control is required before
claiming an improvement over the installed product. Files:
`target/aot-cold-*.time`, `.out`, `.err`. All processes are complete.

**Timing correction:** a subsequent classic-CDS control produced a negative
external `time` elapsed duration (`target/cache-control-cds-1.time`: -1.07 s).
Consequently the external-wall-clock cold measurements above are not accepted
as performance evidence, including the provisional 36% figure. The exact clock
failure is not established. The standard benchmark engine already uses
`System.nanoTime`; the cold-control driver has now been changed to that
monotonic clock too, with exact stdout/exit checks and positive-duration checks.

Classic CDS training used the same compiler jar, probe jar, classpath and
500/100 workload as AOT training, producing a 17,604,608-byte archive at
`target/compiler-classic.jsa`. A four-way rotating-order cold comparison now
covers plain G1, classic CDS/G1, AOT/G1, and classic CDS/Parallel GC. The final
configuration approximates the existing launcher's VM policy, but this remains
a jar-level diagnostic rather than installed-launcher acceptance.

All sixteen monotonic-clock subprocesses exited zero with exact Hello output.
Median seconds: plain 1.411436, CDS/G1 0.918122, AOT/G1 0.858034,
CDS/Parallel 0.979675. AOT improves over classic CDS/G1 by about 6.5% here,
not 50%. No production AOT integration is justified by this small sample,
especially with the earlier warmed-Hello regression. Raw nanoseconds:
`target/cold-cache-monotonic.tsv`. The old external-time estimates remain
superseded, not mixed into these results.

The next VM-only probe addresses object density directly: JDK 25 accepts
`-XX:+UseCompactObjectHeaders` as a product option (verified locally).
The [JDK GC guide](https://docs.oracle.com/en/java/javase/25/gctuning/other-considerations.html)
describes its smaller heap object headers and four-million-loaded-class limit.
Only the exploratory subprocess flag changes; no launcher, JVM minimum, or
compiler source default is changed. Compare the same plain reference jar,
25 warmups / 25 measurements, in ABBA order before considering adoption.

Compact-header probe completed (milliseconds, baseline A / compact A / compact B /
baseline B): Hello 5.103 / 5.096 / 4.694 / 4.725; TodoManager 25.925 / 23.641 /
25.028 / 18.329; StatsApp 29.463 / 23.407 / 20.563 / 23.722; 20 files 42.728 /
21.893 / 17.886 / 24.521. The baseline itself drifts substantially, so these
results do not establish a general improvement, let alone halving. No default
was changed. Raw evidence: `target/compact-compare-*.log`.

**Flat-static candidate withdrawn:** follow-up review found that the old
`TreeSet.asScala.flatMap(...).toList` uses the Scala mutable HashSet factory,
whereas the replacement ArraySeq preserves sequence order and duplicates.
`collectStaticApplicables` and `collectPartialInstanceApplicables` therefore
change behavior despite their common Iterable parameter type. The full green
suites did not detect this collection-factory boundary. The initial ready
verdict is superseded. Given inconclusive performance evidence, the production
candidate and its helper-only tests were removed rather than adding machinery
to preserve that boundary. The experimental jar/logs remain for reproducibility;
they are not a shipping candidate.

## Shared ASM descriptor experiment

`AsmCodeGeneration` parsed twelve fixed method signatures and two fixed object
types on every construction, including compilations without records. Move only
those immutable values to its existing companion, leaving per-class state and
ThreadLocal ownership unchanged. An isolated JDK thread-allocation probe with
10,000 warmups and 10,000 escaped constructions measured 8,296 bytes per
reference instance (failed the prewritten 1 KiB gate) and 56 bytes per candidate
instance (passed). This is an allocation result, not a whole-compile speedup.
The throwaway probe is `target/GeneratorAllocationProbe.java`.

The targeted record/ASM run passed 107 tests in 15 suites. Read-only review
found no correctness findings: the actual ASM dependency's Type/Method values
are immutable, consumers only read them, and companion initialization has no
compiler callbacks. Full-suite and timing acceptance remain separate gates.

The isolated jar `target/compile-shared-asm-only.jar` (SHA-256
`043c16ccb9c65206adfe3c52961933b3ef4a90d4066dc71bc62a7db71b406d53`)
contains only this change over the unchanged reference jar, not the previously
committed erased-pattern optimization. Equal 500-warmup/200-measurement paired
compiles compare both classloader construction orders. These are exploratory
and cannot be compared to the original 25/25 acceptance numbers.

Both paired runs completed with matching workload hashes and checked class
counts. Candidate/reference median ratios in forward and reverse construction
order respectively: Hello 0.987 / 1.003; TodoManager 1.060 / 0.962; StatsApp
1.034 / 1.025; 20 files 0.883 / 1.013. Reverse ratios are inverted from the raw
driver's second/first output. The order sensitivity does not establish an
end-to-end speed improvement. Keep this as an uncommitted allocation candidate,
not progress counted toward the half-time acceptance threshold. Raw samples:
`target/shared-asm-paired-forward.log`, `target/shared-asm-paired-reverse.log`.

The shared-ASM snapshot's English full suite completed with 5,238 passing tests,
717 completed suites, and the one opt-in distribution test canceled. Log:
`target/shared-asm-full-en.log`. This run predates the arity-pruning change below.

## Arity before specialization experiment

`MethodResolutionSupport.applicable` previously specialized every candidate's
parameter types before rejecting impossible argument counts. Substitution does
not change array length, so move the existing fixed/vararg count checks before
specialization; preserve the zero-parameter vararg fallback and default-argument
minimum. Three prewritten regression cases failed because specialization was
called for rejected candidates, then passed after the change. The initial
targeted run passed 34 tests in seven suites, including default arguments and
varargs. Read-only review found no correctness blockers. Additional direct
zero-parameter and array-vararg cases are pending the next test run.

Isolated candidate `target/compile-arity-only.jar`, SHA-256
`b6391c4f080b0c05c085666d8ef34c51e11b3272438a372537264d77c4db5d6f`,
changes only MethodResolutionSupport relative to the reference. A paired
200-warmup/100-measurement probe runs both construction orders; no speed claim
is made from reduced specialization work alone.

The v1 paired candidate/reference ratios were 1.140 / 0.939 (Hello), 1.055 /
1.110 (TodoManager), 1.061 / 0.926 (StatsApp), and 1.180 / 0.871 (20 files),
forward/reverse respectively. There is no reproducible improvement. Inspection
then found an added cost: both external Method implementations return a clone
from `arguments`, so the new arity check copied an array even on a specialization
cache hit.

The revised candidate introduces `Method.argumentCount`, defaulting to the
existing array length but overriding it with the stored length for ASM and
reflection methods. Actual argument arrays remain defensively copied. A
counting ASM method test failed with one array request against the default
accessor and passed with zero after the override; both external implementations
also retain their defensive-array tests. Two other existing count-only consumers
use the new accessor. The expanded targeted run passed 37 tests in eight suites,
including the extra zero-parameter and array-vararg cases.

A cumulative candidate jar now includes the committed erased-pattern change,
shared ASM constants, and revised arity/count changes; it is not an isolated
arity experiment: `target/compile-arity-count-cumulative.jar`, SHA-256
`7245cc0550b748462748e6dabea52ab13dd44cd615f3a934cad46b48ed1446ec`.
An equal 25/25 ABBA reference/candidate comparison is being collected in
`target/arity-count-{base-a,candidate-a,candidate-b,base-b}.log`. Full-suite
validation of this revised snapshot remains pending.

The cumulative ABBA run completed successfully. Median milliseconds (reference
A / candidate A / candidate B / reference B):

| Scenario | Reference A | Candidate A | Candidate B | Reference B |
|---|---:|---:|---:|---:|
| Hello | 3.676 | 3.846 | 6.190 | 3.730 |
| TodoManager | 15.578 | 17.469 | 18.821 | 25.613 |
| StatsApp | 17.873 | 16.260 | 16.659 | 23.078 |
| 20 files | 16.459 | 22.321 | 24.938 | 26.050 |

This is not a consistent end-to-end improvement and does not meet any general
halving claim. The two locale full suites have been started for this snapshot
(`target/arity-count-full-en.log`, `target/arity-count-full-ja.log`); their
completion must be checked before treating the cumulative code as fully tested.

Both locale runs subsequently completed successfully: English and Japanese each
passed 5,246 tests in 719 suites, with zero failures and one opt-in distribution
test canceled. This validates the tested behavior of the cumulative snapshot,
not its performance: the half-time goal remains unachieved.

## Allocation-free external method comparison

MethodComparator still requested two defensive parameter-array copies per
comparison. It now reads argumentCount and argumentTypeAt(index), with direct
stored-slot implementations for ASM/reflection methods and the closure adapter.
The public defensive argument arrays are unchanged. The comparator's existing
equal-name/nonidentical-type early return is explicitly preserved, not fixed as
part of this optimization. A test first failed on six defensive-array requests
across three comparisons, then passed with zero. The targeted overload/arity
run passed 39 tests; the closure/accessor run passed 45. Read-only review found
no correctness blockers; full-suite validation of this snapshot is pending.

The real external String.substring overloads were compared one million times
after 100,000 warmups, with alternating argument order and an escaped result.
The JDK allocation counter measured 48.00008 bytes/comparison for both reference
runs and 0.00008 for both candidate runs. Nanoseconds/comparison in ABBA order:
33.418826 / 6.221010 / 4.515291 / 36.925287. This probe exercises arity rejection
in the comparator, not whole compilation or the equal-arity slot loop. Setup is
outside timing. Driver: `target/ComparatorAllocationProbe.java`; raw logs:
`target/comparator-allocation-*.log`.

The cumulative candidate jar is `target/compile-slot-cumulative.jar`, SHA-256
`c61ab16762fd3b60a1eb014c3ecd18ea24fb0c65a6bc8eafcbb75a57acb24ae9`.
Equal 25/25 ABBA whole-compile measurements are being collected separately in
`target/slot-{base-a,candidate-a,candidate-b,base-b}.log`; a microbenchmark win
alone does not establish progress against the half-time acceptance target.

The equal-arity probe selects the two one-argument String.indexOf overloads,
so it exercises the slot loop rather than an arity mismatch. ABBA results in
ns/comparison: 41.705269 / 7.909372 / 10.249812 / 39.940673. Reference allocation
was 48.00008 bytes/comparison in both runs, candidate 0.00008 in both. Logs:
`target/comparator-equal-*.log`. The tiny nonzero allocation is 80 bytes across
the entire million-comparison measurement, not a per-call object.

The 25/25 whole-compile ABBA medians were, in milliseconds:

| Scenario | Reference A | Candidate A | Candidate B | Reference B |
|---|---:|---:|---:|---:|
| Hello | 3.561 | 3.472 | 3.151 | 3.865 |
| TodoManager | 19.170 | 17.051 | 16.518 | 16.764 |
| StatsApp | 16.871 | 17.348 | 19.025 | 17.859 |
| 20 files | 13.964 | 19.438 | 20.659 | 20.787 |

These results do not establish a consistent whole-compile improvement or
halving. English/Japanese full suites for this snapshot are running separately
after all timing probes (`target/slot-full-en.log`, `target/slot-full-ja.log`).

The slot snapshot's English and Japanese full suites both completed: 5,248
passing tests each, 719 suites, zero failures, one opt-in distribution test
canceled. A fresh multi-file JFR profile (200 warmups, 1,500 compilations) produced
30,000 classes. Mean total was 11.287 ms, of which Typing 7.293 ms, Parsing
1.886 ms, BytecodeGeneration 1.500 ms. This is a profiling protocol, not an
acceptance speedup. Execution samples remain dispersed across captures, name
resolution, collections, signatures, parsing and generation; no single leaf
accounts for more than 3% of the 10,531 samples. Raw evidence:
`target/slot-profile.jfr`, `target/slot-profile.log`, `target/slot-hotspots.txt`.

To separate JVM warmup from compiler work, an unchanged-reference 25/25 probe
compares default tiering, `-XX:TieredStopAtLevel=1`, and
`-XX:CompileThresholdScaling=0.1` in base/C1/quick/quick/C1/base order.
No product flag is changed. Raw evidence is collected in `target/jit-*.log`.

All six JVM-policy runs completed successfully. Median milliseconds:

| Scenario | Base A | C1 A | Quick A | Quick B | C1 B | Base B |
|---|---:|---:|---:|---:|---:|---:|
| Hello | 4.073 | 4.261 | 4.819 | 3.333 | 3.447 | 6.470 |
| TodoManager | 21.735 | 15.656 | 13.837 | 13.928 | 10.970 | 18.148 |
| StatsApp | 17.374 | 14.239 | 16.537 | 13.809 | 9.767 | 18.555 |
| 20 files | 19.775 | 25.384 | 23.355 | 22.583 | 28.789 | 20.471 |

Both altered policies regress the multi-file workload in both orderings, despite
some smaller-workload improvements. Neither is a general-halving solution, and
neither should become a launcher default on this evidence. The work should now
focus on substantial typing work reduction rather than assuming a VM-policy
switch or another isolated allocation win will meet the objective.

## Demand-driven override implementation signatures

Expanded attribution of the same JFR recording assigns 4.93% of samples to
DuplicationChecks and 4.53% to TypingDuplicationPass as the first compiler class
on the stack (not an additional wall-time measurement). The former eagerly
erased every implementation signature, including names no ancestor declares.
`checkOverrideContracts` now groups implementations by name and computes a
descriptor index only when an eligible inherited contract requests that name.
The existing contract traversal order, static/private guards, specialized/raw
lookup preference, and last-implementation-wins behavior are preserved.

Two tests first failed on unnecessary erasure of an unrelated name, then passed.
The relevant override/forward/erasure run passed 28 tests; an additional direct
last-wins regression passed in the three-test pruning suite. Read-only review
found no correctness blockers. Whole-suite and performance acceptance remain
separate requirements.

Cumulative artifact: `target/compile-override-pruned-cumulative.jar`, SHA-256
`bdb948f5838c9d594936308fc57d7d3e773fb5a260e1f8c6aae17991e3206135`.
Equal 25/25 ABBA samples are collected under
`target/override-pruned-{base-a,candidate-a,candidate-b,base-b}.log`.

The completed ABBA medians (reference A / candidate A / candidate B / reference B)
were Hello 3.260 / 4.042 / 3.738 / 3.572 ms; TodoManager 18.544 / 21.799 /
15.027 / 15.520 ms; StatsApp 17.239 / 20.384 / 18.477 / 17.069 ms; 20 files
21.910 / 18.813 / 18.020 / 16.734 ms. These do not demonstrate a consistent
whole-compile gain. Before adding more candidates, rebuild the original source
commit in a separate temporary directory and audit the reference artifact
against that build; preserve the original jar and raw results unchanged.

The reference audit completed successfully: rebuilding exact source commit
`c9e77b96af44ee32a429166171e06991d6022b5b` from `git archive` in
`/tmp/onion-baseline-audit-KHv29W` and packaging its classes/resources produced
`target/compile-base-audited.jar`. Extracting both jars and running `diff -qr`
reported no differences (exit 0; `target/baseline-artifact-audit.diff` is empty).
This checks every archived file's contents, not just the compiler entry point.
The build used sbt's disk cache; this is source/build-output consistency evidence,
not an independent cache-free compilation. There is no evidence that a stale or
incorrect reference jar explains the benchmark variation. Original artifacts
and measurements remain unchanged.

The cumulative override-pruning source passed fresh English and Japanese
`testFull` runs: 5,251 tests succeeded in each locale, 720 suites completed,
zero failures/aborts, and one canceled test per run. Logs are
`target/override-pruned-full-{en,ja}.log`. This validates correctness coverage,
not the performance goal. A four-process identical-reference (A/A) control with
the unchanged 25-warmup/25-iteration protocol follows, with no simultaneous
builds or test JVMs, to quantify run-to-run noise before further attribution.

All four A/A processes exited successfully. Medians in process order (ms):

| Workload | A | B | C | D |
|---|---:|---:|---:|---:|
| Hello | 4.239 | 4.582 | 6.799 | 5.959 |
| TodoManager | 21.222 | 20.107 | 28.614 | 32.234 |
| StatsApp | 21.013 | 19.075 | 26.186 | 21.162 |
| 20 files | 20.806 | 23.212 | 27.844 | 23.791 |

Raw samples are `target/aa-reference-{a,b,c,d}.log`. Max/min run medians vary by
1.60x for Hello and TodoManager, 1.37x for StatsApp, and 1.34x for 20 files,
despite identical code. These observations rule out treating small unreplicated
AB differences as reliable evidence. They do not prove a specific noise source,
nor alter the half-time acceptance target.

A separate diagnostic A/A control used `PairedCompileProbe`, two isolated class
loaders of the identical reference in one JVM, 500 warmups and 200 measurements
on each side, alternating AB/BA within each iteration. Second/first median ratios
were Hello 0.960, TodoManager 0.960, StatsApp 0.985, and 20 files 0.929. All
workload hashes and expected output class counts matched. Log:
`target/aa-paired-reference.log`. This reduces but does not eliminate position/JIT
bias (up to 7.1% in this control). Its lower absolute warmed times are not a
speedup relative to the original 25/25 protocol. Evaluate cumulative candidates
in both class-loader orders under this same diagnostic protocol, retaining the
original acceptance protocol separately.

The cumulative candidate's 500/200 paired comparison also completed in both
class-loader orders (`target/pruned-paired-{forward,reverse}.log`). Expressed
consistently as candidate/reference, the forward/reverse ratios are Hello
0.975 / 1.035, TodoManager 1.060 / 0.979, StatsApp 1.014 / 0.998, and 20 files
0.973 / 1.030. Reverse values invert the probe's second/first output. None
demonstrates a consistent improvement beyond the A/A variation. Thus even the
verified allocation reductions do not yet justify a whole-compile speed claim;
the half-time objective remains unmet.

Allocation triage of the existing `slot-profile.jfr` is exploratory only.
`target/JfrAllocations.java` grouped 5,052 `jdk.ObjectAllocationSample` events
by object class and first compiler frame, with 14,005,866,832 total weighted
bytes (`target/slot-allocations.txt`). A single int-array sample attributed to
`InnerClassLambdaMetafactory` has weight 1,245,311,072 bytes, and several other
individual events exceed 100 MiB. These are sampling weights, not individual
object sizes or exact per-site totals. Consequently this distribution cannot
yet justify a large representation change; inspect recording-boundary and
sampling effects or use more direct allocation measurements first.

Direct JDK `ThreadMXBean` allocation counters, using the unchanged scenarios
with 500 warmups and 200 measurements in separate reference/candidate JVMs,
gave the following total allocated bytes per compilation (all Java threads):

| Workload | Reference | Cumulative candidate | Reduction |
|---|---:|---:|---:|
| Hello | 84,170 | 75,942 | 9.8% |
| TodoManager | 810,393 | 795,489 | 1.8% |
| StatsApp | 885,486 | 876,219 | 1.0% |
| 20 files | 8,500,279 | 8,276,664 | 2.6% |

Driver: `target/CompileAllocationProbe.java`; raw outputs:
`target/allocation-{base,pruned}.log`. It verifies generated class counts and
prints workload hashes, calling-thread allocation/CPU time, and wall time too.
Total counters include other live Java threads in the process; these are not
per-allocation-site measurements. Counter reads and checks add small probe
overhead to both sides. This single ordered pair is allocation evidence, not
timing acceptance. The modest whole-workload allocation reduction explains why
the large isolated constructor/comparator wins need not translate into a large
whole-compile improvement. Larger repeated representations need investigation.

`target/PhaseAllocationProbe.java` then wrapped the production `CompilerPhase`
implementations without editing them, with fresh phase instances and sources on
every iteration. On the cumulative candidate (500 warmups / 200 measurements),
20-file per-phase allocated bytes were: Typing 3,856,792; BytecodeGeneration
2,625,643; Parsing 1,769,144; Rewriting 86,648; remaining phases each below 1 KiB.
Log: `target/phase-allocation-pruned.log`. Rewriting is only about 1% of these
allocations, so an AST-copy redesign is not the next priority on this evidence.

The probe's optional `subpasses` mode subclasses `Typing` only to wrap calls to
the four original `super.process*` methods. It retains the full original
`process` flow, including capability and shape-law checks, and returns classes,
warnings, and bindings as `TypingPhase` does. `target/typing-allocation-pruned.log`
shows the 20-file breakdown: Body 2,080,368 bytes / 3.630 ms calling-thread CPU;
Outline 840,019 / 1.021 ms; Duplication 751,592 / 1.106 ms; Header 159,000 /
0.175 ms. Whole Typing was 3,871,773 bytes / 6.353 ms in this instrumented run.
For TodoManager and StatsApp, Body allocated about 229,080 and 227,460 bytes,
with 1.891 and 2.044 ms calling-thread CPU respectively. These are diagnostic
measurements, not acceptance timings; wrappers/counter reads affect execution.
They narrow the next investigation toward method-body setup/typing rather than
AST rewriting or another isolated duplication-check change.

A throwaway ASM-instrumented jar (`target/compile-body-instrumented.jar`, never
a shipping candidate) measured selected `MethodBodySupport` methods without
editing source. `target/body-allocation-pruned.log` reports 633 method bodies in
the 20-file workload: `prepareMethodContext` allocated 1,045,109 bytes per compile
out of 1,806,756 in `processMethodLikeBody` (inclusive); parameter binding,
default-argument construction, and capture scanning accounted for 156,984,
167,116, and 184,224 bytes respectively. Counter calls substantially inflate CPU
times at this granularity, so those instrumented times are not used as evidence
of a speedup or exact uninstrumented percentages.

The next bounded representation experiment targets `LocalScope`: retain one
binding directly and spill into the existing HashMap on a second distinct name.
Empty scopes must still avoid a table; duplicate detection, null-map semantics,
parent lookup, and detached name/entry snapshots must remain unchanged. A real
escaped-object allocation probe (`target/ScopeAllocationProbe.java`) failed its
prewritten 96-byte singleton budget on the reference at 168 bytes/scope
(`target/scope-allocation-red.log`). This is a local allocation gate only; it
does not replace full compiler correctness or whole-workload timing acceptance.

The singleton representation candidate passes the same escaped allocation gate
at 32 bytes/scope (`target/scope-allocation-green.log`), versus the reference's
168 bytes. Four `LocalScopeSpec` characterization tests passed before the change
and after the final implementation, covering duplicate preservation, growth and
parent shadowing, detached snapshots, and null key/value semantics. Grown
`toString` deliberately retains the original HashMap-keySet mapping behavior
rather than replacing its collection factory with an iterator.

Cumulative artifact: `target/compile-singleton-scope-cumulative.jar`, SHA-256
`9b1ccc78541e98d6727e1e6f4697fd5a589a608ce67ad638bc2bee434c22c3ff`.
Read-only review found no Critical/Important issues and marked the slice ready
for broader validation, including inspection of `LocalFrame` consumers.
Fresh English/Japanese `testFull` runs both passed 5,255 tests in 721 suites,
with zero failures/aborts and one canceled test each (`target/scope-full-{en,ja}.log`). Whole-compile
performance remains unproven; the singleton allocation reduction alone cannot
count as the requested halving.

Scope-shape allocation controls measured old/new bytes per escaped scope as
0 bindings: 24/32; 1: 168/32; 2: 200/208; 8: 392/400; 32: 1704/1712.
Logs: `target/scope-shape-{old,new}-{0,1,2,8,32}.log`. Thus the tradeoff is
136 bytes saved for a singleton, 8 bytes added for the other measured shapes.

An isolated comparison against the immediately preceding cumulative artifact
used 500 warmups / 200 measurements and both class-loader orders. New/old ratios
(forward / inverted reverse) were Hello 0.895 / 0.935; TodoManager 0.924 /
0.997; StatsApp 1.011 / 1.008; 20 files 1.067 / 1.007. Logs:
`target/scope-paired-{forward,reverse}.log`. The Hello result is encouraging but
the remaining workloads do not establish a consistent benefit, and the earlier
A/A position variation still applies. These results do not establish halving.

Original-protocol 25/25 ABBA against the original reference produced medians
(reference A / cumulative A / cumulative B / reference B, ms): Hello 4.350 /
3.943 / 4.043 / 3.501; TodoManager 23.682 / 17.745 / 19.779 / 18.810;
StatsApp 20.642 / 16.819 / 17.858 / 21.761; 20 files 24.415 / 20.075 /
18.238 / 22.809. Logs: `target/scope-acceptance-{base-a,candidate-a,candidate-b,base-b}.log`.
These remain far from a verified halving.

Whole-JVM allocation on the singleton cumulative artifact (500/200) measured
75,548 / 801,327 / 887,868 / 8,311,689 bytes per compilation for the four
workloads (`target/allocation-singleton.log`). Unlike the isolated scope probe,
these totals do not consistently improve on the preceding candidate. Artifact
audit (`target/scope-artifact.diff`) confirms that only `LocalScope.class` and
`LocalScope.tasty` differ between those two jars; unrelated rebuilt classes do
not explain this. A same-protocol interpreted allocation control is next, to
separate representation costs from JIT escape-analysis differences. Interpreted
timings are diagnostic only and cannot count as performance acceptance.

The `-Xint` allocation control completed with 5 warmups / 5 measured fresh
compiles on both versions (`target/scope-interpreted-{old,new}.log`). Old/new
whole-JVM bytes per compile were Hello 77,192 / 76,800; TodoManager 936,306 /
932,090; StatsApp 1,034,338 / 1,031,678; 20 files 9,832,277 / 9,706,989.
The representation does reduce unoptimized allocation (about 125,288 bytes for
20 files), but the larger warmed totals seen earlier mean this cannot be assumed
to carry through JIT optimization. These separate-process controls suggest an
optimization interaction; they do not establish which JIT decision causes it.

An independent read-only performance design review did not identify a convincing
large common optimization from method setup alone: even eliminating that setup
would remove only about 13% of 20-file allocation, 1.7% of TodoManager, and 1.1%
of StatsApp. The suggested next measurement is exclusive allocation within
expression/name/member resolution and overload inference across all four
workloads. This avoids letting the many-small-methods fixture determine a
compiler-wide representation redesign. The singleton candidate remains
uncommitted and its whole-compile benefit remains unproven.

## Final diagnostic checkpoint

An exclusive allocation probe instrumented ten name/member/call-resolution
classes, subtracting nested instrumented spans to avoid double counting. Across
500 warmups and 200 fresh compilations per workload, the largest measured self
allocations were 528 bytes/Hello compile in call construction, 26,377 bytes/Todo
compile in instance-call typing, 19,890 bytes/Stats compile in unqualified-call
typing, and 67,456 bytes/20-file compile in identifier typing. This did not reveal
a large common allocation hotspot. Instrumentation changes runtime behavior;
these figures are attribution diagnostics, not timing acceptance evidence.
Local artifacts: `target/ExclusiveMeter.java`, `target/InstrumentResolution.java`,
and `target/resolution-allocation.log`.

Native execution was considered only as a feasibility research direction. No
native toolchain was installed and no native implementation or distribution
change was made. The investigation is now paused rather than expanding scope to
reach the original numerical target. The changes above are submitted for review
as bounded improvements with the documented performance limitations.
