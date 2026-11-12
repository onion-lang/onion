### Fixed

- **`Yaml::parse` reads a file that starts with `---` (#2805).**
  - Before: `---\nname: Alice` failed with "found no colon".
  - Now: a leading `---` and a trailing `...` are skipped. A second document is rejected with "Multiple documents are out of scope".
