# Onion Design Principles

Onion is designed and grown by the evolution loop, not by a human design committee. The goal
is a language **everyone can use**, with no human developer as the bottleneck. These
principles stand in for the taste, memory and restraint a human designer would bring. Every
RFC and every review-panel verdict (RUNBOOK §4) cites the principles it relies on; when two
good options conflict, the principles break the tie.

Changing this file is `risk:ultrahigh` (GUARDRAILS.md): the loop does not rewrite its own
constitution without a 48-hour window in which Kota can veto.

Each principle names the history it comes from. These are lessons other languages paid for.

1. **Evidence over taste.** Decide from real programs: dogfood projects, probe scripts,
   `source:user` issues, the corpus. A change that no program needs is not made. (Every
   successful language grew from users' pain; Kotlin was built from Java's.)

2. **One obvious way.** When two spellings do the same thing, keep one and point the other at
   it with a hint, or remove it. (Python's "there should be one obvious way"; PHP still
   carries its inconsistent standard-library names because it never cleaned them up.)

3. **Break only before 1.0, and always ship the migration.** Before 1.0, breaking changes
   that make the language simpler or more consistent are allowed and encouraged. Each one
   comes with a `### Breaking` CHANGELOG entry, a migration note, a hint diagnostic from the
   old spelling, and an `onion migrate` rewrite rule. The consolidation phase is time-boxed.
   (Python 3 broke *after* adoption and took over a decade to migrate; Swift 3 broke *before*
   its stability promise; `go fix` and `cargo fix` shipped the migrator with the change; Perl 6
   redesigned for fifteen years and lost its momentum.)

4. **After 1.0, keep the promise.** A program that compiles on 1.x keeps compiling and means
   the same thing on later 1.x releases. Later breaking evolution goes through editions that
   a project opts into. (Go 1's compatibility promise; Rust's editions.)

5. **Test against the world before changing it.** A language change runs against the whole
   corpus (`run/`, `dogfood/`, every docs sample) before it merges. (Rust's crater runs
   every published crate before a change.)

6. **Stage new syntax.** New syntax lands behind a preview flag, and graduates only when the
   corpus and dogfood use it. What graduates is permanent. (TC39's stages 0–4 and "don't
   break the web"; Java's preview features.)

7. **Errors are user interface.** Every diagnostic has a code, says what to do, and
   recognises what a newcomer from another language would have written. (Elm and Rust made
   error messages a reason to choose the language.)

8. **The tools are part of the language.** The formatter, the LSP, the package manager, the
   test runner and the docs get the same care as the compiler. (`gofmt`, `go test`, Cargo.)

9. **Every feature has a complexity budget.** A feature must remove more pain than the
   complexity it adds. Before adding, look for something to remove. (C++ and Scala grew by
   accretion until mastery became a career; Scala 3 had to replace implicits.)

10. **Write every decision down.** An RFC records the problem, the options, the choice, and
    why each rejected option lost. No single agent or person is the gatekeeper; the
    principles are. (Python's PEPs, Rust's RFCs; Elm stalled on a single gatekeeper, and C++
    committees decide by compromise.)

11. **People are the first users, not gatekeepers.** An issue filed by a person using Onion
    for real work gets the fastest response and the highest priority after P0.

12. **Trust is earned by steadiness.** Releases on a predictable cadence, docs that match the
    release, a learning path for newcomers, a clear license. (Rust's six-week trains.)
