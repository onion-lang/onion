### Fixed

- **A cycle through a generic supertype is a compile error.**
  - Before: `class A[T] extends B[T]` with `class B[T] extends A[T]` compiled, then crashed with `ClassCircularityError`.
  - Now: E0016, as for the same classes without `[T]`.
