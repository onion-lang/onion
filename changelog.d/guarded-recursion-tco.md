### Fixed

- **A self-recursive method with `ensures` keeps tail-call optimization under `--contracts runtime`.**
  - Before: the guard made each self-call a non-tail call; deep recursion overflowed the stack.
  - Now: only the entry is guarded. The `requires` of an inner self-call is not checked at run time.
