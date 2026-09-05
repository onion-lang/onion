# Linear parameter-pattern matching

> **For agentic workers:** Use superpowers:executing-plans for inline execution.

**Goal:** Remove exponential boxed/primitive signature enumeration without changing contract matching.

**Architecture:** Store allowed erased descriptors separately for each argument. Abstract implementation checks compare a raw implementation against a contract pattern; override-target checks compare two patterns for positional intersection. Index candidates by method name and arity, preserving all base names for suggestions.

**Tech Stack:** Scala 3, ScalaTest, existing JVM benchmark runner.

**Spec:** `docs/compilation-speed.md`, especially the warmed ABBA probe and next investigation.

## Global constraints

Preserve static/private/abstract filtering, generic substitution, specialized/raw override alternatives, and diagnostics. No compile-result cache and no disabled checks. A high-arity improvement does not establish the overall half-time goal.

## Task 1: Pattern matching and contract integration

Files: create `src/main/scala/onion/compiler/typing/ErasedParameterPattern.scala`, create `src/test/scala/onion/compiler/ErasedParameterPatternSpec.scala`, modify `src/main/scala/onion/compiler/typing/DuplicationChecks.scala`.

- [x] Add tests for empty patterns, arity mismatch, primitive/boxed overlap, disjoint positions, raw one-sided acceptance, and 64 independent two-way positions. `accepts(Array("I", "Ljava/lang/Integer;"))` must accept a two-position primitive/boxed pattern, but reject a Long at either position.
- [x] Run the new suite and observe failure before implementation.
- [x] Implement `ErasedParameterPattern(alternatives: Array[Set[String]])` with `accepts(actual: Array[String]): Boolean` and `overlaps(other: ErasedParameterPattern): Boolean`; compare arity first and then use a while loop with early mismatch return.
- [x] Replace `allErasedParamDescriptors` with per-position pattern creation using its existing primitive/boxed alternatives. Override lookup uses `(name, arity)` to find raw and specialized patterns. Abstract lookup retains raw descriptor arrays, not boxing-expanded implementations.
- [x] Run pattern tests and existing override, generic, abstract, and forwarding suites. Add wide-interface compile/run coverage; use an unconditional match mutation to establish mismatch coverage.
- [ ] Independently review the resulting changes, run both locale full suites, compare the immutable reference jar with the candidate on the wide probe and unchanged representative workloads. Commit only changes justified by correctness and performance evidence.
