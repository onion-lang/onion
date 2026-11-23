### Fixed

- **An `instance` declared through an import alias is found by `[T: Trait]`.**
  - Before: `import { lib.Sh as S }` + `instance S[Integer]` gave E0003 at the call.
  - Now: the alias names the same trait, so the program prints `I5`.
