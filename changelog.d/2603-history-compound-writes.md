### Fixed

- **A `history` now sees `*=`, `/=`, `%=` and the bitwise compound assignments (#2603).**
  - Before: under `history n >= old(n)`, `def neg(): void { n *= -1 }` compiled with no diagnostic, and the run took `n` from 10 to -10.
  - Now: `n *= -1` is E0112 (the run reproduces the break), and `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`, `>>=`, `>>>=` get the warning W0024, since the solver does not model them.
