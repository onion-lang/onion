### Added

- **An `invariant` or `history` on a mutable object is E0110.**
  - `invariant items != null` on a `List` field is rejected; `Int`, `String`, enum and record fields are fine.
