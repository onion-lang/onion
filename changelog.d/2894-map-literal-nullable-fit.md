### Fixed

- **`val m: Map[String, Int?] = ["a": ident(5), "b": null]` compiles.**
  - Before: E0000, `Map[String, Int]` is used where `Map[String, Int?]` is expected.
  - Now: prints `{a=5, b=null}`, as the list form `[ident(5), null]` already did.
