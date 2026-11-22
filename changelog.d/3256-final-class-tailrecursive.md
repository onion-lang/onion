### Fixed

- **A `@TailRecursive` method of a `final` class or a record becomes a loop (#3256).**
  - Before: it was treated as overridable, so deep recursion overflowed the stack, and W0016 said "it can be overridden".
  - Now: no subclass can exist, so it is rewritten like a `final` method, with no warning.
