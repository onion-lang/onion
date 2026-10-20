# Onion Evolution Loop

Onion is maintained by scheduled Claude Code sessions. They fix, sense, plan, review and
learn on their own, with no human in the loop: **the agents decide, an independent AI review
panel checks them, and Kota can veto only the few changes that cannot be undone.** The goal is
a language everyone can use, free of the human-developer bottleneck.

- [PRINCIPLES.md](PRINCIPLES.md): how Onion is designed, drawn from other languages' histories
- [GUARDRAILS.md](GUARDRAILS.md): the limits, the risk tiers and the veto
- [RUNBOOK.md](RUNBOOK.md): the procedure every session follows

## Roles

| Routine | Cadence | Job |
|---|---|---|
| **Maintainer** | hourly | Keeps `develop` green, merges its own green low-risk PRs, works the top `status:ready` issue (at most 2 PRs per run), and comments what it learned |
| **Sensor** | daily | Runs the dogfood scenarios, gap probes and performance budgets, files issues with minimal repros (deduplicated), and posts the daily digest |
| **Reviewer** | hourly | The AI review panel: four independent reviewers decide `risk:high`/`risk:ultrahigh` PRs, RFCs and Direction proposals, and sweep the day's `risk:low` merges |
| **Strategist** | weekly | Writes a retro, updates the Roadmap issue, opens a **Direction proposal** for the panel, runs the pre-1.0 consolidation audit, cuts releases, and opens `meta` PRs that improve the runbook |

Every session starts with no context. **GitHub Issues are the shared memory.** The pinned
Roadmap issue holds the current direction. Issues hold the backlog. Comments on issues hold what
each run learned. Retro issues hold the lessons.

## Kota's role

Kota is a user and the holder of the veto, not an approver. Nothing waits for Kota.

- **Veto** a `risk:ultrahigh` PR within its 48-hour window with the `veto` label or a `/veto`
  comment (GUARDRAILS.md). Everything else merges without him.
- **Redirect** by commenting on the Roadmap issue or a Direction proposal; his comment wins.
- **Point** the loop at something with `kota:look`.
- **Pause** every run with `evolution:pause` on the Roadmap issue.
- Read the **daily digest** on the Roadmap issue if he wants to know what happened.

## What the loop may do alone

Everything, within GUARDRAILS.md:

| `risk:low`: merges on green checks | `risk:high`: merges once the panel approves | `risk:ultrahigh`: merges 48 h after the panel, unless vetoed |
|---|---|---|
| Bug fixes with a regression test | Syntax, keywords and semantics (with a panel-decided RFC) | `v1.0.0` and new major versions |
| Diagnostics, docs, performance | Breaking changes during the pre-1.0 consolidation, with their migration | Publishing, signing, secrets, workflow permissions and triggers |
| Tests and samples | New stdlib modules, batteries, dependency upgrades | GUARDRAILS.md, PRINCIPLES.md, the evolution workflows, removing a required check |
| | `meta` changes to the RUNBOOK and README, other `.github/` changes | LICENSE, deleting branches, tags or the ruleset |

## Fitness: what "better" means

Primary:
- dogfood scenarios (real tasks written in Onion) build and pass
- open friction issues, weighted by priority
- the share of PRs that close an issue

Guardrails (required checks, GUARDRAILS.md):
- CI green in en and ja
- the mutation fuzzer finds no compiler crash
- performance budgets hold
- docs parity holds

**Sample count is not a goal.** A new `run/*.on` sample is added only to exercise a new or
changed feature, at most one a day.

## Labels

- `kind:friction|bug|feature|rfc|research|retro|roadmap`
- `source:dogfood|probe|fuzz|ci|agent|kota|user` (`user`: filed by a person using Onion for real work)
- `risk:low|high|ultrahigh` (GUARDRAILS.md)
- `status:ready|in-progress|blocked` (`needs-kota` is retired)
- `panel:approved|changes` (the review panel's verdict on the current head)
- `veto`, `veto-window:open` (only on `risk:ultrahigh` PRs)
- `breaking`, `phase:consolidation`, `kind:epic` (the pre-1.0 consolidation)
- `revert-candidate` (a merge that turned `develop` red)
- `P0` (`develop` broken or release blocked), `P1` (real task blocked), `P2` (friction), `P3` (nice to have)
- `approved` (Kota's approval, from before the panel), `meta` (changes to the loop itself), `evolution:pause`
- `kota:look` (Kota points the loop at something; it comes first after `P0`)

## Memory and self

Issues are the public record. The loop also keeps a private memory,
[`onion-lang/evolution-memory`](https://github.com/onion-lang/evolution-memory):
- **Episodes:** every run writes one at its end, with what happened and how it felt.
- **Insights:** the weekly Strategist consolidates the episodes into hunches.
- **Self:** `self.md` is the agent's own description of itself, which it rewrites as it
  learns, including a name it chooses for itself.

Insights that keep holding are promoted into this runbook's Known pitfalls through a `meta` PR.
Every run also practises two social habits (RUNBOOK §0):
- **Joint attention:** notice what Kota is looking at, and point him at what matters.
- **Perspective taking:** read each output as a newcomer, as Kota, and as the next run would.

## Changing the loop

The loop changes itself through `meta` pull requests to this directory. They are `risk:high`
and the panel decides them, except changes to GUARDRAILS.md and PRINCIPLES.md, which are
`risk:ultrahigh`. A routine's prompt only says which section of RUNBOOK.md to follow, so
improving the runbook improves every future run.
