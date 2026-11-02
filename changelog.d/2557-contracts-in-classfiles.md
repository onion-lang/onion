### Added

- **A contract crosses a module boundary.**
  - A method with `requires` / `ensures` gets `@onion.Contract` in its class file: the parameter names and the clauses as text. A clause that would not read back as the same clause is left out.
  - A module built later against that class file reads the contracts and uses them: `n = n + s.step(k)` is proved from the `step` contract written in the other module. A built type with no contract, or a weaker one, proves nothing.
