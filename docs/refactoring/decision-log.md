# Maintainability decision log

## D001: current source and tests are authoritative

Decision: treat historical and proposed design files as evidence, not current
truth, when they contradict executable code.

Reason: several design documents mix proposed status with later shipped notes.
Following their headers literally would recreate already-completed work or
describe files that no longer exist.

## D002: use repeatable measurements, not file size alone

Decision: rank candidates with separate LOC, branch proxy, package dependency,
commit, churn, and co-change facts from the last 200 commits touching current
main-source paths.

Reason: `TypedAST.scala` is larger than most sources but stable and definition
heavy. `Parsing.scala` is smaller but changed in 36 commits and mixes unrelated
policy with parser lifecycle.

Consequence: the audit score is advisory and its raw dimensions remain visible.

## D003: extract parser hint policy first

Decision: the first production slice is a pure syntax-hint classifier extracted
from `Parsing.scala`.

Reason:

- highest recent edit frequency among compiler hotspots;
- classification is deterministic and has no need for reader/parser state;
- extensive existing end-to-end and i18n coverage;
- exact rollback is one source move and one call-site change;
- no grammar, AST, typed semantics, bytecode, or public API change.

Rejected first slices:

- splitting `TypedAST.scala`, because low churn does not show active pain;
- splitting `Rewriting.scala` immediately, because its transformations have
  wider semantic co-change and need stronger AST characterization first;
- splitting ASM first, because parser policy offers a lower-risk proof of the
  process.

## D004: classifier returns message identity, not localized text

Decision: `SyntaxHintClassifier` returns a message key and literal arguments.
`Parsing` renders them with `Message`.

Reason: classification stays locale-independent and directly testable while
the parser adapter preserves the exact existing localization boundary.

Invariant: message key, argument order, match priority, and final English and
Japanese text do not change.

## D005: preserve ordered matching visibly

Decision: keep classification as one ordered decision chain in the first
extraction. Do not prematurely split hint families into independent registries.

Reason: several cases intentionally shadow broader cases. A registry or map
could hide or accidentally reorder those precedence rules.

## D006: use the JVM sbt runner on this WSL host

Decision: run `sbt --server` with a writable temporary `XDG_RUNTIME_DIR` for
local verification.

Reason: the sbt 2 native thin client cannot connect in this WSL session, while
the JVM runner executes the same project sbt version successfully.

Consequence: command reports disclose the launcher deviation. No repository
configuration is changed to accommodate one host.

## D007: remove the tracked editor backup in Phase 0

Decision: delete `project/.build.properties.un~` and ignore `*.un~`.

Reason: it is editor debris containing obsolete build-property edit history and
is not referenced by the build. This is a hygiene-only commit before compiler
changes.
