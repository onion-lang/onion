### Fixed

- **The "pass the elements as `list.toArray(...)`" hint now compiles for a nullable List (#2903).**
  - Before: for `Proc::run(cmd)` with `cmd: List[String]?`, following the hint gave E0070.
  - Now: the hint reads `list!!.toArray(new String[0])`.
