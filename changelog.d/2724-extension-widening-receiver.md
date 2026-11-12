### Fixed

- **`n.bytes()` compiles when `n` is an `Int` (#2724).**
  - Before: E0005 for an `Int` receiver of an `extension Long` method, though `Format::bytes(n)` worked.
  - Now: a primitive receiver widens as a primitive argument does (`Byte`/`Short`/`Int` to `Long`, `Float` or `Double`; `Long` to `Float`/`Double`), when no extension of that name exists on its own type or a supertype (`Number`, `Object`). A boxed value, such as an element of a `List[Int]`, does not widen, just as it does not as an argument. A `Long` is still not an `Int` receiver.
