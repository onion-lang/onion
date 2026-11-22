### Fixed

- **A `void` `@TailRecursive` mutual group is stack-safe.**
  - Before: `a(n - 1)` as a bare statement in tail position was not seen as a tail call, so a deep group overflowed the stack with no W0016.
  - Now: it runs as a loop, like the `return b(..)` form.
