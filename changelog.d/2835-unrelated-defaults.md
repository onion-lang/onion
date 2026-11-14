### Fixed

- **A class that inherits two unrelated defaults for one method is now E0116.**
  - Before: `class C conforms A, B` compiled when `A` and `B` both give `g()` a body, then `c.g()` threw `AbstractMethodError`.
  - Now: E0116 at the class, telling you to override `g()` in `C`, as Java does.
