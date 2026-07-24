# Absolute Performance Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the five practical latency ceilings into typed, machine-enforced benchmark policy while keeping non-reference runs useful and informational.

**Architecture:** Extend benchmark environment evidence with the Linux distribution and assigned memory needed for reference-lane detection. A pure `AbsolutePerformancePolicy` evaluates scenario summaries without running benchmarks; schema v3 embeds that evaluation in JSON and text output. The existing runner exits nonzero only for invalid/scenario failures or a policy failure on the detected reference lane.

**Tech Stack:** Scala 3.3.7, ScalaTest 3.2.19, Java management APIs, the existing `onion.Json` renderer, sbt input tasks.

## Global Constraints

- The reference lane is Ubuntu 24.04 x86-64, Temurin JDK 21, two assigned processors, 4 GiB assigned memory, and a 2 GiB maximum heap with G1 for in-process scenarios.
- Absolute limits are inclusive: a result exactly equal to its median or p95 ceiling passes.
- The five governed scenario IDs and ceilings come verbatim from the practical-readiness design.
- A successful non-reference run is `informational`; its absolute checks are `not-applicable`, never `pass`.
- Missing or failed scenario evidence on a reference lane is `unknown` and fails the policy.
- All report durations remain integer nanoseconds.
- The report schema increments from 2 to 3 because environment and policy fields change its public shape.
- `bench` remains a compatibility alias for `benchmark`.
- Relative base/head regression comparison is a separate plan because it requires report decoding, two clean revisions, alternating execution, and confirmation rounds.

---

## File Structure

- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkMetadata.scala`
  captures the additional reference-lane evidence.
- `src/main/scala/onion/tools/readiness/policy/PerformancePolicy.scala`
  owns reference requirements, budgets, check statuses, and pure evaluation.
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkReport.scala`
  embeds the policy evaluation in schema v3.
- `src/main/scala/onion/tools/readiness/benchmark/BenchmarkRender.scala`
  serializes and summarizes the policy evidence.
- `src/main/scala/onion/tools/BenchmarkRunner.scala`
  supplies captured evidence and inherits policy-aware exit semantics.
- `src/test/scala/onion/tools/readiness/benchmark/BenchmarkMetadataSpec.scala`
  verifies stable `/etc/os-release` parsing.
- `src/test/scala/onion/tools/readiness/policy/PerformancePolicySpec.scala`
  proves reference detection and all policy boundaries.
- `src/test/scala/onion/tools/readiness/benchmark/BenchmarkRenderSpec.scala`
  locks schema v3 JSON and text output.
- `src/test/scala/onion/tools/BenchmarkRunnerSpec.scala`
  proves local informational and reference failure behavior.
- `docs/contributing/building.md` and
  `docs/ja/contributing/building.md`
  document policy applicability and schema v3.

---

### Task 1: Capture Reference-Lane Environment Evidence

**Files:**
- Modify: `src/main/scala/onion/tools/readiness/benchmark/BenchmarkMetadata.scala`
- Create: `src/test/scala/onion/tools/readiness/benchmark/BenchmarkMetadataSpec.scala`

**Interfaces:**
- Produces: `EnvironmentMetadata.osReleaseId: String`
- Produces: `EnvironmentMetadata.osReleaseVersion: String`
- Produces: `EnvironmentMetadata.totalMemoryBytes: Long`
- Produces: `EnvironmentMetadata.parseOsRelease(lines: Seq[String]): (String, String)` with `private[benchmark]` visibility.

- [ ] **Step 1: Write the failing parser and capture tests**

