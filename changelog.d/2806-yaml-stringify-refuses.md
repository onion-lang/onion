### Fixed

- **`Yaml::stringify` refuses text that `Yaml::parse` cannot read back (#2806).**
  - Before: `["apple", "banana"]`, a list with `[:]`, and `NaN` were written, then failed (or came back as a String) on reload.
  - Now: each throws `IllegalArgumentException`, as a nested Map already did. The message suggests `[["v": "apple"], ...]` or `Json`. A record's `toYaml` (`derive!(Yaml)`) and a `shape ... = yaml` `print` throw the same way for a NaN or infinite component.
