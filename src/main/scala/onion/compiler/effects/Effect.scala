package onion.compiler.effects

/**
 * The effect vocabulary (issue #356). One value per way a call can touch the world
 * outside the program:
 *
 *   - `Read`    — reads the filesystem
 *   - `Write`   — writes the filesystem
 *   - `Net`     — talks to the network
 *   - `Exec`    — starts, replaces or terminates a process (includes `System::exit`)
 *   - `Env`     — reads process environment: env vars, system properties, cwd, JVM stats
 *   - `Clock`   — reads the clock, or blocks on it (sleep, timeouts)
 *   - `Rand`    — draws from a random source
 *   - `Console` — reads or writes the attached console (stdin/stdout/stderr)
 *   - `Unknown` — a call the effect table cannot vouch for
 *
 * One more value is *internal*: `Mutate` — writes to memory that outlives the call (a
 * field, an array element, a collection). It describes the program's own state, not the
 * outside world, so it is not a capability: a `tool` never declares it, and the
 * capability layer ignores it. It exists so that "pure" can mean what it says: a contract
 * expression must have no effects and must not write (RFC #2557).
 *
 * A pure call has the empty set. `Unknown` is deliberately an effect rather than an
 * absence: an unlisted Java method is *not known to be pure*, and pretending otherwise
 * would make every guarantee built on this vocabulary a lie. The capability layer
 * (#357) treats `Unknown` as "needs an explicit allow", not as "harmless".
 */
sealed abstract class Effect(val name: String) extends Ordered[Effect] {
  def compare(that: Effect): Int = name.compare(that.name)
  override def toString: String = name
}

object Effect {
  case object Read    extends Effect("read")
  case object Write   extends Effect("write")
  case object Net     extends Effect("net")
  case object Exec    extends Effect("exec")
  case object Env     extends Effect("env")
  case object Clock   extends Effect("clock")
  case object Rand    extends Effect("rand")
  case object Console extends Effect("console")
  case object Unknown extends Effect("unknown")
  case object Mutate  extends Effect("mutate")

  /** The capabilities a `tool` can declare in `requires { ... }`. Does not contain
   *  the internal effects. */
  val all: Seq[Effect] = Seq(Read, Write, Net, Exec, Env, Clock, Rand, Console, Unknown)

  /** Effects that describe the program's own memory. They show up in inference, the
   *  effect table and `--effects`, but are never declared and never checked. */
  val internal: Set[Effect] = Set(Mutate)

  /** Effects that can be tied to a parameter (`read(src)`); the rest are ambient. */
  val parameterized: Set[Effect] = Set(Read, Write, Net, Exec)

  private val byName: Map[String, Effect] = (all ++ internal).map(e => e.name -> e).toMap

  /** Every effect by name, internal ones included. The effect table uses this. */
  def parse(name: String): Option[Effect] = byName.get(name)

  /** A name a `tool` may write in `requires { ... }`: `parse` without the internal effects. */
  def parseCapability(name: String): Option[Effect] = parse(name).filterNot(internal.contains)

  /** Renders a set the way diagnostics and `--effects` print it: `pure` for the
   *  empty set, otherwise the names sorted and comma-joined (`read,write`). */
  def render(effects: Set[Effect]): String =
    if (effects.isEmpty) "pure" else effects.toSeq.sorted.map(_.name).mkString(",")
}
