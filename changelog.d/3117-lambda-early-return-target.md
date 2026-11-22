### Fixed

- **An early `return` in a lambda passed to a generic method is checked against the target type.**
  - Before: `val c: Option[Int] = ch2(() -> { if b { return Option::some("str") }; Option::none() })` compiled, then threw ClassCastException where `c` was read.
  - Now: E0000 at the `return`, as when the lambda is assigned to `() -> Option[Int]` directly.
