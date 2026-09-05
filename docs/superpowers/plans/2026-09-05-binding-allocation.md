# Binding allocation experiment

> **For agentic workers:** Use superpowers:executing-plans for this isolated experiment.

**Goal:** Reduce wasted small-compilation allocation without sacrificing large-input performance.

**Architecture:** Keep the existing identity-keyed growable map and read-only view; only reduce its initial expected size from 1,024 to 128. No shared mutable state or compilation-result cache.

**Tech Stack:** Scala 3, JDK 25 allocation counters, ScalaTest, existing paired benchmark driver.

**Spec:** `docs/compilation-speed.md`; the full compilation-time-half goal remains unmet.

## Constraints

- Preserve identity, reverse lookup, view copy semantics, and unbounded growth.
- Compare an isolated jar against the immutable baseline, not combined speculative changes.
- No timed benchmark concurrent with compilation or tests.

## Experiment

- [x] Add `AstBindingIndexSpec`: 4,096 equal-shaped distinct AST keys and read-only view copies.
- [x] Measure an empty index with `target/BindingAllocationProbe.java`; baseline exceeds the 8 KiB exploratory allocation budget at 16,480 bytes.
- [x] Run characterization tests before changing production.
- [x] Change only the constructor capacity in `typing/session/AstBindingIndex.scala` to 128.
- [x] Run `testOnly *AstBindingIndexSpec`; run the allocation probe on the candidate and require less than 8 KiB.
- [x] Package only `AstBindingIndex.class` and its Scala metadata into a baseline copy.
- [x] Run `PairedCompileProbe` with 500 warmups / 200 samples, both loader orders, on Hello, TodoManager, StatsApp and 20 files.
- [x] Record results and either retain for full correctness validation/review or remove the production experiment if it has no defensible benefit.

Outcome: removed. Empty allocation fell to 2,144 bytes, but the 20-file
workload regressed in both loader orders (1.093 and 1.041 ratios).
