### Fixed

- **`old(...)` inside a lambda of a `history` or `ensures` is E0107 (#2732).**
  - Before: it compiled, then `--contracts runtime` crashed with `NoSuchFieldError`.
  - Now: the compiler rejects it. Compute the value outside the lambda.
