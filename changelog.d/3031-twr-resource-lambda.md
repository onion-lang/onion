### Fixed

- **A lambda inside `try (val r = ...) { }` can read `r` (#3031).**
  - Before: `try (val w = ...) { rows.forEach { x -> w.write(x) } }` compiled, then failed with `VerifyError` before the program started.
  - Now: it runs, and the resource is still closed at the end of the block.
