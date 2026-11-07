### Fixed

- **A primitive in a `T` slot is a compile error, not a ClassCastException later (#2534, #2652).**
  - Before: `def zero(): T = 0`, `t = 5` and a default `def id(n: T = 3)` compiled; a caller with `T = String` then failed at run time on its own line.
  - Now: each is E0000 `type T is expected, but type Int is used`, as a `String` there already was. `new Box[JInteger](3)` and `val o: Object = 5` still compile.
