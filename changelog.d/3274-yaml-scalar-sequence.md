### Fixed

- **`Yaml::parse("- 1\n- 2")` says a sequence of scalars is out of scope (#3274).**
  - Before: `Expected 'key: value' but found no colon: 1`, as if the file were malformed.
  - Now: the message names the scope limit and points to `Json`, like the other out-of-scope cases.
