### Fixed

- **`super.f((s) -> ...)` types the lambda from the superclass method (#2674).**
  - Before: E0052 "lambda parameter s must specify a type", though `this.f((s) -> ...)` compiled.
  - Now: the parameter type comes from `f`'s function type, also for a trailing lambda and inside a closure.
