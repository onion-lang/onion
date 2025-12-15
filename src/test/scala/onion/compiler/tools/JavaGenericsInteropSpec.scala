package onion.compiler.tools

import onion.tools.Shell

class JavaGenericsInteropSpec extends AbstractShellSpec {
  describe("Java generics interop") {
    it("accepts type arguments on Java generic classes") {
      val result = shell.run(
        """
          |class JavaList {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("ok")
          |    return list.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaList.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("handles Java generic methods") {
      val result = shell.run(
        """
          |class JavaGenericMethod {
          |public:
          |  static def main(args: String[]): String {
          |    val list: List[String] = Collections::singletonList[String]("x")
          |    return list.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaGenericMethod.on",
        Array()
      )
      assert(Shell.Success("x") == result)
    }

    it("handles multiple type arguments") {
      val result = shell.run(
        """
          |class JavaMap {
          |public:
          |  static def main(args: String[]): String {
          |    val map: HashMap[String, String] = new HashMap[String, String]
          |    map.put("k", "v")
          |    return map.get("k")
          |  }
          |}
          |""".stripMargin,
        "JavaMap.on",
        Array()
      )
      assert(Shell.Success("v") == result)
    }

    it("preserves generic supertypes for applied Java classes") {
      val result = shell.run(
        """
          |class JavaListAsSuper {
          |public:
          |  static def main(args: String[]): String {
          |    val impl: ArrayList[String] = new ArrayList[String]
          |    impl.add("ok")
          |    val asList: List[String] = impl
          |    return asList.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaListAsSuper.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("supports Collections.copy with wildcards") {
      val result = shell.run(
        """
          |class JavaCollectionsCopy {
          |public:
          |  static def main(args: String[]): String {
          |    val src: ArrayList[String] = new ArrayList[String]
          |    src.add("x")
          |    val dest: ArrayList[Object] = new ArrayList[Object]
          |    dest.add("y")
          |
          |    Collections::copy[String](dest, src)
          |    return "" + dest.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsCopy.on",
        Array()
      )
      assert(Shell.Success("x") == result)
    }

    it("supports Collections.sort with inferred type arguments") {
      val result = shell.run(
        """
          |class JavaCollectionsSort {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("b")
          |    list.add("a")
          |    Collections::sort(list)
          |    return list.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsSort.on",
        Array()
      )
      assert(Shell.Success("a") == result)
    }

    it("supports Collections.max") {
      val result = shell.run(
        """
          |class JavaCollectionsMax {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("b")
          |    list.add("a")
          |    return Collections::max(list)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsMax.on",
        Array()
      )
      assert(Shell.Success("b") == result)
    }

    it("supports Collections.binarySearch") {
      val result = shell.run(
        """
          |class JavaCollectionsBinarySearch {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("b")
          |    list.add("a")
          |    Collections::sort(list)
          |    val idx: Int = Collections::binarySearch(list, "b")
          |    return "" + idx
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsBinarySearch.on",
        Array()
      )
      assert(Shell.Success("1") == result)
    }

    it("supports Collections.reverse with wildcard parameter") {
      val result = shell.run(
        """
          |class JavaCollectionsReverse {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("a")
          |    list.add("b")
          |    Collections::reverse(list)
          |    return list.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsReverse.on",
        Array()
      )
      assert(Shell.Success("b") == result)
    }

    it("supports Collections.unmodifiableList") {
      val result = shell.run(
        """
          |class JavaCollectionsUnmodifiableList {
          |public:
          |  static def main(args: String[]): String {
          |    val src: ArrayList[String] = new ArrayList[String]
          |    src.add("ok")
          |    val v: List[String] = Collections::unmodifiableList(src)
          |    return v.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsUnmodifiableList.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("supports Collections.min") {
      val result = shell.run(
        """
          |class JavaCollectionsMin {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("b")
          |    list.add("a")
          |    return Collections::min(list)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsMin.on",
        Array()
      )
      assert(Shell.Success("a") == result)
    }

    it("supports Collections.swap") {
      val result = shell.run(
        """
          |class JavaCollectionsSwap {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("a")
          |    list.add("b")
          |    Collections::swap(list, 0, 1)
          |    return list.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsSwap.on",
        Array()
      )
      assert(Shell.Success("b") == result)
    }

    it("supports Collections.fill with lower-bounded wildcard") {
      val result = shell.run(
        """
          |class JavaCollectionsFill {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[Object] = new ArrayList[Object]
          |    list.add(new Object)
          |    list.add(new Object)
          |    Collections::fill(list, "x")
          |    return "" + list.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsFill.on",
        Array()
      )
      assert(Shell.Success("x") == result)
    }

    it("supports Collections.replaceAll") {
      val result = shell.run(
        """
          |class JavaCollectionsReplaceAll {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("a")
          |    list.add("b")
          |    Collections::replaceAll(list, "a", "x")
          |    return list.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsReplaceAll.on",
        Array()
      )
      assert(Shell.Success("x") == result)
    }

    it("supports Collections.frequency") {
      val result = shell.run(
        """
          |class JavaCollectionsFrequency {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("a")
          |    list.add("b")
          |    list.add("a")
          |    return "" + Collections::frequency(list, "a")
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsFrequency.on",
        Array()
      )
      assert(Shell.Success("2") == result)
    }

    it("supports Collections.disjoint") {
      val result = shell.run(
        """
          |class JavaCollectionsDisjoint {
          |public:
          |  static def main(args: String[]): String {
          |    val a: ArrayList[String] = new ArrayList[String]
          |    a.add("a")
          |    val b: ArrayList[String] = new ArrayList[String]
          |    b.add("b")
          |    b.add("a")
          |    return "" + Collections::disjoint(a, b)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsDisjoint.on",
        Array()
      )
      assert(Shell.Success("false") == result)
    }

    it("supports Collections.indexOfSubList") {
      val result = shell.run(
        """
          |class JavaCollectionsIndexOfSubList {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("a")
          |    list.add("b")
          |    list.add("c")
          |    val sub: ArrayList[String] = new ArrayList[String]
          |    sub.add("b")
          |    sub.add("c")
          |    return "" + Collections::indexOfSubList(list, sub)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsIndexOfSubList.on",
        Array()
      )
      assert(Shell.Success("1") == result)
    }

    it("supports Collections.lastIndexOfSubList") {
      val result = shell.run(
        """
          |class JavaCollectionsLastIndexOfSubList {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("a")
          |    list.add("b")
          |    list.add("c")
          |    list.add("b")
          |    list.add("c")
          |    val sub: ArrayList[String] = new ArrayList[String]
          |    sub.add("b")
          |    sub.add("c")
          |    return "" + Collections::lastIndexOfSubList(list, sub)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsLastIndexOfSubList.on",
        Array()
      )
      assert(Shell.Success("3") == result)
    }

    it("supports Collections.rotate") {
      val result = shell.run(
        """
          |class JavaCollectionsRotate {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("a")
          |    list.add("b")
          |    list.add("c")
          |    Collections::rotate(list, 1)
          |    return list.get(0) + list.get(1) + list.get(2)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsRotate.on",
        Array()
      )
      assert(Shell.Success("cab") == result)
    }

    it("supports Collections.synchronizedList") {
      val result = shell.run(
        """
          |class JavaCollectionsSynchronizedList {
          |public:
          |  static def main(args: String[]): String {
          |    val src: ArrayList[String] = new ArrayList[String]
          |    src.add("ok")
          |    val v: List[String] = Collections::synchronizedList(src)
          |    return v.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsSynchronizedList.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("supports Collections.emptyList") {
      val result = shell.run(
        """
          |class JavaCollectionsEmptyList {
          |public:
          |  static def main(args: String[]): String {
          |    val v: List[String] = Collections::emptyList[String]()
          |    return "" + v.isEmpty
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsEmptyList.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("infers type arguments for Collections.emptyList from the expected type") {
      val result = shell.run(
        """
          |class JavaCollectionsEmptyListInferred {
          |public:
          |  static def main(args: String[]): String {
          |    val v: List[String] = Collections::emptyList()
          |    return "" + v.isEmpty
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsEmptyListInferred.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("supports Collections.nCopies") {
      val result = shell.run(
        """
          |class JavaCollectionsNCopies {
          |public:
          |  static def main(args: String[]): String {
          |    val v: List[String] = Collections::nCopies(3, "x")
          |    return v.get(2)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsNCopies.on",
        Array()
      )
      assert(Shell.Success("x") == result)
    }

    it("supports Collections.singletonMap") {
      val result = shell.run(
        """
          |class JavaCollectionsSingletonMap {
          |public:
          |  static def main(args: String[]): String {
          |    val m: Map[String, String] = Collections::singletonMap("k", "v")
          |    return m.get("k")
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsSingletonMap.on",
        Array()
      )
      assert(Shell.Success("v") == result)
    }

    it("supports Collections.emptyMap with explicit type args") {
      val result = shell.run(
        """
          |class JavaCollectionsEmptyMap {
          |public:
          |  static def main(args: String[]): String {
          |    val m: Map[String, String] = Collections::emptyMap[String, String]()
          |    val v: String = m.get("missing")
          |    if v == null { return "null"; }
          |    return v
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsEmptyMap.on",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("infers type arguments for Collections.emptyMap from the expected type") {
      val result = shell.run(
        """
          |class JavaCollectionsEmptyMapInferred {
          |public:
          |  static def main(args: String[]): String {
          |    val m: Map[String, String] = Collections::emptyMap()
          |    val v: String = m.get("missing")
          |    if v == null { return "null"; }
          |    return v
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsEmptyMapInferred.on",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("supports Collections.enumeration and list") {
      val result = shell.run(
        """
          |class JavaCollectionsEnumeration {
          |public:
          |  static def main(args: String[]): String {
          |    val list: ArrayList[String] = new ArrayList[String]
          |    list.add("a")
          |    val e: Enumeration[String] = Collections::enumeration(list)
          |    val list2: ArrayList[String] = Collections::list(e)
          |    return list2.get(0)
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsEnumeration.on",
        Array()
      )
      assert(Shell.Success("a") == result)
    }

    it("supports Collections.newSetFromMap") {
      val result = shell.run(
        """
          |class JavaCollectionsNewSetFromMap {
          |public:
          |  static def main(args: String[]): String {
          |    val m: HashMap[String, JBoolean] = new HashMap[String, JBoolean]
          |    val s: Set[String] = Collections::newSetFromMap(m)
          |    s.add("x")
          |    return "" + s.contains("x")
          |  }
          |}
          |""".stripMargin,
        "JavaCollectionsNewSetFromMap.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }
  }
}
