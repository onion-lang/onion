### Fixed

- **`Args` `toString()` shows the same reading `positional()` gives (#3034).**
  - Before: after `flag("verbose")`, `toString()` still showed `options={verbose=a.txt}`.
  - Now: it shows `flags=[verbose], positional=[a.txt, b.txt]`.

### Documentation

- **Say to call `flag`/`option` before `positional()` in the scripting guide and stdlib reference (en/ja).**
