package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Concurrent`: bounded parallelism and the pieces needed to use it safely.
 *
 * `Future` could already run one thing off the current thread, but there was no way to
 * bound how many run at once, to share a counter between them, to hold a lock, or to hand
 * work from one thread to another.
 *
 * Every case here is deterministic: the assertions are about results and totals, never
 * about which task finished first.
 */
class ConcurrentSpec extends AbstractShellSpec {

  describe("Concurrent.Pool") {
    it("returns results in the input's order, not in the order they finished") {
      // The point of the guarantee: the second element sleeps longest, so an
      // implementation collecting completions would put it last.
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val pool = Concurrent::pool(4)
          |    val out = pool.mapAll([30, 10, 20], (delayObj) -> {
          |      val delay = (delayObj as Integer).intValue()
          |      Timing::sleep(delay)
          |      return "t" + delay
          |    })
          |    pool.close()
          |    return out[0] + "," + out[1] + "," + out[2]
          |  }
          |}
          |""".stripMargin,
        "ConcurrentOrder.on",
        Array()
      )
      assert(Shell.Success("t30,t10,t20") == result)
    }

    it("runs work off the caller's thread and hands back a Future") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val pool = Concurrent::pool(2)
          |    val a = pool.submit(() -> 6)
          |    val b = pool.submit(() -> 7)
          |    val total = (a.await() as Integer).intValue() * (b.await() as Integer).intValue()
          |    pool.close()
          |    return "" + total
          |  }
          |}
          |""".stripMargin,
        "ConcurrentSubmit.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("reports a failing task instead of hanging or losing it") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val pool = Concurrent::pool(2)
          |    try {
          |      pool.mapAll([1, 2, 3], (n) -> {
          |        if (n as Integer).intValue() == 2 { throw new RuntimeException("bad element") }
          |        return n
          |      })
          |      pool.close()
          |      return "no failure surfaced"
          |    } catch e: Exception {
          |      pool.close()
          |      if e.getMessage().contains("bad element") { return "reported" }
          |      return e.getMessage()
          |    }
          |  }
          |}
          |""".stripMargin,
        "ConcurrentFailure.on",
        Array()
      )
      assert(Shell.Success("reported") == result)
    }
  }

  describe("Concurrent.Counter") {
    it("does not lose increments under contention") {
      // A plain `var` here would end below 4000 on any real machine; that is the whole
      // reason the type exists.
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val counter = Concurrent::counter()
          |    val pool = Concurrent::pool(4)
          |    pool.mapAll([1, 2, 3, 4], (ignored) -> {
          |      var i: Int = 0
          |      while i < 1000 {
          |        counter.increment()
          |        i = i + 1
          |      }
          |      return 0
          |    })
          |    pool.close()
          |    return "" + counter.get()
          |  }
          |}
          |""".stripMargin,
        "ConcurrentCounter.on",
        Array()
      )
      assert(Shell.Success("4000") == result)
    }
  }

  describe("Concurrent.Lock") {
    it("releases the lock even when the body throws") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val lock = Concurrent::lock()
          |    try {
          |      lock.withLock(() -> { throw new RuntimeException("inside") })
          |    } catch e: Exception {
          |      // Ignored: what matters is whether the lock survived it.
          |    }
          |    if lock.tryAcquire() { lock.release(); return "released" }
          |    return "leaked"
          |  }
          |}
          |""".stripMargin,
        "ConcurrentLock.on",
        Array()
      )
      assert(Shell.Success("released") == result)
    }
  }

  describe("Concurrent.Channel") {
    it("hands items from one thread to another") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val chan = Concurrent::channel(4)
          |    val pool = Concurrent::pool(1)
          |    val producer = pool.submit(() -> {
          |      chan.send("one")
          |      chan.send("two")
          |      return "sent"
          |    })
          |    val first = chan.receive()
          |    val second = chan.receive()
          |    producer.await()
          |    pool.close()
          |    return first + "," + second
          |  }
          |}
          |""".stripMargin,
        "ConcurrentChannel.on",
        Array()
      )
      assert(Shell.Success("one,two") == result)
    }

    it("times out rather than blocking forever on an empty channel") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val chan = Concurrent::channel(1)
          |    val nothing = chan.receiveTimeout(50)
          |    if nothing == null { return "timed out" }
          |    return "got " + nothing
          |  }
          |}
          |""".stripMargin,
        "ConcurrentChannelTimeout.on",
        Array()
      )
      assert(Shell.Success("timed out") == result)
    }

    it("refuses null, which would be indistinguishable from an empty receive") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val chan = Concurrent::channel(1)
          |    try {
          |      chan.send(null)
          |      return "accepted null"
          |    } catch e: Exception {
          |      return "refused"
          |    }
          |  }
          |}
          |""".stripMargin,
        "ConcurrentChannelNull.on",
        Array()
      )
      assert(Shell.Success("refused") == result)
    }
  }
}
