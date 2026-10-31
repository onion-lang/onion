### Fixed

- **A record component typed with a `type` alias works in `from re"..."`, `derive!(Json)` and `shape` (#2507).**
  - Before: `type Port = Int` then `record Addr(host: String, port: Port) from re"(\S+):(\d+)"` was E0021, about a constructor call nobody wrote.
  - Now: `port` is read as an `Int`. An alias to an unsupported type is E0061, as the type itself would be.
  - In a `shape doc = json` record, `type Tags = List[String]` now reads as a list instead of E0061.
