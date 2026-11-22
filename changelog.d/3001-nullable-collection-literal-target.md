### Fixed

- **A list or map literal takes its element types from a nullable target (#3001).**
  - Before: `val a: List[Int?]? = [1]` was E0000, and `val b: List[Int]? = [1, null]` compiled.
  - Now: the first compiles; the second is E0000 at the `null`, as it is for `List[Int]`.
