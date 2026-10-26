### Fixed

- **Extension overloads pick the most specific one, as class methods do (#2446).**
  - Before: `"x".ex('A')` with `ex(Char)` and `ex(Long)` was E0006 ambiguous, then a contradicting E0005; `"x".ez(3)` with `ez(Object)` was E0005.
  - Now: `ex(Char)` is chosen and `3` boxes into `Object`. A real ambiguity, including two receivers that neither one is more specific than (`extension I` and `extension J`), is one E0006.
