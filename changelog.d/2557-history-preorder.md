### Added

- **A `history` that is not a preorder is E0108.**
  - `history n == old(n) + 1` or `n > old(n)` is rejected; `n >= old(n)` is accepted.
  - Needs `--contracts check`.
