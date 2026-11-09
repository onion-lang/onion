### Fixed

- **`requires x > 0` inside a method body now says where the clause goes (#2605).**
  - Before: the hint said to write `requires(...)` as a call, which sends you the wrong way.
  - Now: the hint says `requires`/`ensures` follow the signature, before the body: `def half(x: Int): Int requires x > 0 { ... }`.
