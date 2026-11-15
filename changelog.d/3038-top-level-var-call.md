### Fixed

- **`g()` works when `g` is a top-level `var` holding a function.**
  - Before: `var g: () -> Int = () -> 5; println(g())` compiled, then threw `IncompatibleClassChangeError`.
  - Now: it prints `5`, as it does for a `val`.
