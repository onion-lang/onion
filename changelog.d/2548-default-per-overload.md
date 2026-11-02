### Fixed

- **A subclass method with the same name no longer takes over an inherited default (#2548).**
  - Before (v0.150.0): `sh.scale(5)` printed `500`, the subclass's default, and an overload whose default had another type was `E0104` naming `f$default$2`.
  - Now: `5`, and the overload compiles. Through a parent type, `a.f()` uses the parent's default again, as in v0.149.0.
