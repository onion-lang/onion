### Fixed

- **`g()` works when `g` is a top-level `var` holding a function.**
  - Before: `var g: () -> Int = () -> 5; println(g())` compiled, then threw `IncompatibleClassChangeError`.
  - Now: it prints `5`, as it does for a `val`.
- **`f()` works inside a lambda when `f` is a field holding a function.**
  - Before: `val l: () -> Int = () -> inf()` in a method compiled, then threw `VerifyError`.
  - Now: it calls the field's function, as `inf.call()` already did.
