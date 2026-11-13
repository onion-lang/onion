### Fixed

- **`Archive::gzipFile`/`gunzipFile` say which path is missing (#2774).**
  - Before: `could not gzip /etc/hostname (/no/dir/out.gz)`, with no reason.
  - Now: `... (no such directory: /no/dir)` or `... (no such file: <source>)`.
