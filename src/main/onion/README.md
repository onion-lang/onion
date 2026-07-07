# Onion-implemented library modules

These modules are written in **Onion itself** (`.on`), demonstrating that Onion
is expressive enough to host library code — records with methods, operator
methods, generics, Java interop, and exceptions.

The API documentation on the site is generated from these sources with
`oniondoc`:

```sh
java -cp target/scala-3.3.7/onion-*.jar onion.tools.doc.OnionDoc \
  -d docs/api src/main/onion/onion/*.on
```

The rendered pages live under `docs/api/` and are published at
`https://onion-lang.github.io/onion/api/`.
