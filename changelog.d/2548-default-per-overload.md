### Fixed

- **A subclass method with the same name no longer takes over an inherited default (#2548).**
  - Before (v0.150.0): `sh.scale(5)` printed `500`, the subclass's default, and an overload whose default had another type was `E0104` naming `f$default$2`.
  - Now: `5`, and the overload compiles. Through a parent type, `a.f()` uses the parent's default again, as in v0.149.0.
- **A default argument is named by its method in runtime errors, `--effects` and `E0077`.**
  - Before: `called from a.on:5 (f$default$1)`, `--effects` listed `A#f$default$1()`, and E0077 said `(calling stamp$default$1)`.
  - Now: `called from a.on:5 (f)`, E0077 says `(calling stamp)`, and `--effects` lists no default; its effects go to each caller that omits it.
