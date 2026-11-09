### Fixed

- **A type-class instance declared in a `module` is found, and a same-named trait cannot capture it (#2769).**
  - Before: calling a `[T: Numeric]` method from a `module` was E0003 "instance Numeric[Integer] is not found". With a second `trait Numeric` elsewhere, the program compiled and then threw `IncompatibleClassChangeError`.
  - Now: the compiler looks for the instance beside the caller, the trait, the type argument and in the default package, and uses only one that implements that trait.
- **Two instances of one trait for one type are E0097 even in different files or modules.**
  - Before: both compiled, and the call site's package silently decided which one ran.
  - Now: the second one is `E0097 instance ... is already defined`.
