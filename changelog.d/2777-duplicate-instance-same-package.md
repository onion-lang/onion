### Fixed

- **A duplicate `instance` in two files of one package is E0097.**
  - Before: E0008 `duplicated class definition Show$$Integer`, a name you never wrote.
  - Now: E0097 `instance Show[Integer] is already defined`, as across modules.
