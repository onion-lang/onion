### Fixed

- **Two contract messages read correctly (#2605).**
  - Before: a predicate that threw gave E0113 "is threw ..."; W0021 was English-only, with single quotes and no final period.
  - Now: E0113 says `(threw X: ...)`; W0021 is translated and styled like the other warnings.
