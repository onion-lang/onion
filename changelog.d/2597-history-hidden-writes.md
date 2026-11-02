### Fixed

- **A write that breaks a `history` is caught inside a lambda (#2597).**
  - Before: `() -> { t = t - 1 }` against `history t >= old(t)` compiled with no error and no warning.
  - Now: E0109, as for `t = t - 1` in the method body. A lambda's write the checker cannot settle (`t = t + 1`, `t++`) gets W0024 at its line, so a `--warn error` build stops there.

### Breaking

- **A field an `invariant` or `history` names is written only through `this` (E0110, #2599).**
  - Before: `o.t = o.t - 1` (with `o: Clock`), `cs[0].t = 7` or a factory's `val c = new Clock(); c.t = 5` compiled silently.
  - Now: E0110 at `o`. Call a method of that object, or pass the value to a constructor. Contracts are a preview feature (RFC #2557); `docs/migration/pre-1.0.md` shows both rewrites.
