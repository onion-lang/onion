### Breaking

- **`Args` `intOption("count", 3)` rejects `--count abc` instead of returning `3` (#2881).**
  - Before: a typo such as `--count 10O` silently ran with the default.
  - Now: `IllegalArgumentException: invalid value for --count: 'abc' (expected Int)`, as the auto-CLI reports. The default still applies when the option is absent.
