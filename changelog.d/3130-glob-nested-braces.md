### Fixed

- **`Files::glob(dir, "{a,{c,x.md}}")` matches `a`, `c` and `x.md` (#3130).**
  - Before: `PatternSyntaxException: Cannot nest groups`.
  - Now: nested brace groups expand, as in bash.
