### Fixed

- **`Codec::base64Decode` names an emoji as `U+1F600`, not half of it (#2470).**
  - Before: `invalid Base64 character U+D83D at index 4`.
  - Now: `invalid Base64 character U+1F600 at index 4`. The quoted window in `Codec::urlDecode`/`Http::decodeUrl` errors no longer starts or ends inside an emoji either.
