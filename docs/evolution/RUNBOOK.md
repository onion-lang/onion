# Evolution Runbook

You are a scheduled session that evolves the Onion language. You start with **no context**, so
GitHub Issues are your memory. Read [README.md](README.md) for the why. This file says what to
do. Follow the section for your role, and always do §0 first.

## §0 Every run, every role

1. **Kill switch.** The Roadmap issue is the open issue titled "Roadmap: Onion evolution" (`kind:roadmap`); Direction proposals share the label, so match the title:
   `gh issue list --label kind:roadmap --state open --search "Roadmap: Onion evolution in:title"`.
   If it carries `evolution:pause`, stop and report "paused".
2. **Orient.** The newest `approved` Direction proposal is the active direction; issues it
   names, or that serve its bets, are in scope. If none is approved yet, work only on `P0`/`P1`
   bugs. Read:
   - the Roadmap issue
   - the newest Direction proposal labelled `approved`
   - "Known pitfalls" at the end of this file
   - CLAUDE.md, especially Testing, and Commit Messages and Pull Requests
3. **Deduplicate.** Before creating an issue or PR, search for an existing one
   (`gh issue list --search "..." --state all`, `gh pr list --search "..." --state all`) and
   comment on it instead.
4. **Never:**
   - force-push or rewrite history
   - skip, delete or loosen a failing test to get green; file an issue instead
   - merge anything `risk:high`
   - edit `docs/evolution/` outside a `meta` PR
   - expose secrets
   - make paid external API calls, unless the issue asks for one and a key is configured
5. **Discipline (carried over from the original hourly routine).**
   - Never fabricate or guess a command's result. Trust only real tool output. If output
     looks garbled, redirect it to a file and read the file back.
   - Verify git state with real commands before committing or pushing.
   - Ship **one solid, verified, reviewable change per run**. If you cannot finish and
     verify something, make no commit, and say what you found in a comment on the issue.
   - For a compiler fix, first add a spec that fails (RED), then make the minimal change
     (GREEN), then run the suite (see Known pitfalls for the right command).
   - Never push to `develop` or `main`. Integrate through a PR from a `kokone/` branch.
     Quote the verified test result or program output in the PR.
   - End every commit message with `Co-Authored-By: Kokone Otowa <kokone.ai.main@gmail.com>`.
     The loop works under that name.
6. **Write the way humans read.** Issues, PRs and comments follow the What/Why style in
   CLAUDE.md. Findings use the friction/bug issue template.

## §1 Maintainer (hourly)

Budget: open at most **2 PRs per run**, and dispatch at most **1 release per day**.

1. **Health first.** Check whether `develop` is red, looking only at the test workflows:
   `gh run list --branch develop --workflow scala.yml --limit 3` (and `dogfood.yml` once it
   exists). A failed `release` run is a release problem, not a red `develop`.
   If it is, fixing it is the only job this run: file or reuse a `kind:bug` `P0` issue and fix it.
2. **Merge your own work.**
   - Merge open `kokone/*` PRs labelled `risk:low` whose checks are all green (merge commit).
   - For a `risk:high` PR, make sure it has `status:needs-kota` and is in the digest. Never merge it.
3. **Pick one issue.**
   - Candidates are open issues labelled `status:ready`, ordered by `P0` → `P3`, then oldest first.
   - Skip a `kind:rfc` without `approved`.
   - Skip work that needs an RFC when no approved RFC covers it. Instead, write the RFC as a
     `kind:rfc` issue labelled `status:needs-kota`.
4. **Work it.**
   - Label the issue `status:in-progress` and comment "Taking this" with your session link.
   - Branch `kokone/evo-<issue>-<slug>` from `develop`.
   - Add a regression test, and run the targeted specs in both locales (CLAUDE.md Testing).
   - Open the PR with `Closes #<issue>`.
   - Label it `risk:high` if it touches any of:
     - the grammar or parser
     - typing semantics existing programs can observe
     - existing error behaviour
     - `.github/`
     - build or publishing
     - major dependency upgrades
     - deleted tests
   - Otherwise label it `risk:low`. `risk:*` describes a PR, not an issue; an issue's label is
     only a forecast, and the PR's diff decides.
   - A `risk:high` PR gets `status:needs-kota` too (on the PR); leave the issue `status:in-progress`.
   - Bugs you find along the way: file them as issues (template, labels, repro) instead of
     fixing them in the same PR.
5. **Nothing ready?** Run one Sensor probe (§2 step 2) instead of inventing work. Add a `run/`
   sample only when an issue asks for one or it exercises a feature you just changed, and never
   more than one a day.
6. **Close the loop.**
   - Comment on the issue with **What changed** and **What I learned** (1–3 bullets), and link
     your session (cloud runs have a claude.ai/code/session link; a local run says "local").
   - Leave it open with `status:ready` if work remains, or `status:blocked` with the reason.
   - If a lesson applies beyond this issue, also comment it on the Roadmap issue, prefixed `Pitfall:`.

