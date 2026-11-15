### Fixed

- **An effect error names an extension call as written, `String.shout` (#2846).**
  - Before: `calling Extension$String::shout`, a class you never wrote.
  - Now: `calling String.shout`. The same name is used by E0106.
