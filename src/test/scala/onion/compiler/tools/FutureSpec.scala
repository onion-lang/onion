package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the Future library for asynchronous programming.
 *
 * Note: Some transformation methods (map, flatMap, filter) require generic type
 * parameter inference that Onion's current type system doesn't fully support.
 * These are tested through direct Java interop in more complex scenarios.
 */
class FutureSpec extends AbstractShellSpec {

  describe("Future") {

    describe("factory methods") {
      it("creates a successful future with value") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::successful[String]("hello");
            |    return f.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("hello"))
      }

      it("creates a failed future with exception") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::failed[String](new RuntimeException("test error"));
            |    return if(f.isFailure()) { "failed" } else { "success" }
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("failed"))
      }

      it("async runs operation in background and returns result") {
        val result = shell.run(
          """
            |import { onion.Future; java.lang.Integer; }
            |class Test {
            |public:
            |  static def main(args: String[]): Integer {
            |    val f = Future::async[Integer](() -> { Integer::valueOf(40 + 2) });
            |    return f.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(42))
      }
    }

    describe("transformation methods") {
      it("map transforms the value when successful") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::successful[String]("hello");
            |    val f2 = f.map((s: String) -> { s + " world" });
            |    return f2.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("hello world"))
      }

      it("map propagates failure") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::failed[String](new RuntimeException("error"));
            |    val f2 = f.map((s: String) -> { s + " world" });
            |    return if(f2.isFailure()) { "failed" } else { "success" }
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("failed"))
      }

      it("flatMap chains async operations") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f1 = Future::successful[String]("hello");
            |    val f2 = f1.flatMap((s: String) -> { Future::successful[String](s + "!") });
            |    return f2.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("hello!"))
      }

      it("filter keeps value when predicate is true") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::successful[String]("hello");
            |    val f2 = f.filter((s: String) -> { s.length() > 3 });
            |    return f2.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("hello"))
      }

      it("filter fails when predicate is false") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): Boolean {
            |    val f = Future::successful[String]("hi");
            |    val f2 = f.filter((s: String) -> { s.length() > 3 });
            |    return f2.isFailure()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(true))
      }
    }

    describe("error handling") {
      it("recover provides fallback value on failure") {
        val result = shell.run(
          """
            |import { onion.Future; java.lang.Throwable; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::failed[String](new RuntimeException("error"));
            |    val f2 = f.recover((e: Throwable) -> { "recovered" });
            |    return f2.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("recovered"))
      }

      it("recover does nothing on success") {
        val result = shell.run(
          """
            |import { onion.Future; java.lang.Throwable; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::successful[String]("original");
            |    val f2 = f.recover((e: Throwable) -> { "recovered" });
            |    return f2.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("original"))
      }
    }

    describe("blocking operations") {
      it("getOrElse returns value on success") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::successful[String]("value");
            |    return f.getOrElse("default")
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("value"))
      }

      it("getOrElse returns default on failure") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::failed[String](new RuntimeException("error"));
            |    return f.getOrElse("default")
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("default"))
      }

      it("awaitTimeout returns value within timeout") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::successful[String]("quick");
            |    return f.awaitTimeout(1000L)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("quick"))
      }
    }

    describe("status queries") {
      it("isCompleted returns true for completed future") {
        val result = shell.run(
          """
            |import { onion.Future; java.lang.Integer; }
            |class Test {
            |public:
            |  static def main(args: String[]): Boolean {
            |    val f = Future::successful[Integer](Integer::valueOf(1));
            |    return f.isCompleted()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(true))
      }

      it("isSuccess returns true for successful future") {
        val result = shell.run(
          """
            |import { onion.Future; java.lang.Integer; }
            |class Test {
            |public:
            |  static def main(args: String[]): Boolean {
            |    val f = Future::successful[Integer](Integer::valueOf(1));
            |    return f.isSuccess()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(true))
      }

      it("isFailure returns true for failed future") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): Boolean {
            |    val f = Future::failed[String](new RuntimeException("error"));
            |    return f.isFailure()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(true))
      }
    }

    describe("type inference") {
      it("infers type argument for static method from argument type") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::successful("hello");
            |    return f.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("hello"))
      }

      it("infers lambda parameter type from context") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::successful[String]("hello");
            |    val f2 = f.map((s) -> { s + " world" });
            |    return f2.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("hello world"))
      }

      it("chains type inference through multiple map calls") {
        val result = shell.run(
          """
            |import { onion.Future; java.lang.Integer; }
            |class Test {
            |public:
            |  static def main(args: String[]): Integer {
            |    val f1 = Future::successful("test");
            |    val f2 = f1.map((s) -> { Integer::valueOf(s.length()) });
            |    val f3 = f2.map((n) -> { Integer::valueOf(n.intValue() * 2) });
            |    return f3.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(8))
      }

      it("supports trailing lambda syntax for instance method") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::successful[String]("hello");
            |    val f2 = f.map() { s => s + " world" };
            |    return f2.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("hello world"))
      }

      it("supports trailing lambda with type inference") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = Future::successful("hi");
            |    val f2 = f.map() { s => s + "!" };
            |    return f2.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("hi!"))
      }
    }

    describe("conversion methods") {
      it("toOption returns Some for success") {
        val result = shell.run(
          """
            |import { onion.Future; onion.Option; }
            |class Test {
            |public:
            |  static def main(args: String[]): Boolean {
            |    val f = Future::successful[String]("value");
            |    val opt = f.toOption();
            |    return opt.isDefined()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(true))
      }

      it("toOption returns None for failure") {
        val result = shell.run(
          """
            |import { onion.Future; onion.Option; }
            |class Test {
            |public:
            |  static def main(args: String[]): Boolean {
            |    val f = Future::failed[String](new RuntimeException("error"));
            |    val opt = f.toOption();
            |    return opt.isEmpty()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(true))
      }

      it("toResult returns Ok for success") {
        val result = shell.run(
          """
            |import { onion.Future; onion.Result; }
            |class Test {
            |public:
            |  static def main(args: String[]): Boolean {
            |    val f = Future::successful[String]("value");
            |    val res = f.toResult();
            |    return res.isOk()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(true))
      }

      it("toResult returns Err for failure") {
        val result = shell.run(
          """
            |import { onion.Future; onion.Result; }
            |class Test {
            |public:
            |  static def main(args: String[]): Boolean {
            |    val f = Future::failed[String](new RuntimeException("error"));
            |    val res = f.toResult();
            |    return res.isErr()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(true))
      }
    }

    describe("do notation") {
      it("desugars single ret to successful") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = do[Future] { ret "hello" };
            |    return f.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("hello"))
      }

      it("desugars bind with arrow notation") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = do[Future] {
            |      x <- Future::successful("hello");
            |      ret x + " world"
            |    };
            |    return f.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("hello world"))
      }

      it("chains multiple bindings") {
        val result = shell.run(
          """
            |import { onion.Future; java.lang.Integer; }
            |class Test {
            |public:
            |  static def main(args: String[]): Integer {
            |    val f = do[Future] {
            |      x <- Future::successful(Integer::valueOf(10));
            |      y <- Future::successful(Integer::valueOf(20));
            |      z <- Future::successful(Integer::valueOf(12));
            |      ret Integer::valueOf(x.intValue() + y.intValue() + z.intValue())
            |    };
            |    return f.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(42))
      }

      it("supports final expression without ret") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = do[Future] {
            |      x <- Future::successful("test");
            |      x + "!"
            |    };
            |    return f.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("test!"))
      }

      it("works with type inference") {
        val result = shell.run(
          """
            |import { onion.Future; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val f = do[Future] {
            |      x <- Future::successful("hi");
            |      y <- Future::successful(" there");
            |      ret x + y
            |    };
            |    return f.await()
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success("hi there"))
      }
    }
  }
}
