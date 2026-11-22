### Fixed

- **A script whose container name equals a class in another file gets one E0008 naming both files.**
  - Before: `j1/y.on` (`class yMain`) plus `e2/y.on` (top-level code) printed an E0008 naming only `y.on`, then an E0035 about `yMain.start`.
  - Now: one E0008 saying `e2/y.on` would become `yMain` and `j1/y.on` declares it; the E0035 is gone.
