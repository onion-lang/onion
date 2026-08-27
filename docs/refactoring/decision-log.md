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

## D008: keep refactoring work records outside the public manual

Decision: list `docs/refactoring/` under MkDocs `exclude_docs` instead of adding
the six planning and evidence files to the public navigation.

Reason: these files record maintainer-facing measurements, sequencing, and
temporary implementation status. The existing architecture and contributor
documentation remains the stable public entry point, while the MkDocs coverage
gate still verifies that the exclusion is intentional.

## D009: represent parser hint source input as one pure value

Decision: `parser.SourceContext.at` converts a 1-based parser position into the
bounded context starting at the reported column and the complete reported
source line. `Parsing` calculates this value once for each collected or
terminal parse error.

Reason: both parse-error paths previously repeated the same private offset and
substring work. The calculation has no dependency on JavaCC state,
localization, or diagnostic rendering, so a parser-local pure helper gives it a
direct test seam without widening a compiler API.

Invariant: coordinates stay 1-based, context remains capped at 200 characters
and may cross line boundaries, the complete source line is not truncated, and
a line beyond the source produces empty values.

## D010: keep terminal and recovery expected-token formatters separate

Decision: `parser.ExpectedTokenFormatter` renders JavaCC expected-token
metadata for terminal `ParseException` diagnostics. The generated grammar's
`expectedSummary` method continues to render recovery-collected diagnostics.

Reason: terminal rendering is deterministic and directly testable without
parser or localization state. The recovery formatter runs inside generated
JavaCC code and has different existing punctuation and truncation behavior;
unifying the two would cross the generation boundary and change diagnostics,
which is outside this refactoring slice.

Invariant: terminal formatting keeps the null/empty fallback, considers only
the first token of each sequence, deduplicates in encounter order, joins one to
three tokens with the existing separators, and preserves the four-token
truncation boundary exactly, including the existing `... (0 more)` result.

## D011: begin hint grouping with one contiguous control-flow family

Decision: move the seven contiguous unsupported control-flow rules for
`switch`, `when`, `match`, `default`, `elif`, `unless`, and `except` into the
package-private pure `parser.ControlFlowSyntaxHints` object. Keep its single
delegation point in the exact former position of the global classifier chain.

Reason: after the original classifier extraction, the file reached 240 LOC,
113 branch proxies, 16 commits, and 242 lines of churn in the audit window;
most of that activity was new hint work. These seven rules form a closed,
contiguous priority interval and share the same unsupported-control-flow
responsibility. Extracting them localizes future changes without introducing a
registry, one-rule files, or a speculative rule interface.

Invariant: the seven internal rules retain their order, the family remains
after the removed `for ... in` cases and before declaration rules, and it stays
ahead of the generic missing-block fallback. Message keys, arguments, regular
expressions, localization, source locations, and no-match behavior do not
change.
