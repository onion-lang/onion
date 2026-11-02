### Fixed

- **The history solver no longer "proves" two methods whose run breaks the history.**
  - `def set(n: Int) { this.n = n }`: the parameter `n` was read as the field, so the write looked like a no-op.
  - `if k > 0 { n = n - k; return }` followed by `n = n + k`: the path through the `return` was not seen.
  - Before: `--verbose` printed `[history] proved`, and `--contracts runtime` dropped the guard.
  - Now: neither is proved. The `return` method gets W0024, and `--contracts runtime` throws `ContractViolation`.
  - Not yet: the setter gets no warning (W0024 comes with #2599), and the runtime guard still misses it (#2633).
