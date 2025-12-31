package onion.compiler.tools

import onion.tools.Shell

class JsonSpec extends AbstractShellSpec {
  describe("Json library") {
    describe("parse() - primitives") {
      it("parses null") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("null")
            |    if (obj == null) {
            |      return "null"
            |    } else {
            |      return "not null"
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("null") == result)
      }

      it("parses true") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("true")
            |    return obj.toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }

      it("parses false") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("false")
            |    return obj.toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("false") == result)
      }

      it("parses integer") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("42")
            |    return obj.toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("42") == result)
      }

      it("parses negative integer") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("-123")
            |    return obj.toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("-123") == result)
      }

      it("parses floating-point") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("3.14")
            |    return obj.toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("3.14") == result)
      }

      it("parses simple string") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("\"hello\"")
            |    return obj.toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("hello") == result)
      }
    }

    describe("parse() - arrays") {
      it("parses empty array") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("[]")
            |    return Json::stringify(obj)
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("[]") == result)
      }

      it("parses array with numbers") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("[1,2,3]")
            |    return Json::stringify(obj)
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("[1,2,3]") == result)
      }

      it("parses array with mixed types") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("[1,\"hello\",true,null]")
            |    return Json::stringify(obj)
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("[1,\"hello\",true,null]") == result)
      }
    }

    describe("parse() - objects") {
      it("parses empty object") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("{}")
            |    return Json::stringify(obj)
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("{}") == result)
      }

      it("parses simple object") {
        val result = shell.run(
          """
            |import { onion.Json; java.util.Map; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("{\"name\": \"John\", \"age\": 30}")
            |    val name: String = Json::getString(obj, "name")
            |    val age: Integer = Json::getInt(obj, "age")
            |    return name + ":" + age.toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("John:30") == result)
      }

      it("parses nested object") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("{\"person\": {\"name\": \"Alice\"}}")
            |    val person = Json::get(obj, "person")
            |    val name: String = Json::getString(person, "name")
            |    return name
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("Alice") == result)
      }
    }

    describe("parse() - escape sequences") {
      it("parses escaped quote") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("\"say \\\"hello\\\"\"")
            |    return obj.toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("say \"hello\"") == result)
      }

      it("parses escaped backslash") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("\"path\\\\file\"")
            |    return obj.toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("path\\file") == result)
      }

      it("parses newline escape") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("\"line1\\nline2\"")
            |    val s: String = obj.toString()
            |    if (s.contains("\n")) {
            |      return "has newline"
            |    } else {
            |      return "no newline"
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("has newline") == result)
      }
    }

    describe("stringify()") {
      it("stringifies null") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Json::stringify(null)
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("null") == result)
      }

      it("stringifies boolean") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val t = Json::parse("true")
            |    val f = Json::parse("false")
            |    return Json::stringify(t) + "," + Json::stringify(f)
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true,false") == result)
      }

      it("stringifies number") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Json::stringify(new Integer(42))
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("42") == result)
      }

      it("stringifies string") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Json::stringify("hello")
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("\"hello\"") == result)
      }

      it("stringifies empty array") {
        val result = shell.run(
          """
            |import { onion.Json; java.util.List; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val arr: List = Json::array()
            |    return Json::stringify(arr)
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("[]") == result)
      }

      it("stringifies array with values") {
        val result = shell.run(
          """
            |import { onion.Json; java.util.List; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val arr: List = Json::array()
            |    arr.add(new Integer(1))
            |    arr.add(new Integer(2))
            |    arr.add(new Integer(3))
            |    return Json::stringify(arr)
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("[1,2,3]") == result)
      }

      it("stringifies empty object") {
        val result = shell.run(
          """
            |import { onion.Json; java.util.Map; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj: Map = Json::object()
            |    return Json::stringify(obj)
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("{}") == result)
      }

      it("stringifies object with values") {
        val result = shell.run(
          """
            |import { onion.Json; java.util.Map; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj: Map = Json::object()
            |    obj.put("name", "John")
            |    obj.put("age", new Integer(30))
            |    return Json::stringify(obj)
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("{\"name\":\"John\",\"age\":30}") == result)
      }

      it("escapes special characters in strings") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Json::stringify("line1\nline2")
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("\"line1\\nline2\"") == result)
      }
    }

    describe("round-trip parse and stringify") {
      it("round-trips simple object") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json1 = "{\"name\":\"John\",\"age\":30}"
            |    val obj = Json::parse(json1)
            |    val json2 = Json::stringify(obj)
            |    return json2
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("{\"name\":\"John\",\"age\":30}") == result)
      }

      it("round-trips nested structure") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json1 = "{\"data\":[1,2,3]}"
            |    val obj = Json::parse(json1)
            |    val json2 = Json::stringify(obj)
            |    return json2
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("{\"data\":[1,2,3]}") == result)
      }
    }

    describe("error handling") {
      it("parseOrNull returns null on error") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parseOrNull("{invalid json}")
            |    if (obj == null) {
            |      return "null"
            |    } else {
            |      return "not null"
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("null") == result)
      }
    }

    describe("helper methods") {
      it("asObject returns null for non-object") {
        val result = shell.run(
          """
            |import { onion.Json; java.util.Map; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("42")
            |    val m: Map = Json::asObject(obj)
            |    if (m == null) {
            |      return "null"
            |    } else {
            |      return "not null"
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("null") == result)
      }

      it("asArray returns null for non-array") {
        val result = shell.run(
          """
            |import { onion.Json; java.util.List; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("42")
            |    val arr: List = Json::asArray(obj)
            |    if (arr == null) {
            |      return "null"
            |    } else {
            |      return "not null"
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("null") == result)
      }

      it("get() retrieves value by key") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("{\"key\": \"value\"}")
            |    val value = Json::get(obj, "key")
            |    return value.toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("value") == result)
      }

      it("getInt() converts number to int") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("{\"count\": 42}")
            |    val count: Integer = Json::getInt(obj, "count")
            |    return count.toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("42") == result)
      }

      it("getDouble() converts number to double") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("{\"pi\": 3.14}")
            |    return Json::getDouble(obj, "pi").toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("3.14") == result)
      }

      it("getBoolean() retrieves boolean value") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::parse("{\"active\": true}")
            |    return Json::getBoolean(obj, "active").toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("Type inference with Java standard library methods") {
      it("calls size() on asArray() return value") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val json = "[1,2,3]"
            |    val obj = Json::parse(json)
            |    val arr = Json::asArray(obj)
            |    val size = arr.size()
            |    return "Size: " + size
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("Size: 3") == result)
      }

      it("chains method calls on asArray()") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val size = Json::asArray(Json::parse("[1,2,3]")).size()
            |    return "Count: " + size
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("Count: 3") == result)
      }

      it("calls isEmpty() on asArray() return value") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val arr = Json::asArray(Json::parse("[]"))
            |    val empty = arr.isEmpty()
            |    return "Empty: " + empty
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("Empty: true") == result)
      }

      it("calls keySet() on asObject() return value") {
        val result = shell.run(
          """
            |import { onion.Json; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val obj = Json::asObject(Json::parse("{\"a\":1}"))
            |    return obj.keySet().toString()
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("[a]") == result)
      }
    }
  }
}
