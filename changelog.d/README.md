# Changelog fragments

A PR with a user-visible change adds **one new file here** instead of editing `CHANGELOG.md`.
Two PRs then never touch the same lines, so a merge never makes the others conflict (#2374).

## Write one

Name it `<issue>-<slug>.md`, for example `changelog.d/2425-int-literal-overload.md`.
Without an issue, use the PR's slug alone.

Inside, one or more `### Section` headings, each followed by entries written as
[CLAUDE.md](../CLAUDE.md#writing-for-humans-changelog-docs-prs-comments) describes:

```markdown
### Fixed

- **`System::out.println(75)` prints `75` (#2425).**
  - Before: the `Int` literal was narrowed to `Char`, so the output was `K`.
  - Now: the `int` overload is chosen, as in Java.
```

Sections: `Breaking`, `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`,
`Documentation`. A breaking change needs a `### Breaking` entry (RUNBOOK §5.3).

Check your file:

```bash
python3 scripts/changelog/changelog.py check     # validates every fragment
python3 scripts/changelog/changelog.py preview   # prints what the next release will say
```

## At a release

The changelog-cut PR runs, after the release tag exists:

```bash
python3 scripts/changelog/changelog.py cut 0.147.0
```

It moves every fragment, and anything still written directly under `## [Unreleased]`, under
`## [0.147.0] - <today>`, newest issue first, and deletes the fragments. It prints each file it
consumed; list them in the cut PR. This README is never consumed.
