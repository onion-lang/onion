### Fixed

- **A `main` or `tool` default can use an earlier parameter: `def main(a: Int, b: Int = a * 2)` (#2575).**
  - Before: E0002 with a hint naming a hidden `args`, and `main(args: String, out: String = args + ".out")` silently read the script's `String[]`.
  - Now: `onion x.on 5` prints `15` and `--b 1` gives `6`, as on any other function.
