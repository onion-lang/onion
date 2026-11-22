### Fixed

- **An early `return` of the wrong type is caught when the lambda ends in a variable.**
  - Before: `val c: Option[Int] = ch2(() -> { if b { return Option::some("str") }; tail })` compiled, then threw ClassCastException where `c` was read.
  - Now: E0000 at the `return`, whatever the lambda's last expression is.
