### Fixed

- **A cast from a nullable value is checked like the non-null form (#2388).**
  - Before: `(s as Integer)` with `s: String?` compiled, then threw `ClassCastException`.
  - Now: E0000, with no `!!` hint (`!!` would not help). `null as Int?` and other valid casts still compile.
