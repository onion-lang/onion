### Added

- **Java's anonymous class (`new Runnable { ... }`) gets a hint (#2514).**
  - Before: a bare `Syntax error. Encountered "{"` with a token list.
  - Now: the hint says Onion has no anonymous classes and shows the two ways out: a lambda (`val f: Runnable = () -> ...`) or a named class (`class RunnableImpl conforms Runnable { public: def ... }`).
