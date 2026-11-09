### Breaking

- **A record component named `hashCode`, `toString` or `getClass` is E0010, whatever its type.**
  - Before: `record R(hashCode: Long)` compiled, and `r.hashCode()` and `(r as Object).hashCode()` gave different answers.
  - Now: rename the component. See `docs/migration/pre-1.0.md`.
