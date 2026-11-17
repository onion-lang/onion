### Fixed

- **`f(null)` picks `f(String?)` over `f(Int)` (#2876).**
  - Before: E0006, ambiguous between the two, though `new K(null)` already chose the `String?` constructor.
  - Now: `null` never fills an `Int` slot, so the `String?` overload wins, as in Java.
