package onion.compiler.tools

import onion.tools.Shell

/**
 * An empty list literal (`[]`) passed as a generic constructor argument whose
 * formal type is `List[T]` (or `List[G[T]]` for some other generic `G`) used
 * to be typed with no expected type before the constructor was resolved, so
 * its element type defaulted to `Object`. `List[Object]` is not assignable to
 * `List[Int]` under invariant generics, so the constructor was rejected as
 * not found (`E0021`) even though a matching constructor plainly exists --
 * `new Simple[Int](1, [])` failed while the equivalent non-empty-list call
 * and the equivalent plain generic function call both worked.
 *
 * Ordinary method/static/unqualified calls already retype a malleable
 * argument (a collection literal, or a generic static/unqualified call)
 * against the resolved candidate's expected parameter type when the eager
 * resolution fails (`ArgumentExpectedTypeRetyping`, issue #232). Constructor
 * resolution (`ConstructionTyping.typeNewObject`) did not use that fallback
 * at all; this suite pins the constructor case with its own retyping path.
 */
class GenericConstructorEmptyListArgSpec extends AbstractShellSpec {

  describe("an empty list literal argument to a generic constructor") {
    it("resolves List[T] against the constructor's own type parameter") {
      val result = shell.run(
        """
          |record Simple[T](value: T, items: List[T])
          |def main(args: String[]): Int {
          |  val s: Simple[Int] = new Simple[Int](1, [])
          |  return s.value() + s.items().size
          |}
          |""".stripMargin,
        "EmptyListCtorSimple.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("resolves List[T] when the type argument is inferred from the target type") {
      val result = shell.run(
        """
          |record Simple[T](value: T, items: List[T])
          |def main(args: String[]): Int {
          |  val s: Simple[Int] = new Simple(1, [])
          |  return s.value() + s.items().size
          |}
          |""".stripMargin,
        "EmptyListCtorInferred.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("resolves List[G[T]] for another generic record G") {
      val result = shell.run(
        """
          |record Box[T](value: T)
          |record Wrapper[T](value: T, items: List[Box[T]])
          |def main(args: String[]): Int {
          |  val w: Wrapper[Int] = new Wrapper[Int](1, [])
          |  return w.value() + w.items().size
          |}
          |""".stripMargin,
        "EmptyListCtorNestedGeneric.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("resolves a self-referential List[Tree[T]] component") {
      val result = shell.run(
        """
          |record Tree[T](value: T, children: List[Tree[T]])
          |def main(args: String[]): Int {
          |  val t: Tree[Int] = new Tree[Int](1, [])
          |  return t.value() + t.children().size
          |}
          |""".stripMargin,
        "EmptyListCtorSelfReferential.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }

  describe("an empty collection literal whose type parameter appears only inside the collection argument") {
    // The constructor's substitution-blind exact match (ConstructorFinder /
    // StandardParameterMatcher) special-cases an empty list/map literal to accept
    // ANY parameterization of the same raw collection type
    // (TypeRules.emptyCollectionLiteralAccepts), so it can match List[K]/Map[K, V]
    // against the eagerly-typed List[Object]/Map[Object, Object] even when K is
    // never pinned by another argument. That eager "match" used to short-circuit
    // the retyping fallback above (only triggered when nothing matched at all),
    // so the constructor's own substitution-validity guard then rejected the
    // still-Object-typed argument with a spurious not-found error, even though
    // `new Simple[String](1, [])` / `new Simple[String](1, [:])` are exactly the
    // shape the retyping fallback exists to handle.
    it("resolves List[K] when no other argument pins K, with an explicit type argument") {
      val result = shell.run(
        """
          |record Simple[K](count: Int, entries: List[K])
          |def main(args: String[]): Int {
          |  val s: Simple[String] = new Simple[String](1, [])
          |  return s.count() + s.entries().size
          |}
          |""".stripMargin,
        "EmptyListCtorSoleTypeParamExplicit.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("resolves List[K] when no other argument pins K, with the type argument inferred") {
      val result = shell.run(
        """
          |record Simple[K](count: Int, entries: List[K])
          |def main(args: String[]): Int {
          |  val s: Simple[String] = new Simple(1, [])
          |  return s.count() + s.entries().size
          |}
          |""".stripMargin,
        "EmptyListCtorSoleTypeParamInferred.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("resolves Map[K, V] when no other argument pins K/V, with an explicit type argument") {
      val result = shell.run(
        """
          |record Simple[K, V](count: Int, entries: Map[K, V])
          |def main(args: String[]): Int {
          |  val s: Simple[String, Int] = new Simple[String, Int](1, [:])
          |  return s.count() + s.entries().size
          |}
          |""".stripMargin,
        "EmptyMapCtorSoleTypeParamExplicit.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("resolves Map[K, V] when no other argument pins K/V, with the type argument inferred") {
      val result = shell.run(
        """
          |record Simple[K, V](count: Int, entries: Map[K, V])
          |def main(args: String[]): Int {
          |  val s: Simple[String, Int] = new Simple(1, [:])
          |  return s.count() + s.entries().size
          |}
          |""".stripMargin,
        "EmptyMapCtorSoleTypeParamInferred.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("still reports a genuine constructor-not-found error for this shape") {
      val result = shell.run(
        """
          |record Simple[K](count: Int, entries: List[K])
          |def main(args: String[]): Int {
          |  val s: Simple[String] = new Simple[String](1, [], "extra")
          |  return s.count()
          |}
          |""".stripMargin,
        "EmptyListCtorSoleTypeParamGenuineNotFound.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }

  describe("existing constructor resolution is preserved") {
    it("still resolves a non-empty list argument") {
      val result = shell.run(
        """
          |record Simple[T](value: T, items: List[T])
          |def main(args: String[]): Int {
          |  val s: Simple[Int] = new Simple[Int](1, [7, 8])
          |  return s.value() + s.items().size
          |}
          |""".stripMargin,
        "NonEmptyListCtorPreserved.on",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("still reports a genuine constructor-not-found error") {
      val result = shell.run(
        """
          |record Simple[T](value: T, items: List[T])
          |def main(args: String[]): Int {
          |  val s: Simple[Int] = new Simple[Int](1, [], "extra")
          |  return s.value()
          |}
          |""".stripMargin,
        "GenuineNotFoundPreserved.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
