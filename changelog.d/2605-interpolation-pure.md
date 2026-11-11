### Fixed

- **String interpolation is allowed in a contract, as `+` is.**
  - Before: `requires "n#{n}" != ""` was E0106, naming `StringBuilder::append`, a call you never wrote. `--effects` reported such a method as `mutate`.
  - Now: it compiles, and `--effects` reports it `pure`. A write inside `#{...}` is still E0106.