```scala
package onion.tools.readiness.benchmark

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class BenchmarkMetadataSpec extends AnyFunSpec with Matchers:
  describe("EnvironmentMetadata.parseOsRelease"):
    it("extracts and unquotes Ubuntu identity"):
      EnvironmentMetadata.parseOsRelease(
        Seq("""ID="ubuntu"""", """VERSION_ID="24.04"""")
      ) shouldBe ("ubuntu", "24.04")

    it("uses unknown values when release keys are absent"):
      EnvironmentMetadata.parseOsRelease(Seq("NAME=Linux")) shouldBe
        ("unknown", "unknown")

  describe("EnvironmentMetadata.capture"):
    it("captures positive processor, heap, and memory evidence"):
      val environment = EnvironmentMetadata.capture()
      environment.processors should be > 0
      environment.maxHeapBytes should be > 0L
      environment.totalMemoryBytes should be > 0L
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkMetadataSpec'
```

Expected: test compilation fails because the three fields and `parseOsRelease` do not exist.

- [ ] **Step 3: Add fields and deterministic capture helpers**

Add these fields after `osArch`:

```scala
osReleaseId: String,
osReleaseVersion: String,
processors: Int,
totalMemoryBytes: Long,
```

Add helpers to `EnvironmentMetadata`:

```scala
private[benchmark] def parseOsRelease(
  lines: Seq[String]
): (String, String) =
  val values = lines.flatMap { line =>
    line.split("=", 2) match
      case Array(key, value) =>
        Some(key -> value.stripPrefix("\"").stripSuffix("\""))
      case _ => None
  }.toMap
  (
    values.getOrElse("ID", "unknown"),
    values.getOrElse("VERSION_ID", "unknown")
  )

private def osRelease(): (String, String) =
  val path = java.nio.file.Paths.get("/etc/os-release")
  if java.nio.file.Files.isRegularFile(path) then
    parseOsRelease(java.nio.file.Files.readAllLines(path).asScala.toSeq)
  else ("unknown", "unknown")

private def totalMemoryBytes(): Long =
  ManagementFactory.getOperatingSystemMXBean match
    case bean: com.sun.management.OperatingSystemMXBean =>
      bean.getTotalMemorySize
    case _ => Runtime.getRuntime.maxMemory()
```

Call both helpers once in `capture()` and populate the new fields.

- [ ] **Step 4: Run the metadata tests to verify GREEN**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkMetadataSpec'
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/onion/tools/readiness/benchmark/BenchmarkMetadata.scala \
  src/test/scala/onion/tools/readiness/benchmark/BenchmarkMetadataSpec.scala
git commit -m "Capture benchmark reference environment"
```

---

### Task 2: Evaluate Absolute Performance Budgets

**Files:**
- Create: `src/main/scala/onion/tools/readiness/policy/PerformancePolicy.scala`
- Create: `src/test/scala/onion/tools/readiness/policy/PerformancePolicySpec.scala`

**Interfaces:**
- Consumes: `EnvironmentMetadata` and `Vector[ScenarioResult]`.
- Produces: `PolicyCheckStatus`, `PolicyOverallStatus`, `PerformanceBudget`, `AbsolutePerformanceCheck`, `AbsolutePerformanceEvaluation`.
- Produces: `AbsolutePerformancePolicy.evaluate(environment, scenarios)`.

- [ ] **Step 1: Write failing reference, boundary, non-reference, and missing-evidence tests**

Define test helpers for a qualifying environment and successful scenario, then assert:

```scala
AbsolutePerformancePolicy.evaluate(reference, passingScenarios)
  .overallStatus shouldBe PolicyOverallStatus.Pass

AbsolutePerformancePolicy.evaluate(reference, atExactCeilings)
  .checks.forall(_.status == PolicyCheckStatus.Pass) shouldBe true

AbsolutePerformancePolicy.evaluate(reference, helloMedianOneNanosecondOver)
  .checks.find(_.scenarioId == "steady-fresh:onionc:hello").value.status shouldBe
    PolicyCheckStatus.Fail

AbsolutePerformancePolicy.evaluate(
  reference.copy(processors = 16),
  passingScenarios
).overallStatus shouldBe PolicyOverallStatus.Informational

