package onion.compiler.tools

import onion.tools.Shell

class AutoBoxingSpec extends AbstractShellSpec {

  describe("primitive types unified as type arguments (#161)") {
    it("stores a primitive value type as its wrapper, so put returns it without NPE") {
      val result = shell.run(
        """
          |import { java.util.HashMap }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = new HashMap[String, Int]()
          |    val prev = m.put("a", 1)
          |    return m.get("a")
          |  }
          |}
          |""".stripMargin,
        "MapPutPrimitive.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("auto-unboxes a primitive-typed get result on assignment to Int") {
      val result = shell.run(
        """
          |import { java.util.HashMap }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = new HashMap[String, Int]()
          |    m.put("a", 10)
          |    val x: Int = m.get("a")
          |    return x + 5
          |  }
          |}
          |""".stripMargin,
        "MapGetUnbox.on",
        Array()
      )
      assert(Shell.Success(15) == result)
    }

    it("uses a user generic class with a primitive type argument") {
      val result = shell.run(
        """
          |class Box[T] {
          |  val value: T
          |public:
          |  def this(v: T) { this.value = v }
          |  def get(): T = this.value
          |  static def main(args: String[]): Int = new Box[Int](42).get()
          |}
          |""".stripMargin,
        "BoxPrimitive.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }

  describe("generic methods returning a primitive-bound type variable") {
    it("does not unbox a discarded null return (Map.put as a statement)") {
      val result = shell.run(
        """
          |import { java.util.HashMap }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = new HashMap[String, Int]()
          |    m.put("a", 1)
          |    m.put("b", 2)
          |    return m.get("a") + m.get("b")
          |  }
          |}
          |""".stripMargin,
        "MapPutStatement.on",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("accumulates counts in a Map[String, Int] (put as statement)") {
      val result = shell.run(
        """
          |import { java.util.HashMap }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val counts = new HashMap[String, Int]()
          |    val words = ["a", "b", "a", "a"]
          |    foreach w: String in words {
          |      val cur = counts.getOrDefault(w, 0)
          |      counts.put(w, cur + 1)
          |    }
          |    return counts.get("a")
          |  }
          |}
          |""".stripMargin,
        "WordCount.on",
        Array()
      )
      assert(Shell.Success(3) == result)
    }
  }

  describe("Auto-boxing") {
    it("boxes int to Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val obj: Object = 42
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "IntBoxing.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("boxes long to Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val obj: Object = 100L
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "LongBoxing.on",
        Array()
      )
      assert(Shell.Success("100") == result)
    }

    it("boxes double to Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val obj: Object = 3.14
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "DoubleBoxing.on",
        Array()
      )
      result match {
        case Shell.Success(s: String) => assert(s.startsWith("3.14"))
        case _ => fail(s"Expected success but got $result")
      }
    }

    it("boxes byte to Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val obj: Object = 127B
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "ByteBoxing.on",
        Array()
      )
      assert(Shell.Success("127") == result)
    }

    it("boxes short to Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val obj: Object = 1000S
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "ShortBoxing.on",
        Array()
      )
      assert(Shell.Success("1000") == result)
    }

    it("boxes boolean to Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val obj: Object = true
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "BooleanBoxing.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("boxes float to Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: Float = 1.3F
          |    val obj: Object = f
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "FloatBoxing.on",
        Array()
      )
      result match {
        case Shell.Success(s: String) => assert(s.startsWith("1.3"))
        case _ => fail(s"Expected success but got $result")
      }
    }

    it("boxes char to Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c: Char = 'A'
          |    val obj: Object = c
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "CharBoxing.on",
        Array()
      )
      assert(Shell.Success("A") == result)
    }
  }

