### Fixed

- **`(n as Int)` compiles again when `n` is a `Number?`, and `(i as Long)` when `i` is an `Integer` (#2551).**
  - Before: E0000, "type Int is expected, but type Number? is used". Nullable forms ran before #2487.
  - Now: a box's supertype or another numeric box converts as the primitive cast does. `Comparable[String] as Int` and `Integer as Char` stay E0000.
- **`(o as Byte)` and `(o as Short)` narrow the value, so a `Byte` never holds 300 (#2562).**
  - Before: `val o: Object = 300; val b: Byte = o as Byte` held 300, and printing it could crash.
  - Now: it holds `44`, the same as `(300 as Byte)`.
