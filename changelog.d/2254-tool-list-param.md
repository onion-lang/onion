### Fixed

- **A `tool` parameter of type `List[String]` or `String[]` now says a tool cannot take a list (#2254).**
  - Before: E0081 listed the scalar types and left you to guess.
  - Now: E0081 says a list is not supported yet and shows the workaround, one flag split with `Strings::split` (`--csv a,b`).
