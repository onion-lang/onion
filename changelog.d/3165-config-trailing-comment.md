### Breaking

- **A trailing `# comment` after a `shape name = config` value is a comment.**
  - Before: `port = 5432  # default` was a defect for `Int`, and `host = db  # x` kept `# x` in the `String`.
  - Now: whitespace then `#` starts a comment; quote a value that needs ` #` (`print` does). An edit keeps the comment.
