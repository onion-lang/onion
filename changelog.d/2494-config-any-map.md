### Fixed

- **`Config::get` and its getters read any `Map` or `List`, not only parsed JSON (#2494).**
  - Before: a map from `Colls::mapOf`, a `HashMap` or a `TreeMap` read as "not found", so `Config::getInt(cfg, "port", 3000)` returned `3000`.
  - Now: the value is found, as it is for a map from `Config::parseJson`.
