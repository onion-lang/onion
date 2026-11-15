### Breaking

- **`Yaml::stringify` refuses text that `Yaml::parse` cannot read back (#2806).**
  - Before: `["apple", "banana"]`, a list with `[:]`, and `NaN` were written. Other YAML tools read `- apple` and `- {}`, but `Yaml::parse` failed or returned a String.
  - Now: each throws `IllegalArgumentException`, as a nested Map already did; so do `toYaml` (`derive!(Yaml)`) and a `shape ... = yaml` `print` for a NaN or infinite component. See `docs/migration/pre-1.0.md`.
