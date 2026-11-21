### Added

- **A trailing `List[String]` on an auto-CLI `main` collects the rest of the arguments (#2576).**
  - Before: `def main(cmd: String, files: List[String])` was E0094; only `String[]` worked.
  - Now: it binds the remaining arguments as a list, like `String[]`; `def main(files: List[String])` alone takes every argument. A misplaced or defaulted `List[String]` is E0095, as `String[]` is.
