package onion.compiler.tools

import onion.tools.Shell

class ConfigSpec extends AbstractShellSpec {
  describe("Config module") {
    describe("parseJson and dot notation access") {
      it("accesses nested values with dot notation") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"database\": {\"host\": \"localhost\", \"port\": 5432}}";
            |    val config = Config::parseJson(json);
            |    return Config::getString(config, "database.host", "default");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("localhost") == result)
      }

      it("accesses deeply nested values") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"a\": {\"b\": {\"c\": \"deep\"}}}";
            |    val config = Config::parseJson(json);
            |    return Config::getString(config, "a.b.c", "default");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("deep") == result)
      }
    }

    describe("default values") {
      it("returns default value when path not found") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"foo\": \"bar\"}";
            |    val config = Config::parseJson(json);
            |    return Config::getString(config, "nonexistent.path", "fallback");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("fallback") == result)
      }

      it("returns default for missing intermediate key") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"a\": {\"b\": 1}}";
            |    val config = Config::parseJson(json);
            |    return Config::getString(config, "a.x.y", "missing");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("missing") == result)
      }
    }

    describe("array access") {
      it("accesses array elements by index") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"items\": [\"first\", \"second\", \"third\"]}";
            |    val config = Config::parseJson(json);
            |    return Config::getString(config, "items.1", "none");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("second") == result)
      }

      it("returns default for out-of-bounds array index") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"items\": [\"a\", \"b\"]}";
            |    val config = Config::parseJson(json);
            |    return Config::getString(config, "items.10", "oob");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("oob") == result)
      }

      it("accesses nested object in array") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"users\": [{\"name\": \"Alice\"}, {\"name\": \"Bob\"}]}";
            |    val config = Config::parseJson(json);
            |    return Config::getString(config, "users.0.name", "unknown");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("Alice") == result)
      }
    }

    describe("typed accessors") {
      it("gets integer values with getInt") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"port\": 8080}";
            |    val config = Config::parseJson(json);
            |    val port = Config::getInt(config, "port", 3000);
            |    return "" + port;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("8080") == result)
      }

      it("returns default int for non-numeric value") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"port\": \"invalid\"}";
            |    val config = Config::parseJson(json);
            |    val port = Config::getInt(config, "port", 3000);
            |    return "" + port;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("3000") == result)
      }

      it("gets double values with getDouble") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"rate\": 0.05}";
            |    val config = Config::parseJson(json);
            |    val rate = Config::getDouble(config, "rate", 0.0);
            |    return "" + rate;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("0.05") == result)
      }

      it("gets boolean values with getBoolean") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"enabled\": true, \"debug\": false}";
            |    val config = Config::parseJson(json);
            |    val enabled = Config::getBoolean(config, "enabled", false);
            |    val debug = Config::getBoolean(config, "debug", true);
            |    return "" + enabled + "," + debug;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true,false") == result)
      }
    }

    describe("environment variables") {
      it("returns default when env var not set") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Config::getEnv("NONEXISTENT_VAR_12345", "default_value");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("default_value") == result)
      }

      it("gets existing env var (PATH should exist)") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val path = Config::getEnv("PATH", "");
            |    if (path.length() > 0) {
            |      return "has_path";
            |    } else {
            |      return "no_path";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("has_path") == result)
      }
    }

    describe("hasPath") {
      it("returns true for existing path") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"a\": {\"b\": 1}}";
            |    val config = Config::parseJson(json);
            |    return "" + Config::hasPath(config, "a.b");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }

      it("returns false for missing path") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "{\"a\": 1}";
            |    val config = Config::parseJson(json);
            |    return "" + Config::hasPath(config, "b.c");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("false") == result)
      }
    }
  }
}