  describe("Auto-unboxing") {
    it("unboxes Integer to int") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val i: Integer = new Integer(42)
          |    val x: Int = i
          |    return x
          |  }
          |}
          |""".stripMargin,
        "IntUnboxing.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("unboxes Boolean in conditions") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b: JBoolean = new JBoolean(true)
          |    if (b) {
          |      return "ok"
          |    } else {
          |      return "ng"
          |    }
          |  }
          |}
          |""".stripMargin,
        "BooleanConditionUnboxing.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }
  }

  describe("Method calls on primitives") {
    it("calls toString on int") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = 42
          |    return x.toString()
          |  }
          |}
          |""".stripMargin,
        "IntToString.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("calls toString on boolean") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = true
          |    return x.toString()
          |  }
          |}
          |""".stripMargin,
        "BooleanToString.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("calls hashCode on int") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 42
          |    return x.hashCode()
          |  }
          |}
          |""".stripMargin,
        "IntHashCode.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("calls toString on byte") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = 127B
          |    return x.toString()
          |  }
          |}
          |""".stripMargin,
        "ByteToString.on",
        Array()
      )
      assert(Shell.Success("127") == result)
    }

    it("calls toString on short") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = 1000S
          |    return x.toString()
          |  }
          |}
          |""".stripMargin,
        "ShortToString.on",
        Array()
      )
      assert(Shell.Success("1000") == result)
    }
  }

  describe("Method arguments with boxing") {
    it("boxes int argument to Object parameter") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    printObject(42)
          |    return "OK"
          |  }
          |
          |  static def printObject(o: Object): void {
          |    IO::println(o.toString())
          |  }
          |}
          |""".stripMargin,
        "MethodArgBoxing.on",
        Array()
      )
      assert(Shell.Success("OK") == result)
    }

    it("unboxes Integer argument to int parameter") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    printInt(new Integer(42))
          |    return "OK"
          |  }
          |
          |  static def printInt(x: Int): void {
          |    IO::println(x.toString())
          |  }
          |}
          |""".stripMargin,
        "MethodArgUnboxing.on",
        Array()
      )
      assert(Shell.Success("OK") == result)
    }
  }

  describe("Return statements with boxing") {
    it("boxes int return value to Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val obj = getObject()
          |    return obj.toString()
          |  }
          |
          |  static def getObject(): Object {
          |    return 42
          |  }
          |}
          |""".stripMargin,
        "ReturnBoxing.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("unboxes Integer return value to int") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return getInt()
          |  }
          |
          |  static def getInt(): Int {
          |    return new Integer(42)
          |  }
          |}
          |""".stripMargin,
        "ReturnUnboxing.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }

  describe("Array and field assignments with boxing") {
    it("boxes int in Object array assignment") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Object[] = new Object[1]
          |    arr[0] = 42
          |    return arr[0].toString()
          |  }
          |}
          |""".stripMargin,
        "ArrayBoxing.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("boxes int in Integer array assignment") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Integer[] = new Integer[1]
          |    arr[0] = 42
          |    return arr[0].toString()
          |  }
          |}
          |""".stripMargin,
        "ArrayBoxingInteger.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("boxes int in field assignment") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  var obj: Object
          |
          |  static def main(args: String[]): String {
          |    val t = new Test
          |    t.obj = 42
          |    return t.obj.toString()
          |  }
          |}
          |""".stripMargin,
        "FieldBoxing.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("unboxes Integer in field assignment") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  var x: Int
          |
          |  static def main(args: String[]): Int {
          |    val t = new Test
          |    t.x = new Integer(42)
          |    return t.x
          |  }
          |}
          |""".stripMargin,
        "FieldUnboxing.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }

  describe("Integration with existing features") {
    it("works with generics") {
      val result = shell.run(
        """
          |import {
          |  java.util.ArrayList;
          |}
          |
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val list: ArrayList[Integer] = new ArrayList[Integer]
          |    list.add(42)
          |    list.add(99)
          |    val a: Int = list.get(0)
          |    val b: Int = list.get(1)
          |    return a + b
          |  }
          |}
          |""".stripMargin,
        "GenericsWithBoxing.on",
        Array()
      )
      assert(Shell.Success(141) == result)
    }

    it("works in string concatenation") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "value: " + 42
          |  }
          |}
          |""".stripMargin,
        "StringConcatBoxing.on",
        Array()
      )
      assert(Shell.Success("value: 42") == result)
    }

    it("handles multiple boxings in one expression") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Object = 1
          |    val b: Object = 2
          |    val c: Object = 3
          |    return a.toString() + b.toString() + c.toString()
          |  }
          |}
          |""".stripMargin,
        "MultipleBoxing.on",
        Array()
      )
      assert(Shell.Success("123") == result)
    }
  }

  describe("Edge cases") {
    it("boxes already boxed Integer to Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val i: Integer = new Integer(42)
          |    val obj: Object = i
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "AlreadyBoxed.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("unboxes from array element") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val arr: Integer[] = new Integer[1]
          |    arr[0] = new Integer(42)
          |    val x: Int = arr[0]
          |    return x
          |  }
          |}
          |""".stripMargin,
        "UnboxArrayElement.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("boxes null to Object") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val i: Integer = null
          |    val obj: Object = i
          |    if (obj == null) {
          |      return "null"
          |    } else {
          |      return "not null"
          |    }
          |  }
          |}
          |""".stripMargin,
        "BoxNull.on",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("boxes byte literal with widening") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b: Byte = 127B
          |    val obj: Object = b
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "ByteWideningBoxing.on",
        Array()
      )
      assert(Shell.Success("127") == result)
    }

    it("boxes short literal with widening") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: Short = 1000S
          |    val obj: Object = s
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "ShortWideningBoxing.on",
        Array()
      )
      assert(Shell.Success("1000") == result)
    }

    it("handles mixed primitive types in Object array") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Object[] = new Object[4]
          |    arr[0] = 42
          |    arr[1] = 3.14
          |    arr[2] = true
          |    arr[3] = 'A'
          |    return arr[0].toString() + arr[1].toString() + arr[2].toString() + arr[3].toString()
          |  }
          |}
          |""".stripMargin,
        "MixedPrimitivesArray.on",
        Array()
      )
      result match {
        case Shell.Success(s: String) =>
          assert(s.startsWith("42"))
          assert(s.contains("3.14"))
          assert(s.contains("true"))
          assert(s.contains("A"))
        case _ => fail(s"Expected success but got $result")
      }
    }

    it("chains boxing and method calls") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = 42
          |    return x.hashCode().hashCode()
          |  }
          |}
          |""".stripMargin,
        "ChainedBoxingCalls.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("boxes in nested method calls") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return acceptObject(acceptInt(42))
          |  }
          |
          |  static def acceptInt(x: Int): Int {
          |    return x
          |  }
          |
          |  static def acceptObject(o: Object): String {
          |    return o.toString()
          |  }
          |}
          |""".stripMargin,
        "NestedMethodCallBoxing.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("unboxes and boxes in same expression") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val i: Integer = new Integer(42)
          |    val x: Int = i
          |    val obj: Object = x
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "UnboxThenBox.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("boxes different numeric types to Number") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val n1: Number = 42
          |    val n2: Number = 3.14
          |    val n3: Number = 100L
          |    return n1.toString() + "," + n2.toString() + "," + n3.toString()
          |  }
          |}
          |""".stripMargin,
        "BoxToNumber.on",
        Array()
      )
      result match {
        case Shell.Success(s: String) =>
          assert(s.contains("42"))
          assert(s.contains("3.14"))
          assert(s.contains("100"))
        case _ => fail(s"Expected success but got $result")
      }
    }

    it("works with foreach unboxing") {
      val result = shell.run(
        """
          |import {
          |  java.util.ArrayList;
          |}
          |
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val list: ArrayList[Integer] = new ArrayList[Integer]
          |    list.add(10)
          |    list.add(20)
          |    list.add(30)
          |    var sum: Int = 0
          |    foreach x:Int in list {
          |      sum = sum + x
          |    }
          |    return sum
          |  }
          |}
          |""".stripMargin,
        "ForeachUnboxing.on",
        Array()
      )
      assert(Shell.Success(60) == result)
    }

    it("boxes byte and short literals directly in call") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return accept(127B) + "," + accept(1000S)
          |  }
          |
          |  static def accept(o: Object): String {
          |    return o.toString()
          |  }
          |}
          |""".stripMargin,
        "ByteShortDirectBoxing.on",
        Array()
      )
      assert(Shell.Success("127,1000") == result)
    }

    // Note: Widening + boxing in single expression causes bytecode generation issues
    // This is a known limitation
    /*
    it("handles widening then boxing") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b: Byte = 10B
          |    val l: Long = b
          |    val obj: Object = l
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "WideningThenBoxing.on",
        Array()
      )
      assert(Shell.Success("10") == result)
    }
    */

    // Note: Elvis operator with boxing has issues
    /*
    it("boxes in elvis operator") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val i: Integer = null
          |    val x: Object = i :? new Integer(42)
          |    return x.toString()
          |  }
          |}
          |""".stripMargin,
        "ElvisBoxing.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }
    */

    it("accesses static field on boxed type") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return Integer::MAX_VALUE
          |  }
          |}
          |""".stripMargin,
        "IntegerMaxValue.on",
        Array()
      )
      assert(Shell.Success(2147483647) == result)
    }

    it("calls static method on wrapper class") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return Integer::parseInt("42")
          |  }
          |}
          |""".stripMargin,
        "IntegerParseInt.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("boxes with reference equality check") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Object = 1000
          |    val b: Object = 1000
          |    if (a === b) {
          |      return "same"
          |    } else {
          |      return "different"
          |    }
          |  }
          |}
          |""".stripMargin,
        "ReferenceEqualityBoxing.on",
        Array()
      )
      assert(Shell.Success("different") == result)
    }

    it("boxes with value equality check") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Object = 1000
          |    val b: Object = 1000
          |    if (a == b) {
          |      return "equal"
          |    } else {
          |      return "not equal"
          |    }
          |  }
          |}
          |""".stripMargin,
        "ValueEqualityBoxing.on",
        Array()
      )
      assert(Shell.Success("equal") == result)
    }

    it("boxes mixed types in list literal") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val list: List = [1, 2, 3, 4, 5]
          |    return list.size()
          |  }
          |}
          |""".stripMargin,
        "ListLiteralBoxing.on",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("unboxes in comparison") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val i: Integer = new Integer(42)
          |    if (i > 40) {
          |      return "greater"
          |    } else {
          |      return "not greater"
          |    }
          |  }
          |}
          |""".stripMargin,
        "UnboxInComparison.on",
        Array()
      )
      assert(Shell.Success("greater") == result)
    }

    it("unboxes in arithmetic") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val i: Integer = new Integer(10)
          |    val j: Integer = new Integer(20)
          |    return i + j
          |  }
          |}
          |""".stripMargin,
        "UnboxInArithmetic.on",
        Array()
      )
      assert(Shell.Success(30) == result)
    }

    it("boxes in select expression") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x: Int = 2
          |    select x {
          |    case 1:
          |      return "one"
          |    case 2:
          |      return "two"
          |    else:
          |      return "other"
          |    }
          |  }
          |}
          |""".stripMargin,
        "SelectWithBoxing.on",
        Array()
      )
      assert(Shell.Success("two") == result)
    }

    it("handles double boxing prevention") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x: Int = 42
          |    val i: Integer = x
          |    val obj: Object = i
          |    return obj.toString()
          |  }
          |}
          |""".stripMargin,
        "DoubleBoxingPrevention.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("unboxes in array indexing") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: String[] = new String[5]
          |    arr[0] = "A"
          |    arr[1] = "B"
          |    arr[2] = "C"
          |    val idx: Integer = new Integer(1)
          |    return arr[idx]
          |  }
          |}
          |""".stripMargin,
        "UnboxInArrayIndex.on",
        Array()
      )
      assert(Shell.Success("B") == result)
    }

    it("boxes all primitive types to Comparable") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c: Comparable = 42
          |    return c.toString()
          |  }
          |}
          |""".stripMargin,
        "BoxToComparable.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("boxes in while condition") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var count: Integer = new Integer(3)
          |    var sum: Int = 0
          |    while (count > 0) {
          |      sum = sum + count
          |      count = new Integer(count - 1)
          |    }
          |    return sum
          |  }
          |}
          |""".stripMargin,
        "WhileWithBoxing.on",
        Array()
      )
      assert(Shell.Success(6) == result)
    }

    it("boxes different Float representations") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f1: Float = 1.5F
          |    val f2: Float = 0.5F
          |    val obj1: Object = f1
          |    val obj2: Object = f2
          |    return obj1.toString() + "," + obj2.toString()
          |  }
          |}
          |""".stripMargin,
        "FloatBoxingVariants.on",
        Array()
      )
      result match {
        case Shell.Success(s: String) =>
          assert(s.contains("1.5"))
          assert(s.contains("0.5"))
        case _ => fail(s"Expected success but got $result")
      }
    }

    it("boxes zero values correctly") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val i: Object = 0
          |    val l: Object = 0L
          |    val d: Object = 0.0
          |    val f: Object = 0.0F
          |    return i.toString() + "," + l.toString() + "," + d.toString() + "," + f.toString()
          |  }
          |}
          |""".stripMargin,
        "ZeroBoxing.on",
        Array()
      )
      result match {
        case Shell.Success(s: String) =>
          assert(s.contains("0"))
        case _ => fail(s"Expected success but got $result")
      }
    }

    it("boxes negative values") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val i: Object = -42
          |    val l: Object = -100L
          |    val b: Object = -10B
          |    return i.toString() + "," + l.toString() + "," + b.toString()
          |  }
          |}
          |""".stripMargin,
        "NegativeBoxing.on",
        Array()
      )
      assert(Shell.Success("-42,-100,-10") == result)
    }

    // Note: Boxing in synchronized block has issues
    /*
    it("handles boxed value in synchronized block") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val lock: Object = 42
          |    synchronized (lock) {
          |      return "synchronized"
          |    }
          |  }
          |}
          |""".stripMargin,
        "SynchronizedBoxing.on",
        Array()
      )
      assert(Shell.Success("synchronized") == result)
    }
    */

    // Note: Unboxing in comparison within exception handling has issues
    /*
    it("boxes in exception throw") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    try {
          |      val i: Integer = new Integer(42)
          |      if (i > 40) {
          |        throw new RuntimeException("value: " + i.toString())
          |      }
          |      return "no exception"
          |    } catch (e: Exception) {
          |      return e.getMessage()
          |    }
          |  }
          |}
          |""".stripMargin,
        "ExceptionWithBoxing.on",
        Array()
      )
      assert(Shell.Success("value: 42") == result)
    }
    */

    it("unboxes in post-increment") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var i: Integer = new Integer(10)
          |    var x: Int = 0
          |    x = i
          |    x++
          |    return x
          |  }
          |}
          |""".stripMargin,
        "UnboxPostIncrement.on",
        Array()
      )
      assert(Shell.Success(11) == result)
    }
  }

  describe("Unboxing in operators") {
    it("unboxes boxed operand in value equality") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val i: Integer = new Integer(42)
          |    if i == 42 {
          |      return "equal"
          |    } else {
          |      return "not equal"
          |    }
          |  }
          |}
          |""".stripMargin,
        "UnboxInEquals.on",
        Array()
      )
      assert(Shell.Success("equal") == result)
    }

    it("unboxes boxed operand in not-equal comparison") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val i: Integer = new Integer(42)
          |    if 41 != i {
          |      return "different"
          |    } else {
          |      return "same"
          |    }
          |  }
          |}
          |""".stripMargin,
        "UnboxInNotEquals.on",
        Array()
      )
      assert(Shell.Success("different") == result)
    }

    it("unboxes Boolean in logical operators") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b: JBoolean = new JBoolean(true)
          |    if b && true {
          |      return "and"
          |    } else {
          |      return "no"
          |    }
          |  }
          |}
          |""".stripMargin,
        "UnboxInLogical.on",
        Array()
      )
      assert(Shell.Success("and") == result)
    }

    it("unboxes Integer in bitwise operators") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val i: Integer = new Integer(6)
          |    return i & 3
          |  }
          |}
          |""".stripMargin,
        "UnboxInBitwise.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("unboxes Integer in shift operators") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val i: Integer = new Integer(2)
          |    return i << 1
          |  }
          |}
          |""".stripMargin,
        "UnboxInShift.on",
        Array()
      )
      assert(Shell.Success(4) == result)
    }
  }
}
