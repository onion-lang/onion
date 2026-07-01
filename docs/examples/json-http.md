# JSON & HTTP Examples

Onion provides built-in support for JSON and YAML parsing, HTTP requests, and deriving serializers for records.

## Deriving JSON for Records

Use `derive!(Json)` on a record to automatically generate `toJson` and `fromJson` static methods.

```onion
record User(id: Int, name: String, email: String) derive!(Json)

val user = new User(1, "Alice", "alice@example.com")
val json = User::toJson(user)
println(json)

val parsed: User? = User::fromJson(json)
if parsed != null {
  println("name=" + parsed.name())
}
```

Output:

```json
{"id":1,"name":"Alice","email":"alice@example.com"}
```

## Parsing JSON Strings

For ad-hoc JSON, use the `Json` module directly.

```onion
val raw = "{\"count\": 42, \"items\": [\"a\", \"b\", \"c\"]}"
val map = Json::parse(raw) as Map
println("count=" + map.get("count"))
```

## YAML Config Files

Records can also derive YAML support with `derive!(Yaml)`.

```onion
record ServerConfig(host: String, port: Int, debug: Boolean) derive!(Yaml)

val yaml = "host: localhost\nport: 8080\ndebug: false\n"
val cfg: ServerConfig? = ServerConfig::fromYaml(yaml)
if cfg != null {
  println(cfg.host() + ":" + cfg.port())
}
```

## HTTP GET Requests

Use the `Http` module for simple HTTP calls.

```onion
try {
  val response = Http::get("https://api.example.com/users")
  println("length=" + response.length())
} catch e: Exception {
  println("request failed: " + e.getMessage())
}
```

## HTTP POST with JSON

Send JSON in a POST request:

```onion
val payload = "{\"name\":\"Alice\",\"age\":30}"
try {
  val response = Http::postJson("https://api.example.com/users", payload)
  println(response)
} catch e: Exception {
  println("request failed")
}
```

## Complete Example: JSON API Client

**`JsonApiClient.on`** demonstrates record serialization, HTTP GET, and graceful fallback when the network is unavailable.

```onion
record User(id: Int, name: String, email: String) derive!(Json)

val userJson = "{\"id\":1,\"name\":\"Alice\",\"email\":\"alice@example.com\"}"
val user = User::fromJson(userJson)
if user != null {
  println("parsed " + user.name())
}

val url = "https://httpbin.org/get"
try {
  val response = Http::get(url)
  println("http status length=" + response.length())
} catch e: Exception {
  println("network request skipped")
}

// Round-trip
val bob = new User(42, "Bob", "bob@example.com")
val back = User::fromJson(User::toJson(bob))
if back != null {
  println("roundtrip=" + back.name())
}
```

## URL Utilities

Build query strings and encode values:

```onion
val query = Http::buildQuery(["q", "onion lang", "limit", "10"])
val url = "https://api.example.com/search?" + query
println(url)
```

## Next Steps

- [Scripting Examples](scripting.md) - CLI arguments and process execution
- [Async Examples](async.md) - Make HTTP calls concurrently
- [Standard Library Reference](../reference/stdlib.md) - Full Http/Json/Yaml API
