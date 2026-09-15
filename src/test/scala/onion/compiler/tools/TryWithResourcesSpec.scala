package onion.compiler.tools

import onion.tools.Shell

class TryWithResourcesSpec extends AbstractShellSpec {
  describe("try-with-resources statement") {
    it("should close AutoCloseable resource automatically") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class SimpleResource conforms AutoCloseable {
          |  var result: StringBuilder;
          |public:
          |  def this(r: StringBuilder) {
          |    this.result = r;
          |  }
          |  def close(): Unit {
          |    this.result.append("closed");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val result = new StringBuilder();
          |    try (val res = new SimpleResource(result)) {
          |      result.append("working,");
          |    }
          |    return result.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("working,closed") == result)
    }

    it("should close single resource") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class MyCloseable conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("closed");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    log.append("start,");
          |    try (val a = new MyCloseable(log)) {
          |      log.append("try,");
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("start,try,closed") == result)
    }

    it("should close resource before catch block executes") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class MyCloseable conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("close,");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try (val res = new MyCloseable(log)) {
          |      log.append("try,");
          |      throw new RuntimeException("error");
          |    } catch e: RuntimeException {
          |      log.append("catch");
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("try,close,catch") == result)
    }

    it("should execute finally after resource close") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class MyCloseable conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("close,");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try (val res = new MyCloseable(log)) {
          |      log.append("try,");
          |    } finally {
          |      log.append("finally");
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("try,close,finally") == result)
    }

    it("should close resource even when exception is thrown") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class MyCloseable conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("closed");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try {
          |      try (val res = new MyCloseable(log)) {
          |        throw new RuntimeException("error");
          |      }
          |    } catch e: RuntimeException {
          |      // Outer catch
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("closed") == result)
    }

    it("should allow calling methods on resource variable") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class MyResource conforms AutoCloseable {
          |  var name: String;
          |public:
          |  def this(n: String) {
          |    this.name = n;
          |  }
          |  def getName(): String {
          |    return this.name;
          |  }
          |  def close(): Unit {
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    try (val res = new MyResource("hello")) {
          |      return res.getName();
          |    }
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hello") == result)
    }

    it("should close multiple resources in reverse order") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class Resource1 conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("1,");
          |  }
          |}
          |class Resource2 conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("2,");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try (val r1 = new Resource1(log); val r2 = new Resource2(log)) {
          |      log.append("try,");
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      // Resources should be closed in reverse order: r2 first, then r1
      assert(Shell.Success("try,2,1,") == result)
    }

    it("should still close an earlier-declared resource when a later resource's close() throws (normal completion)") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class Resource1 conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("1,");
          |  }
          |}
          |class Resource2 conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("2closing,");
          |    throw new RuntimeException("boom2");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try {
          |      try (val r1 = new Resource1(log); val r2 = new Resource2(log)) {
          |        log.append("try,");
          |      }
          |    } catch e: RuntimeException {
          |      log.append("caught:" + e.message());
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      // r2 closes first (and throws), but r1 must still be closed afterward instead of being leaked.
      assert(Shell.Success("try,2closing,1,caught:boom2") == result)
    }

    it("should still close an earlier-declared resource, and keep the body's exception as primary, when both the body and a later resource's close() throw") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class Resource1 conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("1,");
          |  }
          |}
          |class Resource2 conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("2closing,");
          |    throw new RuntimeException("closeFail2");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try {
          |      try (val r1 = new Resource1(log); val r2 = new Resource2(log)) {
          |        log.append("try,");
          |        throw new RuntimeException("bodyfail");
          |      }
          |    } catch e: RuntimeException {
          |      log.append("caught:" + e.message());
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      // The body's exception ("bodyfail") stays the one that propagates; r1 must still be
      // closed even though r2's close() failed first, and even though a body exception was
      // already in flight.
      assert(Shell.Success("try,2closing,1,caught:bodyfail") == result)
    }

    it("should still close an earlier-declared resource when a later resource's close() throws and the try itself has a catch clause") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class Resource1 conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("1,");
          |  }
          |}
          |class Resource2 conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("2closing,");
          |    throw new RuntimeException("closeFail2");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try (val r1 = new Resource1(log); val r2 = new Resource2(log)) {
          |      log.append("try,");
          |      throw new RuntimeException("bodyfail");
          |    } catch e: RuntimeException {
          |      log.append("caught:" + e.message());
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("try,2closing,1,caught:bodyfail") == result)
    }

    it("should route a close() exception to the try's own catch clause when the body completes normally") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class FailingResource conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("closing,");
          |    throw new RuntimeException("closeFail");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try (val r = new FailingResource(log)) {
          |      log.append("try,");
          |    } catch e: RuntimeException {
          |      log.append("caught:" + e.message());
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      // Per JLS 14.20.3, a close() failure on normal completion is subject to the
      // try statement's own catch clauses, exactly like a body exception would be --
      // it must not silently bypass them and propagate uncaught.
      assert(Shell.Success("try,closing,caught:closeFail") == result)
    }

    it("should still run the try's own finally block when a close() exception occurs on normal completion") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class FailingResource conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("closing,");
          |    throw new RuntimeException("closeFail");
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try {
          |      try (val r = new FailingResource(log)) {
          |        log.append("try,");
          |      } finally {
          |        log.append("finally,");
          |      }
          |    } catch e: RuntimeException {
          |      log.append("caught:" + e.message());
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      // The finally block must run even though nothing but the resource close
      // itself failed -- it must not be skipped just because there was no catch
      // clause on the try-with-resources statement to route the exception through.
      assert(Shell.Success("try,closing,finally,caught:closeFail") == result)
    }

    it("should close a resource only once when a `return` inside the try body races a failing close() (no catch/finally)") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class FailingResource conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("closing,");
          |    throw new RuntimeException("closeFail");
          |  }
          |}
          |class Test {
          |public:
          |  static def helper(log: StringBuilder): String {
          |    try (val r = new FailingResource(log)) {
          |      log.append("try,");
          |      return "unreachable";
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try {
          |      helper(log);
          |    } catch e: RuntimeException {
          |      log.append("caught:" + e.message());
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      // A `return` inside the try body must run the same resource-close path as
      // normal completion -- close() must be attempted exactly once, not once on
      // the return's own early-exit path and again when the resulting exception
      // reaches the try's exception handler.
      assert(Shell.Success("try,closing,caught:closeFail") == result)
    }

    it("should close a resource only once when a `return` inside the try body races a failing close() (with finally, no catch)") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class FailingResource conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("closing,");
          |    throw new RuntimeException("closeFail");
          |  }
          |}
          |class Test {
          |public:
          |  static def helper(log: StringBuilder): String {
          |    try (val r = new FailingResource(log)) {
          |      log.append("try,");
          |      return "unreachable";
          |    } finally {
          |      log.append("finally,");
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try {
          |      helper(log);
          |    } catch e: RuntimeException {
          |      log.append("caught:" + e.message());
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("try,closing,finally,caught:closeFail") == result)
    }

    it("should close a resource only once when a `return` inside the try body races a failing close() (with catch and finally)") {
      val result = shell.run(
        """
          |import { java.lang.AutoCloseable; }
          |class FailingResource conforms AutoCloseable {
          |  var log: StringBuilder;
          |public:
          |  def this(l: StringBuilder) {
          |    this.log = l;
          |  }
          |  def close(): Unit {
          |    this.log.append("closing,");
          |    throw new RuntimeException("closeFail");
          |  }
          |}
          |class Test {
          |public:
          |  static def helper(log: StringBuilder): String {
          |    try (val r = new FailingResource(log)) {
          |      log.append("try,");
          |      return "unreachable";
          |    } catch e: IllegalStateException {
          |      log.append("wrong-catch,");
          |      return "wrong";
          |    } finally {
          |      log.append("finally,");
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    val log = new StringBuilder();
          |    try {
          |      helper(log);
          |    } catch e: RuntimeException {
          |      log.append("caught:" + e.message());
          |    }
          |    return log.toString();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      // The close() failure (a RuntimeException) doesn't match the declared catch
      // type (IllegalStateException), so it falls through to the catch-all rethrow
      // path -- which must also only attempt the close once.
      assert(Shell.Success("try,closing,finally,caught:closeFail") == result)
    }
  }
}
