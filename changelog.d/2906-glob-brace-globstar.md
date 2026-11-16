### Fixed

- **`Files::glob(dir, "{**/*.on,*.md}")` finds `.on` files directly in `dir`.**
  - Before: only `*.md` and nested `.on` files matched; `./**/*.on` returned `[]`.
  - Now: each brace alternative matches zero directories for `**/`, and a leading `./` is ignored.
