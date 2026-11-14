### Added

- **A built-in `Numeric` type class: `import { onion.typeclass.Numeric }` (#2739, RFC #2760).**
  - `def total[T: Numeric](xs: List[T]): T` now works on `List[Int]`, `Long`, `Double`, `Float`, `BigInteger` and `BigDecimal` with no `instance` of your own; `Int` and `Long` throw on overflow.
  - A user `instance Numeric[Int]` (or any type above) is E0097, as a second instance always is.
  - `Stats::sum` is unchanged for now. Java callers get an instance from `Numeric.forInt()` and friends.
