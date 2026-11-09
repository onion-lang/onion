### Fixed

- **A record method named `toString`, `hashCode` or `equals` with other parameters no longer removes the generated one.**
  - Before: `def toString(n: Int): Int` made `println(r)` print `R@80`.
  - Now: only the `Object` signature (`toString()`, `hashCode()`, `equals(Object)`) replaces the generated method; an overload, or a `static`/`private` method of that name, leaves it in place.
