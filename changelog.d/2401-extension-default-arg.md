### Fixed

- **An extension method's default argument works: `"ab".pad()` compiles (#2401).**
  - Before: `E0005`, "method not found", for a method you can see.
  - Now: the default fills in, for `.` and `?.` calls. An overload that takes exactly the written arguments still wins, and a default that uses `self` or another parameter is a compile error (E0050/E0002).
