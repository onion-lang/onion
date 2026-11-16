### Fixed

- **A class-only file and a script with the same file name (`e1/y.on`, `e2/y.on`) compile together.**
  - Before: a false W0014, then `[I0000] Internal compiler error ... Duplicate method name "start"`.
  - Now: both compile; the class-only file no longer takes the script's `yMain` as its own.
- **E0008 for two same-named scripts names both paths** (`d2/x.on and of d1/x.on`, was `x.on`).
