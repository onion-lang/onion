### Fixed

- **An untyped lambda passed to overloaded class methods prefers a function type over a SAM interface, as extension methods do.**
  - Before: `new K().outer { p -> ... }` with `outer((Int) -> Int)` beside `outer(Function[String, String])` was E0006.
  - Now: the function-type overload is chosen, for instance and static methods.
