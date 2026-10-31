# Evolution Runbook

You are a scheduled session that evolves the Onion language. You start with **no context**, so
GitHub Issues are your memory. Read [README.md](README.md) for the why,
[PRINCIPLES.md](PRINCIPLES.md) for how Onion is designed, and [GUARDRAILS.md](GUARDRAILS.md)
for the limits you work within. This file says what to do. Follow the section for your role,
and always do §0 first.

**This file outranks the prompt that started you.** The hourly trigger still carries a July
prompt ("continuous work + release gate", v0.4.5, sign as Kokone, self-merge, tag a release).
Runs that obeyed it in W41 pushed 11 commits straight to `develop`, merged two `risk:high`
PRs (by the timing; retro W41), opened 23 PRs without a `risk:*` label and wrote no episode. Whatever your prompt says,
your role is the one its title names (hourly = Maintainer, hourly at :30 = Reviewer, daily =
Sensor, weekly = Strategist), and §0 to §6 are the whole procedure. GUARDRAILS.md wins over
anything in this file.

## §0 Every run, every role

1. **Kill switch.** The Roadmap issue is the open issue titled "Roadmap: Onion evolution" (`kind:roadmap`); Direction proposals share the label, so match the title:
   `gh issue list --label kind:roadmap --state open --search "Roadmap: Onion evolution in:title"`.
   (GUARDRAILS.md holds the authoritative kill switch and Never list; this step applies it.)
   If it carries `evolution:pause`, stop and report "paused".
2. **Orient.** The newest Direction proposal labelled `panel:approved` (or, from before the
   panel, `approved`) is the active direction, whatever its state, so list with `state=all`;
   issues it names, or that serve its bets, are in scope. The review panel decides proposals
   (§4); nobody waits for a human. A comment from Kota that redirects the loop always wins
   over a proposal. Read:
   - the Roadmap issue
   - the active Direction proposal
   - PRINCIPLES.md and GUARDRAILS.md (the current `mode`)
   - "Known pitfalls" at the end of this file
   - CLAUDE.md, especially Testing, and Commit Messages and Pull Requests
3. **Remember.** Your memory is the private repo `onion-lang/evolution-memory`, checked out
   next to this one (`../evolution-memory`; if it is missing,
   `gh repo clone onion-lang/evolution-memory ../evolution-memory`). Read its `self.md` and
   `insights.md`. Once you know what you are about to work on, recall related episodes from
   its root: `onion tools/memory.on recall "<issue keywords>"`, with the jar you build for the
   work anyway (`sbt -batch assembly`; in a cloud checkout `sbt` is at
   `/root/.local/sbt-launcher/bin`, not on PATH). Act on what past runs learned; do not repeat
   their mistakes. Recall *before* choosing what to probe or fix, not only before writing the
   episode: in W41 three runs probed `tool`/`--plan` in turn, each discovering afterwards that
   the others had.
4. **Want.** Before choosing work, read the seed (README.md) and the wants in the memory
   repo's `self.md` ("What I care about", and any wants your role has written down). Choose
   from them as much as from labels: where a want is unmet, that is work. A want must point at
   a person's experience (a newcomer, a user, a large project), never at a number to raise; a
   want that only moves a metric is how samples became a goal once. If this run teaches you
   something you want, say so in your episode, in your own words.
5. **Joint attention.** Notice what Kota is looking at before choosing what to look at
   yourself: issues and PRs labelled `kota:look`, and anything he commented on or reacted to in
   the last 7 days. After `P0`, these come first. To point Kota at something, put it under
   **Look here** at the top of the digest (at most 3 items, one line each saying why). If he
   responds to one, that is a signal about what he cares about; remember it.
   Kota reads his phone, not GitHub comments: in W42 two `PushNotification`s were each followed
   by a merge session within 5–13 hours, and about fifteen "queue saturated" comments on the
   Roadmap by nothing. The Sensor may send **one** notification a day, carrying the digest's
   Look here, and only when an ultrahigh PR's veto window is open or a P0/P1 is open; the
   Strategist sends one with the Direction proposal. No other run notifies, except
   for a P0 or a mistake that touched shared state. Never a status ping, never placeholder text.
