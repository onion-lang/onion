### Fixed

- **`Archive::gzipFile`/`gunzipFile` read `/dev/stdin`, `/dev/null` and pipes again (#2924).**
  - Before: any non-regular source failed with "no such file".
  - Now: only a missing path or a directory is refused. A target under a file says "not a directory".
