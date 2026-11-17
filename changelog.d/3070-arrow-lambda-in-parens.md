### Fixed

- **`f({ -> "d" })` compiles: `{ -> body }` is a zero-parameter lambda wherever a value goes (#3070).**
  - Before: a syntax error at `->` inside call parentheses or an initializer; only the trailing form `f { -> "d" }` worked.
  - Now: `opt.fold({ -> "none" }, { s -> s })` and `val h: () -> Int = { -> 7 }` compile.
