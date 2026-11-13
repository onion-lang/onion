### Fixed

- **`Proc::run(cmd)` with `cmd: List[String]` now says how to fix it (#2773).**
  - Before: E0005 listed `run(String[])` and gave no way to get an array.
  - Now: the error adds a hint to pass `cmd.toArray(new String[0])`.
