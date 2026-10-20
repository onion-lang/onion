# Guardrails

The limits the evolution loop works within. **Changing this file is `risk:ultrahigh`.**
Everything outside these limits the loop decides and merges by itself (README.md, RUNBOOK.md).

## Mode

`mode: consolidation`

- `consolidation` (pre-1.0): breaking changes that simplify or unify the language are allowed,
  each with its migration (PRINCIPLES.md §3). See RUNBOOK §5 for the exit criteria.
- `stable` (1.0 and later): the compatibility promise holds (PRINCIPLES.md §4), and the
  compat corpus is a required check.

The switch from `consolidation` to `stable` is one `risk:ultrahigh` PR.

## Kill switch

The `evolution:pause` label on the Roadmap issue stops every run (RUNBOOK §0.1).

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
| `.github/workflows/release.yml`, the publishing or signing settings in `build.sbt`, anything that reads `secrets.*` | A Maven Central release can never be deleted; leaked credentials cannot be un-leaked |
| A workflow diff that touches `permissions:`, `on:` or `pull_request_target` | It can grant the loop power, or quietly switch a gate off |
| This file, `PRINCIPLES.md`, `.github/workflows/evolution-*.yml`, `CODEOWNERS`, removing a required check, or skipping or excluding a spec in CI | The loop would be rewriting its own limits |
| `LICENSE`, or headers that change copyright | A legal change the loop has no authority to make |
| Deleting a branch, tag or repository, or editing the ruleset | Cannot be undone |

Everything else that used to wait for Kota is `risk:high`, and merges autonomously: grammar
and semantics, diagnostics, stdlib API, `meta` changes to the RUNBOOK and README, and
`.github/` changes that touch no secrets, permissions or triggers.

### Veto

Any `veto` label or `/veto` comment on an ultrahigh PR, **from any author**, freezes it. Only
Kota clears a veto, by closing the PR. A run never adds or removes `veto`, `veto-window:*` or
`panel:*` labels on its own PR. Every actor uses the same GitHub account today, so a veto is
built to fail safe: a mistaken veto only stops a merge.

## Review panel

Four fresh reviewers, each seeing only the diff, the linked issue or RFC, and PRINCIPLES.md
(RUNBOOK §4). All four must approve the current head. A block backed by a reproduction stands
until the code changes; any other block gets one rebuttal round, then a fifth reviewer decides.

## Required checks

On `develop`, sourced from GitHub Actions only:

- `onion/test` (the full suite, English locale)
- `onion/test-ja` (the full suite, Japanese locale)
- `dogfood`
- `evolution/risk-gate`
- `evolution/veto-window`

Removing one, or skipping a spec, is `risk:ultrahigh`.
