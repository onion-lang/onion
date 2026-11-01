package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/**
 * Tests for cast validation: the compiler must reject casts that can never
 * succeed at runtime (e.g. String -> Int).
 */
class CastValidationSpec extends AbstractShellSpec {

  describe("Cast validation") {

    it("rejects casting a String to Int") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: String = "42"
          |    return s as Int
          |  }
          |}
          |""".stripMargin,
        "StringAsInt.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects casting an Int to String") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val n: Int = 42
          |    val s: String = n as String
          |    return s.length()
          |  }
          |}
          |""".stripMargin,
        "IntAsString.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("allows unboxing an Integer to Int") {
      val result = shell.run(
        """
          |import { java.lang.Integer as JInt }
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val boxed: JInt = 42
          |    return boxed as Int
          |  }
          |}
          |""".stripMargin,
        "IntegerAsInt.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("allows casting Object to a reference type") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val o: Object = "hello"
          |    val s: String = o as String
          |    return s.length()
          |  }
          |}
          |""".stripMargin,
        "ObjectAsString.on",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("rejects casting a List[String] to a String[]") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val list: List[String] = ["a", "b"]
          |    val arr = (list as String[])
          |    return arr.length
          |  }
          |}
          |""".stripMargin,
        "ListAsStringArray.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects casting a String[] to a List[String]") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val arr: String[] = new String[2]
          |    val list = (arr as List[String])
          |    return list.size
          |  }
          |}
          |""".stripMargin,
        "StringArrayAsList.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("still allows casting Object to an array type") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val o: Object = new String[2]
          |    val arr: String[] = (o as String[])
          |    return arr.length
          |  }
          |}
          |""".stripMargin,
        "ObjectAsStringArray.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("still allows casting an array to Object") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val arr: String[] = new String[2]
          |    val o = (arr as Object)
          |    return (o as String[]).length
          |  }
          |}
          |""".stripMargin,
        "StringArrayAsObject.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("allows casting along the class hierarchy") {
      val result = shell.run(
        """
          |class Animal { }
          |class Dog extends Animal {
          |public:
          |  def bark(): Int = 1
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val a: Animal = new Dog()
          |    val d: Dog = a as Dog
          |    return d.bark()
          |  }
          |}
          |""".stripMargin,
        "HierarchyCast.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("rejects a nullable List[String]? as String[] (#2309)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val xs: List[String]? = ["a"]
          |val a = (xs as String[])
          |return a.length
          |  }
          |}
          |""".stripMargin,
        "NullableListAsArray.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects a String? as String[] (#2309)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val s: String? = "a"
          |val a = (s as String[])
          |return a.length
          |  }
          |}
          |""".stripMargin,
        "NullableStringAsArray.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects a String[]? as List[String] (#2309)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val arr: String[]? = new String[1]
          |val l = (arr as List[String])
          |return l.size
          |  }
          |}
          |""".stripMargin,
        "NullableArrayAsList.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects a String[] as Integer[] (#2309)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val ys: String[] = new String[1]
          |val b = (ys as Integer[])
          |return b.length
          |  }
          |}
          |""".stripMargin,
        "StringArrayAsIntegerArray.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects an Int[] as Long[] (#2309)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val ys: Int[] = new Int[1]
          |val b = (ys as Long[])
          |return b.length
          |  }
          |}
          |""".stripMargin,
        "IntArrayAsLongArray.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects an Int[] as Int[][] (#2309)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val ys: Int[] = new Int[1]
          |val b = (ys as Int[][])
          |return b.length
          |  }
          |}
          |""".stripMargin,
        "IntArrayAsIntArray2.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("still allows a nullable Int[]? as Int[] (#2309)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val ys: Int[]? = new Int[3]
          |val b = (ys as Int[])
          |return b.length
          |  }
          |}
          |""".stripMargin,
        "NullableIntArrayAsIntArray.on",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("still allows Object[] as String[] and String[] as Object[] (#2309)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val o: Object[] = (new String[2] as Object[])
          |val s = (o as String[])
          |return s.length
          |  }
          |}
          |""".stripMargin,
        "ObjectArrayStringArray.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("still allows Object[] as String[][] (#2309)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val o: Object[] = (new String[2][1] as Object[])
          |val s = (o as String[][])
          |return s.length
          |  }
          |}
          |""".stripMargin,
        "ObjectArrayAsStringArray2.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("still allows a nullable Object? as String[] (#2309)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val o: Object? = new String[4]
          |val s = (o as String[])
          |return s.length
          |  }
          |}
          |""".stripMargin,
        "NullableObjectAsArray.on",
        Array()
      )
      assert(Shell.Success(4) == result)
    }

    it("still allows Object?[] as String[][] and back (#2309)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val o: Object?[] = new String[2][3]
          |val s = (o as String[][])
          |val back = (s as Object?[])
          |return s.length * 10 + s[0].length + back.length * 100
          |  }
          |}
          |""".stripMargin,
        "NullableElementArrayAsArray2.on",
        Array()
      )
      assert(Shell.Success(223) == result)
    }

    it("rejects a String?[] as Integer[] (#2309)") {
      val result = shell.run(
        """
          |import { java.lang.Integer as JInteger }
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val xs: String?[] = new String[2]
          |val ys = (xs as JInteger[])
          |return ys.length
          |  }
          |}
          |""".stripMargin,
        "NullableStringArrayAsIntegerArray.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("still allows a generic T[] return whose T is an array, as String[][] (#2309 panel)") {
      val result = shell.run(
        """
          |import { java.util.Arrays }
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val a: String[][] = new String[2][3]
          |val c = Arrays::copyOf(a, 2)
          |val d = (c as String[][])
          |return d.length * 10 + d[0].length
          |  }
          |}
          |""".stripMargin,
        "GenericArrayReturnAsArray2.on",
        Array()
      )
      assert(Shell.Success(23) == result)
    }

    it("still allows Int?[] as Integer[] and Object[] (#2309 panel)") {
      val result = shell.run(
        """
          |import { java.lang.Integer as JInteger }
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val i: Int?[] = new Int?[2]
          |val j = (i as JInteger[])
          |val k = (i as Object[])
          |return j.length * 10 + k.length
          |  }
          |}
          |""".stripMargin,
        "NullableIntArrayAsBoxedArray.on",
        Array()
      )
      assert(Shell.Success(22) == result)
    }

    it("still rejects Int?[] as Long[] (#2309 panel)") {
      val result = shell.run(
        """
          |import { java.lang.Long as JLong }
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |val i: Int?[] = new Int?[2]
          |val j = (i as JLong[])
          |return j.length
          |  }
          |}
          |""".stripMargin,
        "NullableIntArrayAsLongArray.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("gives a nullable List cast the List-is-not-an-array hint, not the `!!` one (#2309 panel)") {
      val src =
        """
          |val xs: List[String]? = ["a"]
          |val a = (xs as String[])
          |""".stripMargin
      val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
      val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "NullableListHint.on"))) match {
        case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
        case _ => ""
      }
      // Code spans are the same in both bundles, so this holds in en and ja.
      assert(msgs.contains("toArray(new String[0])"), s"expected the List-is-not-an-array hint, got: $msgs")
      assert(!msgs.contains("`!!`"), s"`!!` does not make this cast work, got: $msgs")
    }
      def inMain(body: String): String =
      s"class Main {\npublic:\n  static def main(args: String[]): Int {\n    $body\n  }\n}\n"

    def castMessages(src: String): String = {
      val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
      new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "CastHint.on"))) match {
        case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
        case _ => ""
      }
    }

    it("gives a nullable List source a null-safe toArray hint that compiles (#2309 panel round 4)") {
      val msgs = castMessages("val xs: List[String]? = [\"a\"]\nval a = (xs as String[])\n")
      assert(msgs.contains("`xs!!.toArray(new String[0])`"), s"expected a null-safe toArray hint, got: $msgs")
      assert(msgs.contains("List[String]?"), s"the message should keep the `?`, got: $msgs")
      val followed = shell.run(inMain("val xs: List[String]? = [\"a\"]\n    return xs!!.toArray(new String[0]).length"), "None", Array())
      assert(followed == Shell.Success(1))
    }

    it("gives a nullable array source a null-safe Colls::toList hint that compiles (#2309 panel round 4)") {
      val msgs = castMessages("val arr: String[]? = new String[1]\nval l = (arr as List[String])\n")
      assert(msgs.contains("`Colls::toList(arr!!)`"), s"expected a null-safe Colls::toList hint, got: $msgs")
      assert(msgs.contains("String[]?"), s"the message should keep the `?`, got: $msgs")
      val followed = shell.run(inMain("val arr: String[]? = new String[1]\n    return Colls::toList(arr!!).size"), "None", Array())
      assert(followed == Shell.Success(1))
    }

    it("names the user's own variable in the List/array hint") {
      val msgs = castMessages("val names: List[String] = [\"a\"]\nval a = (names as String[])\n")
      assert(msgs.contains("`names.toArray(new String[0])`"), s"expected the variable's own name, got: $msgs")
      assert(!msgs.contains("!!"), s"a non-null source needs no `!!`, got: $msgs")
    }

    it("does not advise a cast on a rejected array cast") {
      val msgs = castMessages("val ys: String[] = new String[1]\nval b = (ys as Integer[])\n")
      assert(msgs.contains("Integer[]") || msgs.contains("Int[]"), s"expected the cast error, got: $msgs")
      assert(!msgs.contains("Consider a cast") && !msgs.contains("キャスト"), s"no cast advice on a cast, got: $msgs")
    }

    it("rejects a cast the non-null form rejects when the source is nullable (#2388)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val s: String? = "x"
          |    val n = (s as Integer)
          |    return 0
          |  }
          |}
          |""".stripMargin,
        "NullableStringAsInteger.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("still allows a valid cast from or to a nullable type (#2388)") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val o: Object? = "abc"
          |    val s: String? = (o as String?)
          |    val t: String = (o as String)
          |    return (s as String).length() + t.length()
          |  }
          |}
          |""".stripMargin,
        "NullableValidCast.on",
        Array()
      )
      assert(Shell.Success(6) == result)
    }
  }
}