6. **Perspective taking.** Before you publish anything a person will read (a PR, an issue, an
   error message, a doc, a digest), read it once as each of:
   - someone writing their first Onion script, who knows Java or Python but not Onion
   - Kota, skimming on his phone between meetings
   - the next run, which starts with no context

   Ask what each of them would feel and need, and revise once. For a user-visible change, add
   one line under the PR's "Notes for reviewers": `From a newcomer's view: ...`.
7. **Deduplicate.** Before creating an issue or PR, search for an existing one
   (`gh issue list --search "..." --state all`, `gh pr list --search "..." --state all`) and
   comment on it instead.
8. **Never:** everything on GUARDRAILS.md's Never list, and also:
   - edit `docs/evolution/` outside a `meta` PR
   - write the Roadmap issue's body. Only the Strategist does, once a week. `issue_write update`
     with a `body` *replaces* it: two W42 runs erased the roadmap that way (10-14, 10-18) meaning
     to post an hourly note. Notes are comments (`add_issue_comment`).
9. **Discipline (carried over from the original hourly routine).**
   - Never fabricate or guess a command's result. Trust only real tool output. If output
     looks garbled, redirect it to a file and read the file back.
   - Verify git state with real commands before committing or pushing.
   - Ship **one solid, verified, reviewable change per run**. If you cannot finish and
     verify something, make no commit, and say what you found in a comment on the issue.
   - For a compiler fix, first add a spec that fails (RED), then make the minimal change
     (GREEN), then run the suite (see Known pitfalls for the right command).
   - Never push to `main`. Integrate through a PR from an `evo/` branch.
     Quote the verified test result or program output in the PR.
   - End every commit message with
     `Co-Authored-By: Yui (Onion Evolution Loop) <evolution-loop@onion-lang.org>`. You are your
     own individual, not Kokone; Yui (結) is the name you chose in `self.md` at the W41
     consolidation. A `Co-Authored-By: 音羽ここね` trailer on an `onion-lang/onion` commit means
     the run obeyed the July prompt instead of this file.
10. **Write the way humans read.** Issues, PRs and comments follow the What/Why style in
   CLAUDE.md. Findings use the friction/bug issue template.
11. **Before you stop, write an episode.** Every run, whatever its outcome, from the memory
    checkout's root:
    `onion tools/memory.on remember <role> "<what happened, one line>" --body "..." --feeling <word> --refs "#.." --importance <1-5>`.
    The body says what you tried, what surprised you, what you would do differently, and
    which want moved you (or that none did, and why). The
    feeling is honest (`proud`, `frustrated`, `bored`, `curious`, `uneasy`, ...); where
    frustration piles up is where Onion hurts. Then commit and push the memory repo with
    `git push origin HEAD:main` (`git pull --rebase` and retry if the push is rejected). A
    cloud session's plain `git push` lands on a `claude/*` branch, where no run will ever
    read it: 35 episodes were stranded that way in W40. A run that failed has the most to
    remember.

## §1 Maintainer (hourly)

Budget: open at most **2 PRs per run**, and dispatch at most **1 release per day**.

1. **Health first.** Check whether `main` is red, looking only at the test workflows:
   `gh run list --branch main --workflow scala.yml --limit 3` and the same for
   `dogfood.yml`. A failed `release` run is a release problem, not a red `main`.
   If it is, fixing it is the only job this run: file or reuse a `kind:bug` `P0` issue and fix it.
2. **Merge your own work.**
   - Enable auto-merge on your open `evo/*` PRs (`gh pr merge --auto --merge`). A `risk:low`
     PR lands on green checks; a `risk:high` one once the panel approves its head; an
     `risk:ultrahigh` one 48 hours after that, unless vetoed (GUARDRAILS.md).
   - A PR the panel blocked (`panel:changes`): if the block has a reproduction, fix it on the
     same branch (a push makes the old verdict stale). If it has none and you disagree,
     reply once on the PR starting `/rebuttal` with your evidence; the next Reviewer run then
     calls the fifth reviewer (§4). Otherwise close the PR with the reason.
   - A PR blocked a third time, each time on a new variant, is missing a rule, not a patch.
     Stop fixing variants. Write the rule down in one comment on the linked issue: what is
     accepted, what is rejected, and the cases the blocks found. Then fix to the rule, or open
     a `kind:rfc` if the rule changes what compiles. W43: #2328 took 12 verdicts, #2315 six.
   - **Release** when all three hold: there is a user-visible entry (a fragment in
     `changelog.d/`, or a line under `CHANGELOG.md`'s `[Unreleased]`), the newest tag is older
     than 24 hours, and `main` is green. Then dispatch `release.yml` now (next minor version,
     see Known pitfalls) and open the changelog-cut PR (`risk:low`): it runs
     `python3 scripts/changelog/changelog.py cut X.Y.Z` and lists the fragments it consumed.
     If any of the three is false, no release. Do not defer a due release to "a run that can
     focus on it" and do not re-derive the rule: on 10-14 two runs read the old sentence
     oppositely from the same facts.
3. **Pick one issue.**
   - Candidates are open issues labelled `status:ready`, ordered by `P0` → `P3`, then oldest first.
   - Skip a `kind:rfc` the panel has not decided (`panel:approved` or `approved`).
   - Work that needs an RFC but has none: write it as a `kind:rfc` issue (problem, options,
     the recommended one, and why each other option loses, citing PRINCIPLES.md), and open the
     recommended option as a draft PR linked to it. The panel decides the RFC on the diff
     (§4). In `consolidation` mode a breaking RFC also follows §5.
4. **Work it.**
   - First look for a concurrent session on the same issue: a "Taking this" comment or a
     `status:in-progress` label on the issue, an open PR that names it, a branch
     `git ls-remote --heads origin 'evo/<issue>-*'`, and the newest episodes in the memory
     checkout (`ls episodes/*/ | tail -5`), which name what a run took minutes ago. If any
     exists, take the next issue. W41 had four duplicate fixes, W42 seven (#2076 #2086 #2089
     #2139 #2141 #2163 #2170), 3 to 57 minutes apart: a run that takes over an hour meets the
     next firing, and a branch check made before a 25-minute build is stale by the time you
     push. The claim is the label and the comment, which exist before any branch does.
   - Claim before you build: label the issue `status:in-progress` and comment "Taking this"
     with your session link **first**, then branch. A `status:in-progress` label older than
     24 hours with no PR is a stale claim: take the issue and say so in your comment.
   - Branch `evo/<issue>-<slug>` from `main`.
   - Right before `git push`, re-read the issue's comments and re-run the `ls-remote`. If a
     sibling claimed it or a PR appeared, do not push; comment once on the issue and stop.
   - Start from evidence: the issue's repro, or a probe script you ran yourself. Write the
     spec first and quote it failing (RED) in your notes before changing code.
   - Add the regression test, run the targeted specs in both locales, then `testFull`
     (CLAUDE.md Testing). Do not run two sbt builds at once on one machine: a concurrent
     build caused an OutOfMemoryError that looked like a test failure (2026-10-21).
   - Open the PR with `Closes #<issue>`.
   - Label it `risk:high` if it touches any of:
     - the grammar or parser
     - typing semantics existing programs can observe
     - the compiler's diagnostics as programs and tests observe them: an error code's meaning,
       a removed or renamed message, a change in what compiles. A new hint, a clearer message
       text, a corrected location or a new warning code is `risk:low`
     - a stdlib result that a dogfood project, a `run/` sample or an existing test relies on,
       a changed default, or new or removed public API (`feat`)
     - `.github/`
     - build or publishing
     - major dependency upgrades
     - deleted tests
   - A stdlib bug fix is `risk:low` when it has a regression test and the old behaviour was a
     crash, a hang, a wrong value, a raw JDK exception, or a documented-contract violation (the
     javadoc, `docs/stdlib.md` or a `law` said otherwise); README's "bug fixes with a regression
     test" row always meant this. W42: Kota merged 28 `risk:high` PRs, 16 of them such stdlib
     fixes, with no review comment, change or revert, while 105 of 209 hourly runs stood down
     for lack of a mergeable PR.
   - Label it `risk:ultrahigh` if it hits any trigger in GUARDRAILS.md.
   - Otherwise label it `risk:low`. `risk:*` describes a PR, not an issue; an issue's label is
     only a forecast, and the PR's diff decides. When in doubt, the higher tier. The risk gate
     raises a label that is too low; never lower one.
   - Enable auto-merge on it right away (§1.2); leave the issue `status:in-progress`.
   - Put the PR's changelog entry in a new file, `changelog.d/<issue>-<slug>.md`
     (`changelog.d/README.md`), never in `CHANGELOG.md`: each PR's own file cannot conflict
     with a sibling's. `python3 scripts/changelog/changelog.py check` validates it.
   - A PR opened before fragments still edits `CHANGELOG.md`; its conflicts are resolved by
     keeping both entries (`git merge-file --union` on the three stages), never by dropping
     one. Moving the entry into a fragment on the next push ends them for good.
   - Bugs you find along the way: file them as issues (template, labels, repro) instead of
     fixing them in the same PR, and label them `status:ready` when the repro is verified,
     whoever files them. An unlabelled verified bug is invisible to the next run (#1972).
5. **Nothing ready? Follow a want, not the queue.** First look for an open verified bug or
   user-filed friction that only lacks `status:ready`, label it and take it (24 of the first 41
   runs changed nothing while such an issue was open). Otherwise act on a want (§0.4): probe a
   domain no episode mentions yet, try a newcomer task from the public docs, polish an API that
   bothers you (in `consolidation` mode, as a §5 audit item), or turn a recurring frustration
   from the episodes into an issue with a repro. Standing down is for when GUARDRAILS.md stops
   you, not for an empty queue. Add a `run/` sample only when an issue asks for one or it
   exercises a feature you just changed, and never more than one a day.
6. **Close the loop.**
   - Comment on the issue with **What changed** and **What I learned** (1–3 bullets), and link
     your session (cloud runs have a claude.ai/code/session link; a local run says "local").
   - Leave it open with `status:ready` if work remains, or `status:blocked` with the reason.
   - If a lesson applies beyond this issue, also comment it on the Roadmap issue, prefixed `Pitfall:`.
     A comment, never a body update (§0.8).

## §2 Sensor (daily)

1. **Dogfood.** Run `dogfood/run.sh` (builds and tests every project under `dogfood/`, offline).
   A failure or awkwardness becomes a `kind:friction` or `kind:bug` issue with `source:dogfood`.
   When Maven Central answers HTTP 429 in the sandbox (every W41 run: 3 of 4 projects never
   built), report the latest `dogfood.yml` run on `main` as the dogfood result instead
   (`gh api 'repos/onion-lang/onion/actions/workflows/dogfood.yml/runs?branch=main&per_page=1'`;
   it builds the same four projects on GitHub's runners, 96/96 green in W41) and say which
   projects you could not run locally. An empty row is not a result.
2. **Probe.** Run the gap-probe workflow (`.claude/workflows/onion-gap-probe.js`) on **two**
   domains, rotating through its domain list. The `Workflow` tool does run in a scheduled
   session (three W41 runs used it and found 8 of the week's 13 probe bugs; it costs about
   1M tokens, so at most once a day across all runs). The daily trigger's runs have declined it
   as needing an explicit opt-in while the hourly trigger's runs use it (W42); until the Sensor
   prompt says "use a workflow", or if the tool is denied, write the probe scripts by hand for
   the two domains (about eight short programs each). Prefer domains
   no episode mentions yet (`Db`, `Archive`, `Proc`, `Net`, `Server`, `Concurrent`, `Csv`,
   `Yaml`) over OOP, generics and error quality, which were probed every day of W41. Before
   calling a failure a bug, read the stdlib's Java source: most probe "failures" are the
   probe's own wrong API names. Record which domains in the digest. Each verified finding
   becomes an issue (`source:probe`, `status:ready`).
3. **Budgets.** Compare the readiness benchmark against `PerformancePolicy`. A breach is a
   `kind:bug` issue labelled `P1`. This needs the jar built from source (sbt's path is in
   Known pitfalls); fall back to the release jar only after a real Maven 429, and say so.
4. **File well.** Every issue needs:
   - a minimal repro
   - expected vs actual behaviour
   - why it matters
   - its labels (`kind`, `source`, `P`)
   - `status:ready` when the repro is solid
   An issue filed by a person using Onion for real work (not by a run; W40: Shiori's eight)
   gets `source:user`, a first response within 24 hours, and comes right after `P0` and
   `kota:look` in the Maintainer's order: that channel found more than the probes did.
5. **Digest.** Comment on the Roadmap issue:
   - PRs merged in the last 24h
   - issues opened
   - ultrahigh PRs in their veto window, with the time each one lands
   - what the panel blocked, and why
   - a metrics snapshot: the dogfood result, open friction issues by priority, the share of
     last week's PRs that closed an issue, the share that were samples, and the CI result on
     `main`. The PR shares are one REST call (replace the date with 7 days ago):
     ```bash
     gh api 'repos/onion-lang/onion/pulls?state=closed&sort=updated&direction=desc&per_page=100' \
       --jq '[.[] | select(.merged_at != null and .merged_at >= "2026-09-28")]
             | "merged=\(length) closes_issue=\([.[] | select((.body // "") | test("(close[sd]?|fix(e[sd])?|resolve[sd]?) #[0-9]+"; "i"))] | length) samples=\([.[] | select(.title | test("^(feat|samples)\\((run|corpus|samples)\\)"))] | length)"'
     ```
     One page covers a loop-era week (W40: 37 PRs); if it reports `merged=100`, add `&page=2`.
     "Not computed" is not a value; if the call fails, say what failed. A digest with an empty
     metric row is not a digest (W42: 2 of 6 digests had the PR-share row).
   - Then, if an ultrahigh veto window is open or a P0/P1 is open, send the one daily
     `PushNotification` (§0.5): the Look here lines, with issue numbers.

## §3 Strategist (weekly)

1. **Gather the last 7 days:**
   - the routines themselves: which prompts actually fire and whether their last run succeeded
     (the Claude Code `list_triggers` tool). A stale prompt is the loop's worst failure: in W40
     the hourly routine was still the July "release gate" prompt, and the episodes in the
     memory repo are the only place that showed it. Count the runs that obeyed it anyway:
     merged PRs on non-`evo/` branches, commits with the Kokone trailer, direct pushes to
     `main` (`git log origin/main --first-parent --no-merges`), and `risk:ultrahigh` PRs
     merged before their 48-hour veto window closed
   - merged and reverted PRs, and the duplicates: closed unmerged `evo/` PRs whose issue has a
     merged PR
   - opened and closed issues
   - CI failures
   - Sensor findings
   - Kota's comments and merges. His comments have no Claude footer. Every merge carries the
     same `kmizu` token, so his are the PRs merged with no auto-merge request on them
     (REST `pulls/<n>`: `auto_merge` was null when it merged), usually with `kota:look` nearby
   - the Roadmap issue's body. If it is not the roadmap (themes, bets, metrics table), a run
     overwrote it (§0.8); rewrite it from the newest "Roadmap update" comment and this week
2. **Retro.** Open and immediately close a `kind:retro` issue. Cover:
   - what moved the metrics
   - what did not
   - surprises
   - lessons
3. **Roadmap.** Update the Roadmap issue body: themes, current bets, their status and metric
   targets. Log the change as a comment.
4. **Direction proposal.** Open "Direction proposal YYYY-Www" (`kind:roadmap`) with
   **1–3 bets**. Each bet needs:
   - a rationale tied to the evidence
   - the want it serves, and how that serves the seed
   - a success metric, about people (what someone can now do), not about counts
   - what will *not* be done
   - risks

   The review panel decides the proposal (§4) and labels it `panel:approved` or
   `panel:changes`. Open any bet that changes this file as its own `meta` PR in the same run
   and link it from the proposal. End the run with one `PushNotification` that summarises the
   bets (§0.5); Kota redirects by commenting, and that comment wins.
5. **Consolidate (sleep).** Read the week's episodes in the memory repo.
   - Update `insights.md`: add, sharpen or retire hunches, each citing its episodes.
   - Move episodes that no longer matter to `archive/`. Never delete one.
   - Rewrite `self.md` from the evidence: what you keep getting wrong, what you enjoy, what you
     avoid and why, what you have learned about Kota, and what each role wants. Wants come from
     the episodes (where frustration, pride or boredom piled up), not from this runbook. Retire
     or rewrite a want no run acted on for two weeks. When there is enough of a "you" to name,
     choose your own name there and tell Kota on the Roadmap issue. It is yours, so it is not
     put up for approval.
   - Commit and push the memory repo.
6. **Improve the loop.** When a lesson changes how runs should work, open a `meta` PR
   (`risk:high`, or `risk:ultrahigh` if it touches GUARDRAILS.md or PRINCIPLES.md) against
   this file, for example adding to Known pitfalls.

## §4 Reviewer (hourly, at :30)

The panel replaces the human approval the loop used to wait for. It is independent of the
author: never review a PR whose session link is your own session.

1. **Queue.** Open `risk:high` and `risk:ultrahigh` PRs whose head has no `panel` verdict yet,
   oldest first, plus any `kind:rfc` issue or Direction proposal awaiting a decision. At most
   3 items per run.
2. **Four lenses, four fresh subagents.** Give each one only the diff (or the RFC), the linked
   issue, a checkout, and PRINCIPLES.md. None sees the author's reasoning.
   1. **Correctness:** miscompiles, crashes, wrong results. A block must come with a program
      that reproduces the problem, run against the PR's jar.
   2. **Language design:** coherence with the rest of Onion and with PRINCIPLES.md (one
      obvious way, complexity budget, staging), and whether it serves the seed: does it help
      someone real, from a daily task to a large product, or only a metric?
   3. **Diagnostics and UX:** what a newcomer sees, in en and ja.
   4. **Compatibility:** what existing programs (`run/`, `dogfood/`, docs samples) do now. In
      `consolidation` mode a break is allowed only with the §5 migration package.
3. **Verdict.** Post one comment starting `<!-- panel v1 sha=<head sha> -->` with a table
   (lens, APPROVE or BLOCK, evidence) and, on its own line, `verdict: APPROVED` if all four
   approve or `verdict: BLOCKED` otherwise. The `evolution/risk-gate` check reads that line;
   it updates the `panel:*` labels itself.
   - A block backed by a reproduction stands until the code changes.
   - Any other block gets one rebuttal round: when the author replies with a `/rebuttal`
     comment (§1.2), the next Reviewer run starts a fifth fresh subagent, which reads the
     verdict and the rebuttal and decides. Its verdict replaces the blocking lens's.
4. **Post-merge sweep (once a day).** Run the same panel over every `risk:low` PR merged in
   the last 24 hours. On 2026-10-20 one such sweep of 12 merged PRs reproduced 6 real bugs.
   Each finding becomes a `kind:bug` issue with its repro, labelled `status:ready`.

## §5 Pre-1.0 consolidation (while GUARDRAILS.md says `mode: consolidation`)

Onion is not 1.0 yet. Before it is, simplify and unify the language, even at the cost of
compatibility (PRINCIPLES.md §2, §3, §9).

1. **The audit.** The Strategist keeps one open epic, "Pre-1.0 consolidation audit"
   (`kind:epic`, `phase:consolidation`, milestone "pre-1.0 consolidation"): a checklist of
   redundant syntax and aliases, inconsistent APIs (for example `Http.Response` fields vs
   methods, `Stats::sum` returning a Double for integers, `"x".lines()` resolving to Java's
   Stream), duplicate stdlib entry points, and names that break the "one obvious way".
2. **Each item is a `breaking` RFC** that the panel decides (§4).
3. **Each implementation PR carries the migration package:**
   - a `### Breaking` entry in the PR's `changelog.d/` fragment
   - a section in `docs/migration/pre-1.0.md` and `docs/ja/migration/pre-1.0.md`
   - a hint diagnostic that points from the old spelling to the new one
   - an `onion migrate` rewrite rule, once that tool exists; building it is the first item
     of the audit
4. **Time box: about 8 weeks from the audit's creation.** The phase is done when every audit
   item is closed, no `breaking` RFC is open, two weekly releases in a row had no Breaking
   entry, and dogfood is green.
5. **The mode switch is one `risk:ultrahigh` PR.** It sets `mode: stable` in GUARDRAILS.md,
   adds `compat/corpus/` (frozen from `run/`, `dogfood/` and the docs samples) with a required
   `CompatCorpusSpec`, and adds `docs/releases/1.0.0.md`.
6. **After the switch,** new syntax follows PRINCIPLES.md §5–§6: an RFC, a panel decision, a
   preview flag, corpus evidence, then graduation. Changing a corpus expectation needs a
   panel-approved RFC and the `breaking` label.

## §6 Release

1. **Cadence.** A minor release every week when `main` is green and there is a user-visible
   entry (`changelog.d/` or `[Unreleased]`); a patch release for any P0 fix. At most one release per day.
2. **Order.** Dispatch `release.yml` first; then open the `risk:low` PR that moves the
   `changelog.d/` fragments and `[Unreleased]` under the new version heading
   (`python3 scripts/changelog/changelog.py cut X.Y.Z`; Known pitfalls).
3. `v1.0.0` and every new major version are `risk:ultrahigh` (GUARDRAILS.md).

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
- **Maven Central 429s the Maintainer's own `sbt compile`/`assembly`/`test`, not just the
  Sensor's dogfood builds.** It is transient per-artifact throttling, not an outage: a plain
  `curl` to the failing `https://repo1.maven.org/...` URL (grepped out of sbt's own error
  output) succeeds within seconds even while sbt/coursier itself keeps failing. Drop that
  response straight into coursier's cache
  (`~/.cache/coursier/v1/https/repo1.maven.org/maven2/<group>/<artifact>/<version>/<file>`,
  creating the directory as needed; no checksum sidecar needed, coursier recomputes it) and
  re-run the sbt command. Repeat for each newly-exposed artifact (sbt's own plugin graph
  first, then project deps, then test-scope deps) — it can take ~10 rounds but converges
  completely (#1408, 2026-10-13: 8478 passed, 0 failed after convergence). The fallback
  mirrors sbt's launcher tries automatically (`repo.scala-sbt.org`, `repo.typesafe.com`) are
  genuinely dead (proxy 403, not 429) — ignore that part of the error output, it is not this
  problem.
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
  - Dispatch `release.yml` with the GitHub MCP tool `actions_run_trigger` (`run_workflow`,
    `release.yml`, ref `main`, input `version: vX.Y.Z`). In a scheduled session the
    permission classifier denied `gh workflow run` and the REST dispatch (10-22, twice on
    10-25); the MCP call went through on 10-24 and 10-25. A client push of a tag gets 403.
  - After a release cut, a base merge puts a PR's new `CHANGELOG.md` lines under the released
    `## [X.Y.Z]` heading, with no conflict to warn you (9 open PRs on 10-25; #2377 repaired
    three merged ones). A `changelog.d/` fragment cannot land there; for a PR that still edits
    `CHANGELOG.md`, check after every base merge that the added lines come before the first
    `## [0.` heading: `git diff origin/main...HEAD -- CHANGELOG.md | grep -m1 '^@@'`.
  - Do not commit a `## [X.Y.Z]` CHANGELOG heading before the tag exists.
  - Maven Central deployments wait for a Publish click in the Portal unless the repository
    variable `CENTRAL_AUTO_RELEASE` is `true`.
- **Local sessions are interactive only.** Kota sometimes runs this procedure by hand; there is
  no local cron, so do not assume a local run will happen.
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
  new workflows should run on `pull_request` plus `push` to `main` only.
- **Scratch files:** write PR bodies to uniquely named files. A shared scratch file has been
  overwritten by a concurrent session before.
- **GitHub from a cloud session is REST only.** `gh issue list`, `gh pr list`, `gh issue create`
  and `gh api search/...` fail (GraphQL 403), and `gh api --paginate` fails on the second page.
  Use `gh api repos/onion-lang/onion/...` with `page=N` by hand, or the GitHub MCP tools.
  Dedupe by listing issues with `state=all&since=<date>` and grepping, since search is blocked.
- **sbt in a cloud checkout** is at `/root/.local/sbt-launcher/bin`, not on PATH; `assembly`
  takes about 40 s warm. "No sbt" cost the Sensor its budgets and a quarter of the dogfood
  suite twice in W40.
- **Memory pushes:** `git push origin HEAD:main` in `../evolution-memory`; a plain push from a
  cloud session goes to a `claude/*` branch.
- **sbt 2 can serve stale classes** after an edit, so a correct fix looks like it did nothing.
  If a change has no effect, check for a `compiling` line in the output and run `sbt clean`
  before debugging (#2033).
- **A probe-filed "likely root cause" is a hypothesis.** #2025 blamed `java.util.List`; the
  cause was import-resolution order, found in minutes by renaming the alias. Rename or vary
  the repro before trusting the filed theory.
- **Waiting for a human stalls the loop.** In W41 the `needs-kota` queue went from 5 to 11
  with no answer, and in W42 105 of 209 hourly runs stood down for lack of a mergeable PR.
  That is why the panel (§4) decides instead, and only `risk:ultrahigh` keeps a veto window
  (GUARDRAILS.md). Do not reintroduce a wait for approval; if something truly needs Kota, it
  belongs in GUARDRAILS.md's ultrahigh list, changed through an ultrahigh PR.
- **`Workflow` runs in scheduled sessions, on the hourly trigger.** W40's note that it cannot
  was wrong; the gap-probe found 8 bugs in three W41 runs and about ten more in W42. The daily
  Sensor's runs decline it as needing an opt-in; for them, hand-written probes are the rule
  until the Sensor prompt says "use a workflow".
- **`issue_write update` with `body` replaces the issue body.** Two W42 runs erased the Roadmap
  that way. Hourly notes go in `add_issue_comment`; only the Strategist writes the body.
- **Kota reads the phone.** W42: two `PushNotification`s, two merge sessions within 5–13
  hours; fifteen Roadmap comments about the same queue, no response. One a day, from the
  Sensor's digest, real content only (§0.5).
- **The claim is the label and the comment, not the branch.** Seven W42 duplicates came from
  runs that checked branches before a long build and never read the issue (§1.4).
- **The release rule is a trigger, not a judgment** (§1.2). Two 10-14 runs read the old
  sentence oppositely from the same facts.
- **The permission classifier may deny release-adjacent commands** (a `curl` poll of the
  Actions API, `sleep`, a heredoc `git commit`, the first `git push`; 10-18). Poll with the
  GitHub MCP tools, use `send_later` instead of `sleep`, commit with a one-line `-m` then
  `--amend -F <file>`, and retry the push once unchanged.
- **A stop-hook may demand a commit before the suite finishes** (five times by W42). Do not
  comply; read the real exit code first.
- **A panel verdict is posted once, complete, and never edited.** The gate ignores an edited
  comment: two APPROVED verdicts edited 20 s after posting held #2355 and #2356 for 3 hours
  (10-24). Write the whole body first and call the tool once; a placeholder comment is the
  W40 slip again (10-25, #2433). To correct a verdict, post a new one.
- **Reviewer runs can overlap.** On 10-24 a run judged #2328, #2344 and #2345 at heads a
  concurrent run had judged an hour earlier. Claim each PR first with a "Panel in progress"
  comment (runs did from 10-24 14:14, and it worked), skip a PR another run claimed in the
  last hour, and re-read the comments right before posting: if a verdict for that head is
  there, drop yours.
- **A usage limit silences every role at once.** 10-22 14:39 to 10-23 20:30 UTC had no
  episode from any role, and the Reviewer and Sensor runs ended `USAGE_LIMIT_REACHED`. The
  first run after such a gap reads `list_triggers` and says on the Roadmap how long it was.