AbsolutePerformancePolicy.evaluate(reference, Vector.empty)
  .checks.forall(_.status == PolicyCheckStatus.Unknown) shouldBe true
```

Also vary each reference field independently and assert `isReferenceLane` is false.

- [ ] **Step 2: Run the test to verify RED**

Run:

```bash
sbt 'testOnly onion.tools.readiness.policy.PerformancePolicySpec'
```

Expected: test compilation fails because the policy package does not exist.

- [ ] **Step 3: Implement typed statuses, exact budgets, and pure evaluation**

Create:

```scala
package onion.tools.readiness.policy

import onion.tools.readiness.benchmark.*

enum PolicyCheckStatus(val wireName: String):
  case Pass extends PolicyCheckStatus("pass")
  case Fail extends PolicyCheckStatus("fail")
  case NotApplicable extends PolicyCheckStatus("not-applicable")
  case Unknown extends PolicyCheckStatus("unknown")

enum PolicyOverallStatus(val wireName: String):
  case Pass extends PolicyOverallStatus("pass")
  case Fail extends PolicyOverallStatus("fail")
  case Informational extends PolicyOverallStatus("informational")

final case class PerformanceBudget(
  scenarioId: String,
  medianCeilingNanos: Long,
  p95CeilingNanos: Long
)

final case class AbsolutePerformanceCheck(
  scenarioId: String,
  status: PolicyCheckStatus,
  medianNanos: Option[Long],
  p95Nanos: Option[Long],
  medianCeilingNanos: Long,
  p95CeilingNanos: Long,
  message: String
)

final case class AbsolutePerformanceEvaluation(
  referenceLane: Boolean,
  applicabilityReason: String,
  checks: Vector[AbsolutePerformanceCheck],
  overallStatus: PolicyOverallStatus
)
```

Use these exact budgets:

```scala
Vector(
  PerformanceBudget("process-cold:onion:hello", 1500000000L, 2500000000L),
  PerformanceBudget("steady-fresh:onionc:hello", 150000000L, 300000000L),
  PerformanceBudget("steady-fresh:onionc:stats-app", 750000000L, 1200000000L),
  PerformanceBudget(
    "persistent-session:repl:growing-state",
    100000000L,
    250000000L
  ),
  PerformanceBudget(
    "multi-file:onionc:automation-project",
    2000000000L,
    3000000000L
  )
)
```

Reference detection requires:

```scala
environment.osName == "Linux"
environment.osReleaseId == "ubuntu"
environment.osReleaseVersion == "24.04"
Set("amd64", "x86_64").contains(environment.osArch)
environment.javaVendor == "Eclipse Adoptium"
javaMajor(environment.javaVersion).contains(21)
environment.processors == 2
environment.totalMemoryBytes == 4L * 1024L * 1024L * 1024L
environment.maxHeapBytes == 2L * 1024L * 1024L * 1024L
environment.garbageCollectors.exists(_.startsWith("G1"))
```

On the reference lane, a check passes only when both actual values are less
than or equal to their ceilings. Failed/missing summaries produce `unknown`.
Off the reference lane, every check is `not-applicable` and the overall status
is `informational`.

- [ ] **Step 4: Run the policy tests to verify GREEN**

Run:

```bash
sbt 'testOnly onion.tools.readiness.policy.PerformancePolicySpec'
```

Expected: all policy tests pass, including exact boundary values.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/onion/tools/readiness/policy/PerformancePolicy.scala \
  src/test/scala/onion/tools/readiness/policy/PerformancePolicySpec.scala
git commit -m "Add absolute performance policy"
```

---

### Task 3: Embed Policy Evidence in Benchmark Schema v3

**Files:**
- Modify: `src/main/scala/onion/tools/readiness/benchmark/BenchmarkReport.scala`
- Modify: `src/main/scala/onion/tools/readiness/benchmark/BenchmarkRender.scala`
- Modify: `src/test/scala/onion/tools/readiness/benchmark/BenchmarkRenderSpec.scala`

