### Fixed

- **A record method named `toString`, `hashCode`, `equals` or `copy` with other parameters no longer removes the generated one.**
  - Before: `def toString(n: Int): Int` made `println(r)` print `R@80`.
  - Now: only a method with the generated method's signature replaces it (`equals(o: Object?)` and `equals(o: T)` count); an overload leaves it in place.
