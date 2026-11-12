### Fixed

- **`--verbose` no longer says an `ensures` is proved when the proof needs a class `invariant` (#2672).**
  - Before: `[ensures] proved: Counter.next` for `ensures result > 0` over `n + 1`, then the run printed `-2147483648`.
  - Now: no "proved" line, since a constructor can break an unchecked `invariant`. A proof that needs no invariant is still proved.
