### Fixed

- **A parameter default is checked against the parameter's type, like `val x: T = e` (#2541).**
  - Before: `b: Byte = 200` ran as `-56` on `main`/`tool`, and crashed at load on other functions. `s: Short = 3.7` and `l: Long = 7` threw `VerifyError`.
  - Now: an out-of-range or wrong-typed default is E0000 at the default; `l: Long = 7` and `d: Double = 1.0f` run.
