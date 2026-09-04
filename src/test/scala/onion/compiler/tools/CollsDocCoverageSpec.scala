package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Colls` module docs.
 *
 * `onion.Colls` is a large factory/pipeline module (`Colls::listOf`, `Colls::setOf`,
 * `Colls::chunked`, ...), but docs/reference/stdlib.md and docs/ja/reference/stdlib.md only
 * ever documented a handful of its members -- most Colls operations are meant to be called
 * as List/Map/Set extension methods (`xs.map { ... }`, not `Colls::map(xs, ...)`), so this
 * guard looks for each member name anywhere in the file rather than requiring the
 * `Colls::name` static-call spelling (contrast `OnionMathDocCoverageSpec`, whose module has
 * no such extension-method form).
 *
 * Arity-suffixed overload-resolution helpers (`listOf0`, `setOf3`, `mapOf1`,
 * `mutableSetOf2`, ...) exist only so the compiler can dispatch a call written as
 * `Colls::listOf(a, b, c)` -- Onion code never spells the suffixed name -- so they are
 * excluded from the guard; the varargs name they back (`listOf`, `setOf`, `mapOf`,
 * `mutableSetOf`) is still required.
 */
class CollsDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String, names: Set[String]): Set[String] =
    names.filter(n => s"\\b${java.util.regex.Pattern.quote(n)}\\b".r.findFirstIn(doc).isDefined)

  private val arityOverloadHelper = """^(?:listOf|setOf|mapOf|mutableSetOf)\d+$""".r

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Colls]
    val methodNames = c.getMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
      .filterNot(arityOverloadHelper.matches)
    methodNames.toSet
  }

  it("actual onion.Colls exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Colls found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Colls member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"), actualNames)
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Colls:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Colls member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"), actualNames)
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Colls:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Colls in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Colls"),
      "docs/reference/stdlib.md's overview table should mention Colls")
    assert(read("docs/ja/reference/stdlib.md").contains("Colls"),
      "docs/ja/reference/stdlib.md's overview table should mention Colls")
  }

  /**
   * `flatMap`/`bind`/`zip`/`groupBy` on `List` are real `onion.Colls` members (caught by
   * the two whole-file checks above only by accident, because `Option`/`Result`/`Future`/
   * `Outcome`/`Maps` happen to document their own same-named methods elsewhere in the
   * file). Pin their presence in the Colls section itself so that coincidence is not the
   * only thing standing between this file and a real gap.
   */
  private def collsSection(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toIndexedSeq
    val start = lines.indexWhere(_.contains(heading))
    assert(start >= 0, s"""could not find a "$heading" heading -- the scan has rotted""")
    val rest = lines.drop(start + 1)
    val end = rest.indexWhere(_.startsWith("## "))
    (if (end < 0) rest else rest.take(end)).mkString("\n")
  }

  private val listMonadAndPairingNames = Set("flatMap", "bind", "zip", "groupBy")

  it("docs/reference/stdlib.md's Colls Module section documents List flatMap/bind/zip/groupBy") {
    val doc = collsSection(read("docs/reference/stdlib.md"), "## Colls Module")
    val missing = listMonadAndPairingNames -- documentedNames(doc, listMonadAndPairingNames)
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md's Colls Module section is missing: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md's Colls section documents List flatMap/bind/zip/groupBy") {
    val doc = collsSection(read("docs/ja/reference/stdlib.md"), "## Colls モジュール")
    val missing = listMonadAndPairingNames -- documentedNames(doc, listMonadAndPairingNames)
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md's Colls section is missing: ${missing.toSeq.sorted.mkString(", ")}")
  }

  /**
   * `any`/`all`/`none`/`find`/`forEach`/`count`/`reverse`/`contains` are real List
   * extension methods backed by `onion.Colls` (same fallback mechanism as
   * `flatMap`/`bind`/`zip`/`groupBy` above), but the whole-file checks miss their absence
   * by accident too: `forEach`/`count`/`contains` are documented for `Option`/`Result`/
   * `Maps`/`Sets` elsewhere in the file, and `any`/`all`/`none`/`find`/`reverse` are common
   * enough English words to appear in unrelated prose. Pin their presence in the Colls
   * section itself, spelled as an actual List call, so neither coincidence hides a real gap.
   */
  private val listPredicateAndQueryNames = Set("any", "all", "none", "find", "forEach", "count", "reverse", "contains")

  private def documentedCalls(doc: String, names: Set[String]): Set[String] =
    names.filter(n => doc.contains(s"xs.$n(") || doc.contains(s".$n {"))

  it("docs/reference/stdlib.md's Colls Module section documents List any/all/none/find/forEach/count/reverse/contains") {
    val doc = collsSection(read("docs/reference/stdlib.md"), "## Colls Module")
    val missing = listPredicateAndQueryNames -- documentedCalls(doc, listPredicateAndQueryNames)
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md's Colls Module section is missing: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md's Colls section documents List any/all/none/find/forEach/count/reverse/contains") {
    val doc = collsSection(read("docs/ja/reference/stdlib.md"), "## Colls モジュール")
    val missing = listPredicateAndQueryNames -- documentedCalls(doc, listPredicateAndQueryNames)
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md's Colls section is missing: ${missing.toSeq.sorted.mkString(", ")}")
  }

  /**
   * `isEmpty(Collection)`, `size(Collection)`, `get(Map, K)` and `containsKey(Map, K)` are
   * real `onion.Colls` members, registered as builtin extensions the same as everything
   * else in this file -- but the whole-file checks above miss their absence entirely by
   * coincidence: `isEmpty`, `get` and `size` are common enough words to appear all over the
   * doc for unrelated reasons, and `containsKey` is shown once already, in the unrelated
   * `java.util.HashMap` interop example earlier in the file. None of the four is ever shown
   * as Colls-backed usage in the Colls Module section itself. Pin their presence there, so
   * this guard's "documents every onion.Colls member" claim is no longer only accidentally
   * true for these.
   */
  private val collectionAndMapUtilityNames = Set("isEmpty", "size", "get", "containsKey")

  private def documentedCollectionAndMapCalls(doc: String, names: Set[String]): Set[String] =
    names.filter(n => doc.contains(s"xs.$n(") || doc.contains(s"m.$n("))

  it("docs/reference/stdlib.md's Colls Module section documents isEmpty/size/get/containsKey") {
    val doc = collsSection(read("docs/reference/stdlib.md"), "## Colls Module")
    val missing = collectionAndMapUtilityNames -- documentedCollectionAndMapCalls(doc, collectionAndMapUtilityNames)
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md's Colls Module section is missing: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md's Colls section documents isEmpty/size/get/containsKey") {
    val doc = collsSection(read("docs/ja/reference/stdlib.md"), "## Colls モジュール")
    val missing = collectionAndMapUtilityNames -- documentedCollectionAndMapCalls(doc, collectionAndMapUtilityNames)
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md's Colls section is missing: ${missing.toSeq.sorted.mkString(", ")}")
  }

  /**
   * `contains(Collection<T>, T)` is, like `isEmpty`/`size`/`get`/`containsKey` above, a
   * one-line pass-through to the identically-named native instance method
   * (`Collection.contains(Object)`), which always wins over the `onion.Colls` extension --
   * but the shadowing-explanation paragraph right after the code block names only
   * "isEmpty, size, get and containsKey" ("these four calls"), omitting `contains` even
   * though it is shadowed the exact same way. Pin that `contains` is named in the
   * explanation itself (not merely shown as a call, which the whole-file check and the
   * `listPredicateAndQueryNames` guard above already pin) so this omission cannot recur.
   */
  it("docs/reference/stdlib.md's Colls Module section names contains in the native-shadowing explanation") {
    val doc = collsSection(read("docs/reference/stdlib.md"), "## Colls Module")
    assert(doc.contains("`contains`"),
      "docs/reference/stdlib.md's Colls Module section's shadowing explanation should name `contains` " +
        "alongside isEmpty/size/get/containsKey -- it is shadowed by Collection.contains(Object) the same way")
  }

  it("docs/ja/reference/stdlib.md's Colls section names contains in the native-shadowing explanation") {
    val doc = collsSection(read("docs/ja/reference/stdlib.md"), "## Colls モジュール")
    assert(doc.contains("`contains`"),
      "docs/ja/reference/stdlib.md's Colls section's shadowing explanation should name `contains` " +
        "alongside isEmpty/size/get/containsKey -- it is shadowed by Collection.contains(Object) the same way")
  }

  /**
   * `Colls::toList(array)` -- converting a Java array (e.g. `main(args: String[])`) into a
   * `List` -- is the one crossing point CLAUDE.md itself calls out ("Use Colls::toList(args)
   * to cross over"), but neither doc file ever showed the call.
   */
  it("docs/reference/stdlib.md documents Colls::toList(array)") {
    assert(read("docs/reference/stdlib.md").contains("Colls::toList("),
      "docs/reference/stdlib.md never shows Colls::toList(...)")
  }

  it("docs/ja/reference/stdlib.md documents Colls::toList(array)") {
    assert(read("docs/ja/reference/stdlib.md").contains("Colls::toList("),
      "docs/ja/reference/stdlib.md never shows Colls::toList(...)")
  }
}
