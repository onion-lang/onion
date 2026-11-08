### Fixed

- **A record method like `def toString(): Int` is E0104 (#2713).**
  - Before: it compiled, and the generated `toString()` was lost (`println(r)` printed `R@80`). `hashCode(): Long` gave two answers.
  - Now: E0104 at the method, the same error a class gets.
