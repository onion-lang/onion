### Fixed

- **An extension method's default argument works: `"ab".pad()` compiles (#2401).**
  - Before: `E0005`, "method not found", for a method you can see.
  - Now: the default fills in, for `.` and `?.` calls. An overload that takes exactly the written arguments still wins. Like any default (#2375), it can use `self` and the parameters before it.
