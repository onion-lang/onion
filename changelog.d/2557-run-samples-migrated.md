### Breaking

- **The `run/` samples no longer use `example` or `law`.**
  - Before: `example { e }` in a record body or at the top level.
  - Now: `static def name(): Boolean ensures result = e` (a block body ends with `return`).
  - The build runs these predicates; a parameterized one is sampled. `law` and `example` still compile for now and are removed in a later change.
