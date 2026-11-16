### Fixed

- **`mk().copy(c = "C")` runs `mk()` once, so the copy keeps one object's fields.**
  - Before: the receiver ran again for each omitted component (`R(a=a2, b=b3, c=C)` after three calls).
  - Now: it runs once (`R(a=a1, b=b1, c=C)`), for `?.copy(..)` and `copy()` too.
