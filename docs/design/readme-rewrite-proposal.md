# README rewrite — proposal

Status: **proposal.** Nothing in `README.md` has been changed. This is the concrete
version of [#354](https://github.com/onion-lang/onion/issues/354), split out so the
wording can be argued about before it lands.

## The problem, precisely

`README.md` currently sells two different languages.

**Lines 1–4** — the first thing anyone reads:

> Onion - A Statically Typed Programming Language on JVM
> Onion is an object-oriented and statically typed programming language. Source codes of
> Onion compiles into JVM class files as in-memory or real files.

Every property claimed there — static typing, object orientation, JVM bytecode — is
shared with Kotlin, Scala, Java, Groovy and Ceylon. It is a *category*, not a reason to
use Onion. Lines 5–7 then spend the next paragraph on rewrite history (Java → Scala,
JavaCC), which is interesting to a contributor and irrelevant to a reader deciding
whether to care.

**Lines 171–205** — "Shape-First Scripting", buried as the sixth subsection of "Language
Snapshot":

> Onion collapses the boilerplate layers a script usually needs.

*That* is the actual thesis, and it is 171 lines down.

Three further inconsistencies, from [audit §8](onion-v0.5-audit.md):

- `grep -nE "trait|instance|type class" README.md` → **0 matches**, although type classes
  are implemented and shipped (`docs/design/type-classes.md:1`, "implemented (v1)") and
  documented in `docs/guide/type-classes.md` in both languages. A headline feature is
  absent from the front door.
- `docs/index.md` lists 13 key features; `docs/ja/index.md` lists 11. The two missing from
  Japanese are **"Bidirectional Records"** and **"Compile-Time Specs"** — the two most
  distinctive items in the list.
- `CHANGELOG.md` contains no positioning statement, so there is no third source to
  arbitrate the README's self-contradiction.

## Proposed opening

Replacing lines 1–7:

```markdown
## Onion — typed tools for a messy world

Onion is a statically typed language for turning messy external data and operations
into checked, reversible tools. It runs on the JVM and calls Java directly.

Most languages hand you a `String` at the boundary and wish you luck. Onion asks you
to describe the boundary once — and derives the parser, the printer, the failure
channel and the command-line interface from that one description.

```onion
record Access(time: String, method: String, path: String, status: Int)
  from re"(\S+) (\w+) (\S+) (\d+)"
  law roundtrip(a: Access) { Access::parse(Access::format(a)) == a }

def main(path: String, min: Int = 400): void {
  file(path).lines()
    .map { line -> Access::parse(line) }
    .filter { a -> a != null && a.status() >= min }
    |> println
}
```

One declaration gives you `parse`, `format`, a `law` the **compiler checks at build
time**, and a CLI with `--min` and `--help` derived from `main`'s signature.
```

Notes on the sample: it is deliberately written in **current** v0.5 syntax, not the
proposed `shape` syntax, so the README stays true while v0.6 is in progress. When
[#350](https://github.com/onion-lang/onion/issues/350) lands, this block changes with it.

The rewrite history (current lines 5–7) moves to `## Architecture` (line 207), where it
belongs.

## Proposed structure

Current order buries the differentiators under the generic features:

```
Language Snapshot
  Null Safety            ← Kotlin has this
  Records, Enums, …      ← Java/Scala have this
  Do Notation            ← Scala/Haskell have this
  Trailing Lambda        ← Kotlin has this
  Async with Future      ← everyone has this
  Shape-First Scripting  ← ONLY Onion has this
```

Proposed: lead with what is unique, then the table stakes.

```
Language Snapshot
  Boundaries           bidirectional records, scheme literals, |>, auto-CLI
  Checked at Build     law / example, exhaustiveness, null safety
  Types                records, enums, generics, type classes   ← currently missing entirely
  Everyday             do notation, trailing lambdas, Future
```

A reader who stops after the first section should already know what Onion is for.

## Companion changes

Same issue, since they are the same inconsistency:

- add the two missing bullets to `docs/ja/index.md` so EN/JA parity is real (13 vs 11);
- link `guide/type-classes.md` from both guide indexes — the file exists in
  `docs/guide/` and `docs/ja/guide/` and is in `mkdocs.yml`, but neither index links it;
- link `docs/design/` from `docs/index.md`, which today links three design-ish documents
  that live *outside* that directory (`GENERICS_DESIGN.md`, `parser-refactoring.md`,
  `quality-bar.md`) and does not link `docs/design/type-classes.md` at all;
- refresh the `docs/quality-bar.md` baseline: it records "1193 pass / 0 fail" and
  "36 / 36 compile" as of 2026-06-26, against an actual ~2,450 tests and 59 files in `run/`.

## What is deliberately not proposed

**Deleting "statically typed" or "JVM".** They are true, they are what a reader filters
on, and they belong in the second sentence. The objection is to leading with them.

**Rewriting the README around v0.6 syntax now.** The README must describe the language
that exists. The positioning changes now because it is wrong now; the code samples change
when the code does.
