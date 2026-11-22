### Breaking

- **`old(...)` inside a lambda of an `ensures` or a `history` is E0107 (#2732).**
  - Before: it compiled and ran under the default `--contracts check`, and `--contracts runtime` crashed with `NoSuchFieldError`.
  - Now: E0107, with a hint. Pass the value in as an argument: `((o: Int) -> n >= o).call(old(n))`.
