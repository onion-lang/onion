# Onion Evolution Loop

Onion is maintained by scheduled Claude Code sessions. They fix, sense, plan and learn on
their own. The maintainer (Kota) does not maintain the code day to day: **the agents propose
the direction and Kota approves or redirects it.** The procedure the sessions follow is in
[RUNBOOK.md](RUNBOOK.md).

## Roles

| Routine | Cadence | Job |
|---|---|---|
| **Maintainer** | hourly | Keeps `develop` green, merges its own green low-risk PRs, works the top `status:ready` issue (at most 2 PRs per run), and comments what it learned |
| **Sensor** | daily | Runs the dogfood scenarios, gap probes and performance budgets, files issues with minimal repros (deduplicated), and posts the daily digest |
| **Strategist** | weekly | Writes a retro, updates the Roadmap issue, opens a **Direction proposal** for Kota, and opens `meta` PRs that improve the runbook |

Every session starts with no context. **GitHub Issues are the shared memory.** The pinned
Roadmap issue holds the current direction. Issues hold the backlog. Comments on issues hold what
each run learned. Retro issues hold the lessons.

## Kota's role

- Read the **daily digest**, a comment on the pinned Roadmap issue that lists what merged, what
  was opened, and what needs Kota.
- Approve or redirect the weekly **Direction proposal** by adding the `approved` label or
  commenting. With no answer, the loop keeps executing the last approved direction and starts
  nothing new.
- Approve RFCs and the other `status:needs-kota` items.
- Add the `evolution:pause` label to the Roadmap issue to stop every run.

## What the loop may do alone

| Merges on green CI by itself (`risk:low`) | Waits for Kota (`risk:high`, `status:needs-kota`) |
|---|---|
| Bug fixes with a regression test | Language-surface changes: syntax, keywords, semantics visible to existing programs (needs an approved `kind:rfc` issue first) |
| Diagnostics, docs, performance | Breaking changes, new stdlib modules or batteries outside an approved roadmap item |
| stdlib additions inside an approved roadmap item | `.github/` workflows, release and publishing configuration |
| Tests | Major dependency upgrades, security-related changes, deleting or skipping tests |

## Fitness: what "better" means

Primary:
- dogfood scenarios (real tasks written in Onion) build and pass
- open friction issues, weighted by priority
- the share of PRs that close an issue

Guardrails, which already exist:
- CI green in en and ja
- the mutation fuzzer finds no compiler crash
- performance budgets hold
- docs parity holds

**Sample count is not a goal.** A new `run/*.on` sample is added only to exercise a new or
changed feature, at most one a day.

## Labels

- `kind:friction|bug|feature|rfc|research|retro|roadmap`
- `source:dogfood|probe|fuzz|ci|agent|kota|user` (`user`: filed by a person using Onion for real work)
- `risk:low|high`
- `status:ready|in-progress|blocked|needs-kota`
- `P0` (`develop` broken or release blocked), `P1` (real task blocked), `P2` (friction), `P3` (nice to have)
- `approved` (Kota's approval), `meta` (changes to the loop itself), `evolution:pause`
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

The loop changes itself through `meta` pull requests to this directory. They are `risk:high`,
so Kota approves them. A routine's prompt only says which section of RUNBOOK.md to follow, so
improving the runbook improves every future run.
