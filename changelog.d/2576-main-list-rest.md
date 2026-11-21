### Added

- **A trailing `List[String]` on an auto-CLI `main` collects the rest of the arguments (#2576).**
  - Before: `def main(cmd: String, files: List[String])` was E0094; only `String[]` worked.
  - Now: it binds the remaining arguments as a list, like `String[]`. A defaulted `List[String]` is still E0094.
