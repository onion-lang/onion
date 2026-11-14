### Fixed

- **`Archive::unzip` refuses an entry that would overwrite the archive it reads (#2957).**
  - Before: `unzip("a.zip", ".")` with an entry named `a.zip` returned normally, and the archive was replaced by that entry.
  - Now: it throws "source and target are the same file" before extracting anything, as `zip` and `gunzipFile` already do.
