### Fixed

- **A `@TailRecursive` group call through another object keeps that object.**
  - Before: `return mk().b(n - 1)` became a jump inside the group, so `mk()` never ran and a `null` receiver did not throw.
  - Now: only a call on `this` (or a static call) becomes a jump; any other call runs as written.
