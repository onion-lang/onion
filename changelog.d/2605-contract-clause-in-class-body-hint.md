### Fixed

- **`requires`/`ensures` written in a class body now get a hint (#2605).**
  - Before: a bare syntax error, `Encountered "requires", but expecting "val", "var", ...`.
  - Now: the hint says the clause belongs after a method's signature (`def inc(): void requires n >= 0 { ... }`), or, for a condition on the fields, to write `invariant n >= 0`.
