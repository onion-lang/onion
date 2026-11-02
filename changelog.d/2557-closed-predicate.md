### Added

- **A closed predicate runs when the build runs; a false one is E0113.**
  - A no-parameter `static def` returning `Boolean` with an `ensures` clause, in a class, record or at the top level.
  - The replacement for `example`. Needs `--contracts check`.
