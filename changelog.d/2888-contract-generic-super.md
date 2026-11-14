### Fixed

- **A class with a `history` or `invariant` can extend a generic Onion class (#2888).**
  - Before: `class Up extends Base[String] { history n >= old(n) }` was E0111, "extends the Java class `Base`".
  - Now: it compiles, and `Base`'s inherited methods are checked against the history (E0109) as for a non-generic `Base`.
- **Contract checks no longer skip a generic supertype (#2888).** An override that breaks a generic base's `ensures` is W0025, and a `history` on a generic interface (`conforms Counter[String]`) is checked (W0024), as without type arguments.
