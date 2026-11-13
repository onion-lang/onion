### Fixed

- **A same-name overload no longer hides an inherited method from a subclass `history` (#2786).**
  - Before: with `def dec(k: Long)` in `Up`, the inherited `Base.dec(k: Int)` lowered `n` under `history n >= old(n)` and nothing was reported.
  - Now: E0109 at `Base.dec`'s write. Only a method with the same parameter types counts as an override, as E0068 already decides.
