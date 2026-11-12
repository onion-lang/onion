### Fixed

- **`instance Show[List[String]]` and `instance Show[List[Integer]]` compile together again (#2778).**
  - Before: the second one was E0097, "already defined", since only `List` was compared.
  - Now: the type arguments count too; the same instantiation twice is still E0097.
