### Fixed

- **`n.bytes()` compiles when `n` is an `Int` (#2724).**
  - Before: E0005 for an `Int` receiver of an `extension Long` method, though `Format::bytes(n)` worked.
  - Now: the receiver widens (`Int` to `Long`/`Float`/`Double`, and so on) as an argument does. A `Long` is still not an `Int` receiver.