**Interfaces:**
- Consumes: `AbsolutePerformancePolicy.evaluate`.
- Produces: `PerformanceBenchmarkReport.policy: AbsolutePerformanceEvaluation`.
- Produces: schema v3 JSON fields `environment.osReleaseId`,
  `environment.osReleaseVersion`, `environment.totalMemoryBytes`, and `policy`.

- [ ] **Step 1: Update renderer tests first**

Change the fixture to include the three environment fields and an explicit
policy evaluation. Assert:

```scala
PerformanceBenchmarkReport.CurrentSchemaVersion shouldBe 3
json should include ("\"schemaVersion\": 3")
json should include ("\"osReleaseId\": \"ubuntu\"")
json should include ("\"totalMemoryBytes\": 4294967296")
json should include ("\"overallStatus\": \"pass\"")
json should include ("\"medianCeilingNanos\": 150000000")
text should include ("absolute-policy=pass")
```

- [ ] **Step 2: Run renderer tests to verify RED**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkRenderSpec'
```

Expected: compilation fails because the report has no policy field.

- [ ] **Step 3: Add report policy and render all evidence**

Add:

```scala
policy: AbsolutePerformanceEvaluation
```

to `PerformanceBenchmarkReport`, set `CurrentSchemaVersion = 3`, and have
`create` evaluate:

```scala
policy = AbsolutePerformancePolicy.evaluate(environment, scenarios)
```

Make `succeeded` require:

```scala
failures.isEmpty &&
  scenarios.forall(_.succeeded) &&
  policy.overallStatus != PolicyOverallStatus.Fail
```

Render policy JSON as:

```json
{
  "referenceLane": true,
  "applicabilityReason": "reference environment matched",
  "overallStatus": "pass",
  "checks": [
    {
      "scenarioId": "steady-fresh:onionc:hello",
      "status": "pass",
      "medianNanos": 1000000,
      "p95Nanos": 1000000,
      "medianCeilingNanos": 150000000,
      "p95CeilingNanos": 300000000,
      "message": "within absolute ceiling"
    }
  ]
}
```

The text header adds:

```text
  absolute-policy=pass reference-lane=true
```

and prints failed or unknown checks beneath it.

- [ ] **Step 4: Run renderer and benchmark tests to verify GREEN**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.BenchmarkRenderSpec onion.tools.readiness.policy.PerformancePolicySpec'
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/onion/tools/readiness/benchmark/BenchmarkReport.scala \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkRender.scala \
  src/test/scala/onion/tools/readiness/benchmark/BenchmarkRenderSpec.scala
git commit -m "Add policy evidence to benchmark reports"
```

---

### Task 4: Enforce Reference Failures and Preserve Informational Runs

**Files:**
- Modify: `src/test/scala/onion/tools/BenchmarkRunnerSpec.scala`
- Modify: `src/main/scala/onion/tools/BenchmarkRunner.scala`
- Modify: `src/main/scala/onion/tools/readiness/benchmark/BenchmarkOptions.scala`
- Modify: `src/test/scala/onion/tools/readiness/benchmark/BenchmarkOptionsSpec.scala`

**Interfaces:**
- Produces: default report path `target/readiness/benchmark-v3.json`.
- Preserves: `BenchmarkRunner.main` exit 0 for successful non-reference evidence.
- Enforces: `PerformanceBenchmarkReport.succeeded == false` for reference-lane
  budget failures.

- [ ] **Step 1: Write failing report-outcome and default-path tests**

Update the default options expectation:

```scala
output = Paths.get("target/readiness/benchmark-v3.json")
```

Add runner-level assertions using constructed reports:

```scala
informationalReport.policy.overallStatus shouldBe
  PolicyOverallStatus.Informational
informationalReport.succeeded shouldBe true

referenceBudgetFailure.policy.overallStatus shouldBe PolicyOverallStatus.Fail
referenceBudgetFailure.succeeded shouldBe false
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
sbt 'testOnly onion.tools.BenchmarkRunnerSpec onion.tools.readiness.benchmark.BenchmarkOptionsSpec'
```

