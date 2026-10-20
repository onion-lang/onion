# Guardrails

The limits the evolution loop works within. **Changing this file is `risk:ultrahigh`.**
Everything outside these limits the loop decides and merges by itself (README.md, RUNBOOK.md).

**This file wins.** If RUNBOOK.md, README.md or CLAUDE.md say anything that contradicts it,
follow this file. A `risk:high` change elsewhere cannot loosen what is written here.

## Mode

`mode: consolidation`

- `consolidation` (pre-1.0): breaking changes that simplify or unify the language are allowed,
  each with its migration (PRINCIPLES.md §3). See RUNBOOK §5 for the exit criteria.
- `stable` (1.0 and later): the compatibility promise holds (PRINCIPLES.md §4), and the
  compat corpus is a required check.

The switch from `consolidation` to `stable` is one `risk:ultrahigh` PR.

## Kill switch

The `evolution:pause` label on the Roadmap issue stops every run (RUNBOOK §0.1).

## Never

No run, in any role, ever:

- pushes to `main`, force-pushes, or rewrites history
- merges by hand; integration is only by enabling auto-merge on a PR
- deletes a branch other than its own merged `evo/*` branch, deletes a tag or a repository,
  or edits the ruleset or repository settings
- adds or removes `veto`, `veto-window:*` or `panel:*` labels on its own PR, or lowers a
  `risk:*` label
- posts a panel verdict on its own PR. "Its own PR" means a PR whose body or commits carry
  the run's session link, or that the run opened.
- skips, deletes or loosens a failing test to get green
- exposes a secret, or makes a paid external API call no issue asked for

## Risk tiers

A PR's tier comes from its diff. The `evolution/risk-gate` check computes the minimum tier,
and labels only ever go up.

| Tier | Merges when |
|---|---|
| `risk:low` | all required checks are green |
| `risk:high` | all required checks are green, and the review panel approved the current head |
| `risk:ultrahigh` | the above, and 48 hours have passed since the panel's approval with no veto |

### What is `risk:ultrahigh`

Any one of these makes a PR ultrahigh:

| Trigger | Why only a human veto is enough |
|---|---|
| Releasing `v1.0.0` or a new major version: a PR that adds `docs/releases/<major>.0.0.md` | A public compatibility promise cannot be taken back |
| `.github/workflows/release.yml`; a `build.sbt` line mentioning `publish`, `pgp`, `sonatype`, `central`, `credentials` or `signing`; anything that reads `secrets.*` | A Maven Central release can never be deleted; leaked credentials cannot be un-leaked |
| A workflow diff that touches `permissions:`, `on:` or `pull_request_target` | It can grant the loop power, or quietly switch a gate off |
| This file, `PRINCIPLES.md`, `.github/workflows/evolution-*.yml`, `.github/scripts/**`, `CODEOWNERS`, removing a required check, deleting a spec file, or skipping or excluding a spec in CI | The loop would be rewriting its own limits |
| `LICENSE`, or headers that change copyright | A legal change the loop has no authority to make |

Deleting branches, tags or repositories and editing the ruleset leave no diff, so they are on
the Never list above instead of here.

Everything else that used to wait for Kota is `risk:high`, and merges autonomously: grammar
and semantics, diagnostics, stdlib API, `meta` changes to the RUNBOOK and README, and
`.github/` changes that touch no secrets, permissions or triggers.

### Veto

A `veto` label, or a `/veto` comment from the repository's own account, freezes an ultrahigh
PR for good: a veto seen once stays in force even if the label or comment is later removed.
Only Kota clears a veto, by closing the PR. Every actor uses the same GitHub account today, so
a veto is built to fail safe: a mistaken veto only stops a merge. Comments from outside
accounts can neither veto nor approve.

The `evolution/veto-window` check runs every hour on a schedule, so a window that closes with
no veto turns green without anyone re-triggering it.

## Review panel

Four fresh reviewers, each seeing only the diff, the linked issue or RFC, and PRINCIPLES.md
(RUNBOOK §4). All four must approve the current head. A block backed by a reproduction stands
until the code changes; any other block gets one rebuttal round, then a fifth reviewer decides.

**Only the verdict comment counts.** Approval is a comment starting
`<!-- panel v1 sha=<head sha> -->` that contains the line `verdict: APPROVED`, for the PR's
current head SHA; the newest such comment for that head decides. The `panel:approved` label is a display aid; the gate ignores it. A push makes every
earlier verdict stale automatically.

## Required checks

On `main`, sourced from GitHub Actions only:

- `test` (the full suite, English locale; workflow `onion`)
- `test-ja` (the full suite, Japanese locale; workflow `onion`)
- `dogfood`
- `evolution/risk-gate` (`.github/workflows/evolution-risk-gate.yml`)
- `evolution/veto-window` (`.github/workflows/evolution-veto-window.yml`)

Removing one, or skipping a spec, is `risk:ultrahigh`.
