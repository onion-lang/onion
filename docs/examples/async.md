# Async & Concurrency Examples

Onion provides a `Future` type and `do[Future]` notation for asynchronous programming.

## Creating Futures

Run work in the background with `Future::async`.

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

## Waiting for Multiple Futures

Launch several futures and wait for all of them.

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

## Do-Notation

Chain futures with `do[Future]`.

```onion
val combined: Future[String] = do[Future] {
  a <- fetchPage("page-a", 20L)
  b <- fetchPage("page-b", 20L)
  ret a.length() + ":" + b.length()
}

println(combined.getOrElse("failed"))
```

## Error Handling

Handle failures with `onFailure`, `recover`, or `recoverWith`.

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

## Complete Example: Async Downloader

**`AsyncDownloader.on`** downloads several simulated pages concurrently and aggregates the results.

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

## Racing Futures

Use `race` to get the value from the first future that completes.

```onion
val fast = fetchPage("fast", 10L)
val slow = fetchPage("slow", 100L)
val winner = fast.race(slow)
println("winner: " + winner.getOrElse("none"))
```

## Next Steps

- [JSON & HTTP Examples](json-http.md) - Combine futures with HTTP requests
- [Error Handling Examples](error-handling.md) - Handle async failures
- [Standard Library Reference](../reference/stdlib.md) - Full Future API
