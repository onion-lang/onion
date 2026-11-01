### Fixed

- **`(n as Int)` compiles again when `n` is a `Number?`, and `(i as Long)` when `i` is an `Integer` (#2551).**
  - Before: E0000, "type Int is expected, but type Number? is used". Nullable forms ran before #2388; non-null `Number as Int` never compiled.
  - Now: a supertype of the box (`Number`, `Serializable`, `Comparable[JInteger]`) narrows then unboxes, as in Java. A numeric box converts to any numeric primitive, like the primitive cast. `Comparable[String] as Int` and `Integer as Char` stay E0000.
