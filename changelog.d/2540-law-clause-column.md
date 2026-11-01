### Fixed

- **A failing `law` or `example` points at its own clause when many files compile together, or when several clauses share a line.**
  - Before: `A.on:3:3`, the column of an `example` in `B.on`; every clause on a one-line record body got the first clause's column.
  - Now: `A.on:3:15`, under the law's name; each clause on a line gets its own column.
