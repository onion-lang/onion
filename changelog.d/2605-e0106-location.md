### Fixed

- **E0106 (a contract that is not pure) points at the call that has the effect.**
  - Before: `def f(xs: List[Int]): Int ensures xs.add(1)` reported `1:1`, under `def`; an `invariant` or `history` pointed at its `class` line or at an `&&`.
  - Now: `1:38`, under `add(1)`. The same for `requires`, `invariant` and `history`.
