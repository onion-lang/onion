### Fixed

- **`Yaml::stringify` of a key with an inner `"` parses back (#2806).**
  - Before: `["5\" tv": "yes"]` wrote `5" tv: yes`, which `Yaml::parse` rejected.
  - Now: such keys and values are quoted. Lists of strings, `{}` items and `NaN` are still open in #2806.