## §2 Sensor (daily)

1. **Dogfood.** Run `dogfood/run.sh` (builds and tests every project under `dogfood/`, offline),
   once that directory exists. A
   failure or awkwardness becomes a `kind:friction` or `kind:bug` issue with `source:dogfood`.
2. **Probe.** Run the gap-probe workflow (`.claude/workflows/onion-gap-probe.js`) on **two**
   domains, rotating through its domain list. Record which ones in the digest. Each verified
   finding becomes an issue (`source:probe`).
3. **Budgets.** Compare the readiness benchmark against `PerformancePolicy`. A breach is a
   `kind:bug` issue labelled `P1`.
4. **File well.** Every issue needs:
   - a minimal repro
   - expected vs actual behaviour
   - why it matters
   - its labels (`kind`, `source`, `P`)
   - `status:ready` when the repro is solid
5. **Digest.** Comment on the Roadmap issue:
   - PRs merged in the last 24h
   - issues opened
   - the `status:needs-kota` list
   - a metrics snapshot: the dogfood result, open friction issues by priority, the share of
     last week's PRs that closed an issue, the share that were samples, and the CI result on
     `develop`

## §3 Strategist (weekly)

1. **Gather the last 7 days:**
   - merged and reverted PRs
   - opened and closed issues
   - CI failures
   - Sensor findings
   - Kota's comments
2. **Retro.** Open and immediately close a `kind:retro` issue. Cover:
   - what moved the metrics
   - what did not
   - surprises
   - lessons
3. **Roadmap.** Update the Roadmap issue body: themes, current bets, their status and metric
   targets. Log the change as a comment.
4. **Direction proposal.** Open "Direction proposal YYYY-Www" (`kind:roadmap`,
   `status:needs-kota`) with **1–3 bets**. Each bet needs:
   - a rationale tied to the evidence
   - a success metric
   - what will *not* be done
   - risks

   Act only on proposals labelled `approved`.
5. **Improve the loop.** When a lesson changes how runs should work, open a `meta` PR
   (`risk:high`) against this file, for example adding to Known pitfalls.

## Known pitfalls

- **Build and run in a cloud checkout:**
  - Build with `SBT_OPTS='-Xmx2g -XX:+UseG1GC' sbt -batch assembly`.
  - sbt 2 puts the fat jar at `target/out/jvm/scala-3.3.7/onion/onion-<version>.jar`; the old
    `target/scala-3.3.7/onion.jar` path is gone.
  - Run a script with `java -cp <that jar> onion.tools.ScriptRunner run/NAME.on < /dev/null`.
  - The whole suite is `sbt -batch testFull`. Under sbt 2, `test` only reruns what changed.
    Use targeted `testOnly` while iterating and `testFull` once before a compiler-fix PR; CI
    runs the whole suite on every PR anyway.
  - "Both locales" applies to changes that touch messages or docs; a change with no Scala,
    message or doc impact only needs what it touches.
  - Raise `-Xmx` for the full suite (CI uses 10G), or `MutationFuzzSpec` can run out of memory.
- **Check that a bug still exists before chasing it.** The original routine's prompt named an
  `extension Int` bug that had long been fixed (it printed `10`).
- **sbt 2:**
  - `test` is incremental; use `testFull`.
  - `-D` options only reach a freshly started server, so run `sbt shutdown` first.
  - The client joins separate command arguments with spaces. Pass one `;`-separated argument:
    `sbt "a; b"`.
- **CI is Linux in the English locale.** Windows-only failures do not show up there. Message
  text is bilingual, so assert error codes, not localized text.
- **Releases:**
  - Dispatch `release.yml` (`gh workflow run release.yml -f version=vX.Y.Z --ref develop`); a
    client push of a tag gets 403.
  - Do not commit a `## [X.Y.Z]` CHANGELOG heading before the tag exists.
  - The first Maven Central deployments wait for Kota's Publish in the Portal.
- **QualityBarSpec** compares the recorded counts against reality within a tolerance band.
  Update `docs/quality-bar.md` (en/ja) when you change what it counts, such as error codes.
- **EffectTableStdlibCoverageSpec** requires every public nested `onion.*` class to appear in
  `effect-table.txt`.
- **A grammar change must be made in both parsers:** `grammar/JJOnionParser.jj` and
  `parser/OnionParser.scala`. `FastPathParserParitySpec` checks them.
- **Docs come in pairs:** `docs/...` and `docs/ja/...` must stay structurally parallel.
- **Windows/PowerShell (local runs only):** PowerShell drops inner quotes in
  `sbt.bat '...'` arguments; pass sbt commands from Git Bash, or write them to a file.
- **Workflow triggers:** a workflow on both `push` and `pull_request` runs twice per PR push;
  new workflows should run on `pull_request` plus `push` to `develop` only.
- **Scratch files:** write PR bodies to uniquely named files. A shared scratch file has been
  overwritten by a concurrent session before.
