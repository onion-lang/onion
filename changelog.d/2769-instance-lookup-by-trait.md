### Fixed

- **A type-class instance declared in a `module` is found, and a same-named trait cannot capture it (#2769).**
  - Before: calling a `[T: Numeric]` method from a `module` was E0003 "instance Numeric[Integer] is not found". With a second `trait Numeric` elsewhere, the program compiled and then threw `IncompatibleClassChangeError`.
  - Now: the compiler looks for the instance beside the caller, the trait and the type argument, and uses only one that implements that trait.
