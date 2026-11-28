### Fixed

- **A contract violation shows `this.n`, not `....n`.**
  - Before: `requires this.n > k` failed as `requires ....n > k`.
  - Now: the clause is printed as written.
