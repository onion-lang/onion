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