Expected: the default still names v2 and report success ignores policy.

- [ ] **Step 3: Update the default and policy-aware construction**

Set:

```scala
output: Path = Paths.get("target/readiness/benchmark-v3.json")
```

Capture `EnvironmentMetadata` once in `buildReport`, pass it into
`PerformanceBenchmarkReport.create`, and rely on the report's policy-aware
`succeeded` method for the existing `sys.exit(1)` branch.

- [ ] **Step 4: Run runner tests and a real non-reference smoke**

Run:

```bash
sbt 'testOnly onion.tools.BenchmarkRunnerSpec onion.tools.readiness.benchmark.* onion.tools.readiness.policy.*'
sbt 'benchmark --warmups 0 --iterations 1 --output target/readiness/policy-smoke-v3.json'
```

Expected: tests pass; the smoke exits zero, names all six scenarios, and prints
`absolute-policy=informational` unless the machine exactly matches the
reference lane.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/onion/tools/BenchmarkRunner.scala \
  src/main/scala/onion/tools/readiness/benchmark/BenchmarkOptions.scala \
  src/test/scala/onion/tools/BenchmarkRunnerSpec.scala \
  src/test/scala/onion/tools/readiness/benchmark/BenchmarkOptionsSpec.scala
git commit -m "Enforce benchmark absolute policy"
```

---

### Task 5: Document and Fully Verify Absolute Policy

**Files:**
- Modify: `docs/contributing/building.md`
- Modify: `docs/ja/contributing/building.md`

**Interfaces:**
- Documents: schema v3, the five ceilings, exact reference-lane requirements,
  and informational non-reference behavior.

- [ ] **Step 1: Update both language documents**

Add the same five-row ceiling table to both documents. State that:

- `benchmark-v3.json` records raw timings and typed policy checks;
- exact reference-lane matches enforce the absolute policy and fail the task on
  breach;
- other machines report `not-applicable` checks and an `informational`
  overall status;
- informational output is useful for profiling but is not release evidence.

- [ ] **Step 2: Check English/Japanese policy values mechanically**

Run:

```bash
rg -n "150 ms|300 ms|750 ms|1.2 s|100 ms|250 ms|2.0 s|3.0 s|1.5 s|2.5 s|benchmark-v3" \
  docs/contributing/building.md docs/ja/contributing/building.md
```

Expected: every value and `benchmark-v3` occur in both files.

- [ ] **Step 3: Run complete verification**

Run:

```bash
sbt 'testOnly onion.tools.readiness.benchmark.* onion.tools.readiness.policy.* onion.tools.BenchmarkRunnerSpec'
sbt 'benchmark --warmups 0 --iterations 1 --output target/readiness/policy-final-v3.json'
sbt test
git diff --check
```

Expected:

- focused policy/benchmark tests pass;
- the JSON parses and contains schema 3, six scenarios, five policy checks,
  and no scenario failures;
- all repository tests pass with zero failed, canceled, ignored, or pending
  tests;
- `git diff --check` emits no output.

- [ ] **Step 4: Commit**

```bash
git add docs/contributing/building.md docs/ja/contributing/building.md
git commit -m "Document absolute benchmark policy"
```

---

## Self-Review

- The plan covers every absolute ceiling, exact boundary behavior,
  reference-lane evidence, schema rendering, exit semantics, bilingual
  documentation, and real protocol verification.
- The plan intentionally excludes relative comparison; that subsystem needs a
  separate executable plan for decoding, compatibility validation, alternating
  base/head rounds, and confirmation semantics.
- The field names and enum values are consistent across policy, report,
  renderer, runner, tests, and documentation.
- No production step changes tracked files during a check; only the explicit
  implementation and documentation commits do so.
