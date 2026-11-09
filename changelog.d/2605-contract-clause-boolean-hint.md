### Fixed

- **A contract clause that is not a condition says so (#2605).**
  - Before: `requires x` with `x: Int` gave E0000 "... Consider a cast or check return type."
  - Now: still E0000, with a hint that `requires` takes a Boolean condition, and an example (`requires n > 0`). The same for `ensures`, `invariant` and `history`, in en and ja.
