### Fixed

- **A `[T: Numeric]` method from a class file or jar can be called (#2768).**
  - Before: `onionc -d out lib.on`, then `Agg::total(xs)` from another compilation was E0005, with a hint naming `total$default$2$…`.
  - Now: the instance is found and passed, as within one compilation, so type-class libraries work from `-classpath`.
- **A statically imported `[T: Trait]` method gets its instance (#2768).**
  - Before: `import { Agg::* }` then `total(xs)` compiled, then failed with "null value used".
  - Now: it runs; a type with no instance is a compile error naming the instance.
