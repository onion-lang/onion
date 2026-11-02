### Breaking

- **`law` and `example` are removed; use a contract predicate.**
  - Before: `law name(p: T) { e }` and `example { e }` in a record body or at the top level.
  - Now: `static def name(p: T): Boolean ensures result = e` (top level: `def name(...)`). A failing one is E0113. See the migration guide.
  - Removed: the flag `--no-check-laws` (use `--contracts off`), and the codes E0064, E0065, E0074, E0075 and E0080 (numbers are not reused).
  - Renamed: `--law-seed` and `--law-samples` are `--contract-seed` and `--contract-samples`.
