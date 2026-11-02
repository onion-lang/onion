### Fixed

- **The history solver no longer "proves" two methods whose run breaks the history.**
  - `def set(n: Int) { this.n = n }`: the parameter `n` was read as the field, so the write looked like a no-op.
  - `if k > 0 { n = n - k; return }` followed by `n = n + k`: the path through the `return` was not seen.
  - Before: `--verbose` printed `[history] proved`, and `--contracts runtime` dropped the guard. Now: no proof, the guard stays, and W0024 says the history was not checked.
