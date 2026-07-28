package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the whole standard-library reference (issue #449).
 *
 * `AssertStdlibDocSpec` covers `Assert` specifically, after every documented name on
 * that class turned out to be wrong. The same rot can happen to any of the ~30 stdlib
 * classes, so this checks all of them: every `Class::method` written in
 * `docs/reference/stdlib.md` and its Japanese copy must exist as a public member of
 * the corresponding `onion.*` class.
 *
 * Two categories are deliberately excluded, because they are not `onion.*` members and
 * a naive scan reports them as missing (which is exactly the false positive that made
 * the real `Assert` breakage hard to spot):
 *
 *   - **import aliases** — `JInteger`, `JLong`, … name `java.lang` types
 *   - **JDK classes** reachable by default (`Math`, `System`, `Thread`, …)
 *
 * Methods synthesized on a user's own record by `derive!` (`toYaml`, `fromJson`, …) are
 * documented on the *user's* type, not on an `onion.*` class, so they never reach the
 * check.
 */
class StdlibDocDriftSpec extends AnyFunSpec {

  /** Names in `Class::member` position that are not `onion.*` standard-library types. */
  private val notStdlibClasses = Set(
    // import aliases for java.lang wrappers
    "JInteger", "JLong", "JDouble", "JFloat", "JShort", "JByte", "JBoolean", "JCharacter",
    "JString", "JObject",
    // JDK types usable without an onion.* counterpart
    "Math", "System", "Thread", "Runtime", "Integer", "Long", "Double", "Float",
    "Short", "Byte", "Boolean", "Character", "String", "Object", "Class", "Arrays",
    "Collections", "Objects", "Optional", "Pattern", "Matcher", "StringBuilder",
    "LocalDate", "LocalDateTime", "Duration", "Instant", "BigInteger", "BigDecimal",
    // record types defined by the examples themselves; `derive!` synthesizes
    // `toJson`/`fromYaml`/… onto the USER's type, not onto an onion.* class
    "ServerConfig", "User", "Point", "Person", "Pt"
  )

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  /** `Class::member` occurrences, restricted to classes that exist under `onion.`. */
  private def documentedCalls(doc: String): Set[(String, String)] =
    """\b([A-Z][A-Za-z0-9]*)::([a-zA-Z][A-Za-z0-9_]*)""".r
      .findAllMatchIn(doc)
      .map(m => (m.group(1), m.group(2)))
      .filterNot { case (cls, _) => notStdlibClasses.contains(cls) }
      .toSet

  /** Public member names (methods and fields) of `onion.<name>`, or None if absent. */
  private def membersOf(simpleName: String): Option[Set[String]] =
    try {
      val c = Class.forName(s"onion.$simpleName")
      Some(c.getMethods.map(_.getName).toSet ++ c.getFields.map(_.getName).toSet)
    } catch {
      case _: ClassNotFoundException => None
    }

  private def check(path: String): Unit = {
    val calls = documentedCalls(read(path))
    assert(calls.nonEmpty, s"no Class::method occurrences found in $path — the scan has rotted")

    val unknownClasses = calls.map(_._1).filter(membersOf(_).isEmpty)
    assert(unknownClasses.isEmpty,
      s"$path documents these as `Class::…` but no `onion.<Class>` exists (add them to " +
      s"notStdlibClasses if they are aliases or JDK types): ${unknownClasses.toSeq.sorted.mkString(", ")}")

    val missing = calls.collect {
      case (cls, m) if membersOf(cls).exists(!_.contains(m)) => s"$cls::$m"
    }
    assert(missing.isEmpty,
      s"$path documents members that do not exist: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("every stdlib call documented in the English reference exists") {
    check("docs/reference/stdlib.md")
  }

  it("every stdlib call documented in the Japanese reference exists") {
    check("docs/ja/reference/stdlib.md")
  }

  it("CLAUDE.md's stdlib summary names real classes") {
    // CLAUDE.md lists the modules in prose rather than as `Class::method`, so only the
    // class names are checkable — which is what went wrong there for Assert.
    // Only the "Standard Library" bullet list — the surrounding sections list compiler
    // internals (BasicType, ClassType, …), which are not onion.* stdlib classes.
    val lines = read("CLAUDE.md").linesIterator.toSeq
    val start = lines.indexWhere(_.contains("**Standard Library**"))
    assert(start >= 0, "CLAUDE.md has no Standard Library section — the scan has rotted")
    val section = lines.drop(start + 1).takeWhile(_.trim.startsWith("- `"))
    assert(section.nonEmpty, "the Standard Library section listed no modules")
    val named = section.flatMap("""- `([A-Z][A-Za-z0-9]*)`""".r.findFirstMatchIn(_).map(_.group(1)))
    val unknown = named.filter(n => !notStdlibClasses.contains(n) && membersOf(n).isEmpty)
    assert(unknown.isEmpty, s"CLAUDE.md names stdlib modules that do not exist: ${unknown.mkString(", ")}")
  }
}
