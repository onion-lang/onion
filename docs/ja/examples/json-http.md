# JSONとHTTPの例

OnionはJSON/YAMLの解析、HTTPリクエスト、recordからのシリアライザ導出を内蔵でサポートしています。

## recordからJSONを導出

recordに `derive!(Json)` を付けると、`toJson` と `fromJson` 静的メソッドが自動生成されます。

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

出力:

```json
{"id":1,"name":"Alice","email":"alice@example.com"}
```

## JSON文字列の解析

アドホックなJSONには `Json` モジュールを直接使います。

```onion
val raw = "{\"count\": 42, \"items\": [\"a\", \"b\", \"c\"]}"
val map = Json::parse(raw) as Map
println("count=" + map.get("count"))
```

## YAML設定ファイル

recordに `derive!(Yaml)` を付けるとYAMLも扱えます。

```onion
record ServerConfig(host: String, port: Int, debug: Boolean) derive!(Yaml)

val yaml = "host: localhost\nport: 8080\ndebug: false\n"
val cfg: ServerConfig? = ServerConfig::fromYaml(yaml)
if cfg != null {
  println(cfg.host() + ":" + cfg.port())
}
```

## HTTP GETリクエスト

`Http` モジュールで簡単なHTTP呼び出しができます。

```onion
try {
  val response = Http::get("https://api.example.com/users")
  println("length=" + response.length())
} catch e: Exception {
  println("request failed: " + e.getMessage())
}
```

## JSONを含むHTTP POST

JSONを含むPOSTリクエストを送信します。

```onion
val payload = "{\"name\":\"Alice\",\"age\":30}"
try {
  val response = Http::postJson("https://api.example.com/users", payload)
  println(response)
} catch e: Exception {
  println("request failed")
}
```

## 完全な例: JSON APIクライアント

**`JsonApiClient.on`** はrecordのシリアライズ、HTTP GET、ネットワークが利用できない場合のフォールバックを示します。

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

// ラウンドトリップ
val bob = new User(42, "Bob", "bob@example.com")
val back = User::fromJson(User::toJson(bob))
if back != null {
  println("roundtrip=" + back.name())
}
```

## HTTPを提供する

**`MiniService.on`** はリクエストを送る側ではなく答える側です。ルーティングはパスに対する
`select` で書けるので、正規表現パターンのキャプチャがそのまま値として束縛されます。

```onion
val server = Server::start("localhost", 8080)

server.handleAll((req) -> select req.path() {
  case re"/users/(\d+)" (id): Server::json("{\"id\": " + id + "}")
  case "/health":             Server::text("ok")
  else:                       Server::notFound()
})

IO::println("listening on " + server.port())
server.await()
```

ポート `0` を渡すと OS が空きポートを選び、`port()` がそれを返します。番号を決め打ちして祈らずに
テストの中でサーバを起動できるのはこのためです。`run/MiniWebService.on` に、自分自身へリクエストを
送って終了する完全な例があります。

## 生のTCP

**`TcpClient.on`** は `Http` では話せないプロトコルを扱います。

```onion
val conn = Net::connect("example.com", 80, 5000)
conn.writeLine("GET / HTTP/1.0")
conn.writeLine("Host: example.com")
conn.writeLine("")
IO::println(conn.readAll())
conn.close()
```

受ける側は対称です。

```onion
val listener = Net::listen("localhost", 0, 4)
val peer = listener.accept()
peer.writeLine("echo: " + peer.readLine())
peer.close()
listener.close()
```

## URLユーティリティ

クエリ文字列の構築や値のエンコードができます。

```onion
val query = Http::buildQuery(["q", "onion lang", "limit", "10"])
val url = "https://api.example.com/search?" + query
println(url)
```

## 次のステップ

- [スクリプティングの例](scripting.md) - CLI引数とプロセス実行
- [非同期の例](async.md) - HTTP呼び出しを並列で行う
- [標準ライブラリリファレンス](../reference/stdlib.md) - Http/Json/Yaml API全文
