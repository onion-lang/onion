### Breaking

- **An `if`/`else` of `Option[String]` and `Option[Int]` is no longer an `Option[Int]`.**
  - Before: passed as a call argument, returned from a lambda for a non-generic parameter, or kept in an untyped `val`, it compiled as `Option[Int]` and threw `ClassCastException` on read.
  - Now: E0000 at the branch that does not fit. With no target the join is `Option[?]`, which fits `Object` and nothing narrower. See `docs/migration/pre-1.0.md` (#3255, #3247).
