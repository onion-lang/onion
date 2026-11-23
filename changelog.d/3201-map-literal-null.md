### Breaking

- **`["a": 1, "b": null]` with no expected type is now a `Map[String, Int?]`, not a `Map[String, Int]`.**
  - Before: `t(["a": 1, "b": null])` compiled for a `Map[String, Int]` parameter, then crashed at the read.
  - Now: E0005, as for a list literal. Declare the parameter `Map[String, Int?]`, or drop the `null`.
