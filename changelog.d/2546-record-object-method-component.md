### Breaking

- **`record R(hashCode: Long)` and `record R(toString: Int)` are E0010, as `hashCode: Int` already was.**
  - Before: they compiled, and `r.hashCode()` and `(r as Object).hashCode()` gave different answers.
  - Now: rename the component. See `docs/migration/pre-1.0.md`.
