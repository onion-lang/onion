# Compilation-time halving work log

Objective: reduce Onion compilation time to half of the starting implementation,
without changing accepted programs, diagnostics, generated code, or correctness
checks. This objective is **not yet achieved**.

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
