### Fixed

- **Reading a plain constructor parameter in a method now says to declare it `val` (#2515).**
  - Before: `class Lru(cap: Int)` with `cap` used in a method gave E0002 and `did you mean: e`.
  - Now: E0002 adds "`cap` is a constructor parameter, not a field. Declare it `val cap` or `var cap`."
