# 非同期・並行処理の例

Onionは `Future` 型と `do[Future]` 構文を提供し、非同期プログラミングを行えます。

## Futureの作成

`Future::async` でバックグラウンドで処理を実行します。

```onion
def fetchPage(url: String, delayMs: Long): Future[String] {
  return Future::async(() -> {
    Thread::sleep(delayMs)
    return "body of " + url
  })
}

val f = fetchPage("https://example.com", 100L)
println(f.getOrElse("failed"))
```

## 複数のFutureを待つ

いくつかのFutureを起動し、すべてが完了するのを待ちます。

```onion
val f1 = fetchPage("a", 50L)
val f2 = fetchPage("b", 30L)
val f3 = fetchPage("c", 40L)

Future::all(f1, f2, f3).onSuccess((results: Object[]) -> {
  println("Downloaded " + results.length + " pages")
  for var i: Int = 0; i < results.length; i = i + 1 {
    println("  " + (results[i] as String))
  }
})
```

## Do記法

`do[Future]` でFutureを連鎖させます。

```onion
val combined: Future[String] = do[Future] {
  a <- fetchPage("page-a", 20L)
  b <- fetchPage("page-b", 20L)
  ret a.length() + ":" + b.length()
}

println(combined.getOrElse("failed"))
```

## エラーハンドリング

`onFailure`、`recover`、`recoverWith` で失敗を処理します。

```onion
val risky: Future[Int] = Future::failed(new Exception("simulated failure"))

risky.onFailure((error: Throwable) -> {
  println("caught: " + error.getMessage())
})

val recovered = risky.recover((error: Throwable) -> {
  println("recovering from " + error.getMessage())
  return 0
})

println("value=" + recovered.getOrElse(-1))
```

## 完全な例: 非同期ダウンローダー

**`AsyncDownloader.on`** は複数のページを同時にダウンロードして結果を集約します。

```onion
def fetchPage(url: String, delayMs: Long): Future[String] {
  return Future::async(() -> {
    Thread::sleep(delayMs)
    return "body of " + url
  })
}

val urls = [
  "https://example.com/a",
  "https://example.com/b",
  "https://example.com/c"
]

val f1 = fetchPage(urls[0] as String, 50L)
val f2 = fetchPage(urls[1] as String, 30L)
val f3 = fetchPage(urls[2] as String, 40L)

Future::all(f1, f2, f3).onSuccess((results: Object[]) -> {
  println("Downloaded " + results.length + " pages")
})

val combined: Future[String] = do[Future] {
  a <- fetchPage("page-a", 20L)
  b <- fetchPage("page-b", 20L)
  ret a.length() + ":" + b.length()
}

println(combined.getOrElse("failed"))
```

## Futureの競争

`race` を使って最初に完了したFutureの値を取得します。

```onion
val fast = fetchPage("fast", 10L)
val slow = fetchPage("slow", 100L)
val winner = fast.race(slow)
println("winner: " + winner.getOrElse("none"))
```

## 次のステップ

- [JSONとHTTPの例](json-http.md) - FutureとHTTPリクエストを組み合わせる
- [エラーハンドリングの例](error-handling.md) - 非同期処理の失敗を扱う
- [標準ライブラリリファレンス](../reference/stdlib.md) - Future API全文
