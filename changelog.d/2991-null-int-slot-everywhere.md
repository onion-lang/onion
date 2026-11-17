### Breaking

- **`null` into an `Int` type argument is E0000 in a constructor and an indexed assignment, not only `xs.add(null)` (#2991).**
  - Before: `new Box[Int](null)`, `new R[Int](null)`, `xs[0] = null` and
    `m["b"] = null` compiled (W0012 or nothing); the stored `null` crashed when read as an `Int`.
  - Now: E0000 with the hint to write `Int?`, as for `xs.add(null)`.
  - To keep storing `null`, declare the type argument nullable: `Box[Int?]`, `List[Int?]`.
