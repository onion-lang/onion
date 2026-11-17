### Fixed

- **A json-shape record component typed by `import { java.lang.String as JS }` compiles (#2959).**
  - Before: E0061 said `JS (= java.lang.String)` was unsupported, while listing `String` as supported.
  - Now: `JS` reads as a `String`, as `type Name = String` does.
