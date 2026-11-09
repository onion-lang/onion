### Fixed

- **A record method named `toString`, `hashCode` or `equals` with other parameters no longer removes the generated one.**
  - Before: `def toString(n: Int): Int` made `println(r)` print `R@80`.
  - Now: only a method with the `Object` signature replaces it (`equals(o: Object?)`, and `equals(o: T)` with a type parameter `T`, count); an overload leaves it in place.
  - `copy` is unchanged: any body method named `copy` still replaces the generated one.
