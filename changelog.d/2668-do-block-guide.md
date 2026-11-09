### Documentation

- **The guide's longer do-block example compiles (#2668).**
  - Before: it used a `val` and a bare `println`, both rejected inside a do block.
  - Now: it binds the intermediate value and prints in the `yield` block; the Japanese guide has the same section.
