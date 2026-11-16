### Fixed

- **The Japanese `Result` sample in `examples/functional.md` compiles (#2899).**
  - Before: E0000 and E0002; it used a `case Result::ok(value):` pattern that is not Onion syntax.
  - Now: it is the English program, and prints `Result: 10`.
