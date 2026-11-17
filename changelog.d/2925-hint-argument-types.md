### Fixed

- **`sha256(42)` no longer hints `Hash::sha256(...)` when `Hash::sha256` cannot take that argument (#2925).**
  - Before: the "call it as `Cls::name(...)`" hint matched on name and argument count, so `bytes("abc")` was sent to `Format::bytes`, which gave a second E0005.
  - Now: the hint appears only when an overload accepts the argument types.
