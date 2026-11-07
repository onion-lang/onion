### Fixed

- **`extends A(b = 7, a = 3)` and `: this(b = 8, a = 4)` bind arguments by name (#2659).**
  - Before: the names were ignored, so `a` got 7 with no error; `extends A(b = 7)` was E0021.
  - Now: they bind as `new A(b = 7, a = 3)` does, an omitted argument takes its default, and an unknown or repeated name is E0043/E0044.
