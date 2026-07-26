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

  val all: Seq[Effect] = Seq(Read, Write, Net, Exec, Env, Clock, Rand, Console, Unknown)

  private val byName: Map[String, Effect] = all.map(e => e.name -> e).toMap

  def parse(name: String): Option[Effect] = byName.get(name)

  /** Renders a set the way diagnostics and `--effects` print it: `pure` for the
   *  empty set, otherwise the names sorted and comma-joined (`read,write`). */
  def render(effects: Set[Effect]): String =
    if (effects.isEmpty) "pure" else effects.toSeq.sorted.map(_.name).mkString(",")
}
