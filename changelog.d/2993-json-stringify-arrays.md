### Fixed

- **`Json::stringify` writes a Java array or a `Set` as a JSON array (#2993).**
  - Before: `Json::stringify(args)` gave `"[Ljava.lang.String;@3b0c9195"`, and a `Set` gave the quoted string `"[a, b]"`.
  - Now: `["a","b"]`, in iteration order; nested in a Map or List too.
