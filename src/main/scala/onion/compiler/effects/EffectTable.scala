package onion.compiler.effects

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.StandardCharsets

/**
 * The out-of-band effect table (issue #356).
 *
 * Effects cannot ride on annotations here: the `Method` trait carries none, nothing is
 * emitted to bytecode, and Java methods never reach the type checker with theirs (audit
 * §7.4). So the facts live in a resource — `onion/effect-table.txt` — same shipping
 * shape as `default-static-imports.txt`, and this object is its loader.
 *
 * Line format (after `#`/`//` comments and blanks are dropped):
 *
 * {{{
 *   fully.qualified.Class#method=effect[,effect...]   // one method
 *   fully.qualified.Class#*=effect[,effect...]        // every method of the class
 *   fully.qualified.Class#method=pure                 // explicitly no effects
 * }}}
 *
 * A specific `Class#method` entry beats the class's `Class#*` wildcard, which is how a
 * mostly-pure class declares its exceptions (`onion.DateTime#*=pure` + `#now=clock`).
 *
 * `lookup` returns `None` for anything the table does not cover. The caller decides
 * what that means: for a Java method it means [[Effect.Unknown]] (not *known* pure);
 * for an Onion-defined method it means "infer from the body" (see [[EffectInference]]).
 */
object EffectTable {
  private val ResourcePath = "onion/effect-table.txt"

  final case class Parsed(exact: Map[(String, String), Set[Effect]],
                          wildcard: Map[String, Set[Effect]])

  /** Parses table lines; malformed lines fail loudly — a silently dropped entry would
   *  turn into a silently wrong `unknown`/`pure` verdict downstream. */
  def parseLines(lines: Iterator[String]): Parsed = {
    var exact = Map.empty[(String, String), Set[Effect]]
    var wildcard = Map.empty[String, Set[Effect]]
    for ((raw, idx) <- lines.zipWithIndex) {
      val line = raw.trim
      if (line.nonEmpty && !line.startsWith("#") && !line.startsWith("//")) {
        val (spec, rhs) = line.split("=", 2) match {
          case Array(l, r) => (l.trim, r.trim)
          case _ => sys.error(s"$ResourcePath:${idx + 1}: missing '=': $line")
        }
        val (cls, method) = spec.split("#", 2) match {
          case Array(c, m) if c.nonEmpty && m.nonEmpty => (c, m)
          case _ => sys.error(s"$ResourcePath:${idx + 1}: expected Class#method: $line")
        }
        val effects: Set[Effect] =
          if (rhs == "pure") Set.empty
          else rhs.split(",").map(_.trim).map { n =>
            Effect.parse(n).getOrElse(
              sys.error(s"$ResourcePath:${idx + 1}: unknown effect '$n' (know: ${Effect.all.mkString(", ")})"))
          }.toSet
        if (method == "*") wildcard += (cls -> effects)
        else exact += ((cls, method) -> effects)
      }
    }
    Parsed(exact, wildcard)
  }

  private lazy val table: Parsed = {
    val loader = Option(Thread.currentThread.getContextClassLoader).getOrElse(getClass.getClassLoader)
    Option(loader.getResourceAsStream(ResourcePath)) match {
      case None => Parsed(Map.empty, Map.empty)  // degraded: everything Java-side is Unknown
      case Some(input) =>
        val reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
        try parseLines(Iterator.continually(reader.readLine()).takeWhile(_ != null))
        finally reader.close()
    }
  }

  /** The table's verdict for `className#methodName`, or `None` if it has none. */
  def lookup(className: String, methodName: String): Option[Set[Effect]] =
    table.exact.get((className, methodName)).orElse(table.wildcard.get(className))

  /** Convenience for the `Method`-trait default: unlisted means not-known-pure. */
  def effectsOrUnknown(className: String, methodName: String): Set[Effect] =
    lookup(className, methodName).getOrElse(Set(Effect.Unknown))
}
