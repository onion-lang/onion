### Fixed

- **A subclass method with the same name no longer takes over an inherited default (#2548).**
  - Before (v0.150.0): `sh.scale(5)` printed `500`, the subclass's default, and an overload whose default had another type was `E0104` naming `f$default$2`.
  - Now: `5`, and the overload compiles. Through a parent type, `a.f()` uses the parent's default again, as in v0.149.0.
- **A runtime error inside a default argument names the method, not a synthesized one.**
  - Before: `called from a.on:5 (f$default$1)`, and `--effects` listed `A#f$default$1()`.
  - Now: `called from a.on:5 (f)`, and `--effects` lists no default; its effects go to each caller that omits it.
