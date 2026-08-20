package onion.compiler.tools

import onion.tools.Shell

/**
 * `onion.Db`: SQL over JDBC.
 *
 * The driver is never bundled — it is whatever the project declares in `[dependencies]`.
 * These tests use H2, a pure-Java in-memory database pulled in at test scope only, so a
 * real JDBC driver is exercised without a server and without growing the shipped jar.
 *
 * Each case uses its own named in-memory database, so nothing leaks between them and the
 * suite does not depend on execution order.
 */
class DbSpec extends AbstractShellSpec {

  describe("Db") {
    it("creates, inserts and queries, returning rows as Maps in column order") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val db = Db::connect("jdbc:h2:mem:basic;DB_CLOSE_DELAY=-1")
          |    db.update("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(64), age INT)")
          |    db.update("INSERT INTO users VALUES (?, ?, ?)", 1, "ada", 36)
          |    db.update("INSERT INTO users VALUES (?, ?, ?)", 2, "grace", 45)
          |
          |    val rows = db.query("SELECT name, age FROM users WHERE age > ? ORDER BY id", 30)
          |    val first = (rows[0] as java.util.Map)
          |    val second = (rows[1] as java.util.Map)
          |    val out = rows.size + ":" + first["NAME"] + "," + second["NAME"]
          |    db.close()
          |    return out
          |  }
          |}
          |""".stripMargin,
        "DbBasic.on",
        Array()
      )
      assert(Shell.Success("2:ada,grace") == result)
    }

    it("binds parameters rather than pasting them, so a quote is just a character") {
      // The value below ends a string literal and starts a DROP if it is ever concatenated
      // into the SQL. Bound, it is only a name.
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val db = Db::connect("jdbc:h2:mem:binding;DB_CLOSE_DELAY=-1")
          |    db.update("CREATE TABLE t (name VARCHAR(128))")
          |    val nasty = "x'); DROP TABLE t; --"
          |    db.update("INSERT INTO t VALUES (?)", nasty)
          |    val back = db.queryValue("SELECT name FROM t")
          |    val still = db.queryValue("SELECT COUNT(*) FROM t")
          |    db.close()
          |    if back == nasty { return "bound:" + still }
          |    return "got " + back
          |  }
          |}
          |""".stripMargin,
        "DbBinding.on",
        Array()
      )
      assert(Shell.Success("bound:1") == result)
    }

    it("commits a transaction that returns") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val db = Db::connect("jdbc:h2:mem:commit;DB_CLOSE_DELAY=-1")
          |    db.update("CREATE TABLE t (n INT)")
          |    db.transaction((conn) -> {
          |      conn.update("INSERT INTO t VALUES (?)", 1)
          |      conn.update("INSERT INTO t VALUES (?)", 2)
          |    })
          |    val count = db.queryValue("SELECT COUNT(*) FROM t")
          |    db.close()
          |    return "" + count
          |  }
          |}
          |""".stripMargin,
        "DbCommit.on",
        Array()
      )
      assert(Shell.Success("2") == result)
    }

    it("rolls a transaction back when the body throws, and rethrows") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val db = Db::connect("jdbc:h2:mem:rollback;DB_CLOSE_DELAY=-1")
          |    db.update("CREATE TABLE t (n INT)")
          |    db.update("INSERT INTO t VALUES (?)", 100)
          |    var raised: String = "none"
          |    try {
          |      db.transaction((conn) -> {
          |        conn.update("INSERT INTO t VALUES (?)", 1)
          |        throw new RuntimeException("half way")
          |      })
          |    } catch e: Exception {
          |      raised = e.getMessage()
          |    }
          |    val count = db.queryValue("SELECT COUNT(*) FROM t")
          |    db.close()
          |    // The pre-existing row survives; the one inserted inside the transaction does not.
          |    if ("" + count) == "1" && raised.contains("half way") { return "rolled back" }
          |    return count + "/" + raised
          |  }
          |}
          |""".stripMargin,
        "DbRollback.on",
        Array()
      )
      assert(Shell.Success("rolled back") == result)
    }

    it("returns null from queryOne when nothing matches, rather than an empty row") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val db = Db::connect("jdbc:h2:mem:empty;DB_CLOSE_DELAY=-1")
          |    db.update("CREATE TABLE t (n INT)")
          |    val row = db.queryOne("SELECT n FROM t WHERE n = ?", 9)
          |    db.close()
          |    if row == null { return "null" }
          |    return "got a row"
          |  }
          |}
          |""".stripMargin,
        "DbQueryOneEmpty.on",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("refuses a result whose columns share a label instead of losing one") {
      // Two columns with the same label cannot both survive in a Map. Keeping one silently
      // would drop a column the query asked for and never say so.
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val db = Db::connect("jdbc:h2:mem:dupes;DB_CLOSE_DELAY=-1")
          |    db.update("CREATE TABLE a (id INT)")
          |    db.update("CREATE TABLE b (id INT)")
          |    try {
          |      db.query("SELECT a.id, b.id FROM a, b")
          |      db.close()
          |      return "silently lost a column"
          |    } catch e: Exception {
          |      db.close()
          |      if e.getMessage().contains("AS") { return "refused" }
          |      return e.getMessage()
          |    }
          |  }
          |}
          |""".stripMargin,
        "DbDuplicateLabels.on",
        Array()
      )
      assert(Shell.Success("refused") == result)
    }

    it("rejects a URL that is not a JDBC URL before trying to connect") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    try {
          |      Db::connect("postgres://localhost/app")
          |      return "accepted"
          |    } catch e: Exception {
          |      if e.getMessage().contains("jdbc:") { return "rejected" }
          |      return e.getMessage()
          |    }
          |  }
          |}
          |""".stripMargin,
        "DbBadUrl.on",
        Array()
      )
      assert(Shell.Success("rejected") == result)
    }
  }
}
