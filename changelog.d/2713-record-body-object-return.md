### Fixed

- **A record method like `def toString(): Int` is E0104 (#2713).**
  - Before: it compiled, and the generated `toString()` was lost (`println(r)` printed `R@80`). `hashCode(): Long` gave two answers.
  - Now: E0104 at the method, the same error a class gets.

### Breaking

- **A record method `def toString(): String?` is E0000, as in a class (#2713).**
  - Before: it compiled, and a `null` result reached `println`.
  - Now: return a `String`, e.g. `name ?: ""`. See `docs/migration/pre-1.0.md`.
