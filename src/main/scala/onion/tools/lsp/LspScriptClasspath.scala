package onion.tools.lsp

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.{ConcurrentHashMap, ExecutorService, Executors, ThreadFactory}

import scala.util.control.NonFatal

import onion.tools.{ScriptDependencies, ScriptDirectives}

/**
 * The jars a standalone script's `//> using dep` directives add to its validation
 * classpath, resolved through the same parser, resolver and on-disk cache as `onion
 * script.on` and `onionc` ([[ScriptDependencies]]), so the editor sees what a run sees.
 *
 * Without this the editor validated such a script against `.` alone and underlined every
 * type the dependency provides, while `onion script.on` ran it happily.
 *
 * Resolution never runs on the caller's thread. [[lookup]] answers from memory or from the
 * on-disk cache (one small file and a few `stat`s); anything colder is handed to a single
 * background thread, the caller is told the set is [[LspScriptClasspath.Lookup.Pending]],
 * and `onSettled` is called with the directive set's key once the answer is in, so the
 * documents waiting on it can be validated again. A set no open document still declares by
 * the time its turn comes is skipped rather than resolved: typing a coordinate produces a
 * new directive set on every keystroke, and only the last one matters.
 *
 * @param wanted    whether any open document still declares the directive set with this key
 * @param onSettled called on the background thread when a set's resolution has finished
 *                  (either way)
 */
final class LspScriptClasspath(
  wanted: String => Boolean,
  onSettled: String => Unit,
  cacheDir: () => Option[Path] = () => ScriptDependencies.cacheDirectory(),
  resolver: (ScriptDirectives.Directives, Option[Path]) => Either[String, Seq[String]] =
    (directives, dir) => ScriptDependencies.resolve(directives, dir, None)
) {
  import LspScriptClasspath.*

  private sealed trait State
  private case object Resolving extends State
  private final case class Resolved(jars: Seq[String]) extends State
  private final case class Failed(message: String) extends State

  /** Per directive set (by [[ScriptDependencies.cacheKey]]), for the life of the server. */
  private val states = new ConcurrentHashMap[String, State]()

  private lazy val executor: ExecutorService =
    Executors.newSingleThreadExecutor(new ThreadFactory {
      override def newThread(task: Runnable): Thread = {
        val thread = new Thread(task, "onion-lsp-script-dependencies")
        thread.setDaemon(true)
        thread
      }
    })

  def keyOf(directives: ScriptDirectives.Directives): String = ScriptDependencies.cacheKey(directives)

  /** What the directive set adds to a document's classpath right now. Never blocks on resolution. */
  def lookup(directives: ScriptDirectives.Directives): Lookup = {
    val key = keyOf(directives)
    states.get(key) match {
      case Resolved(jars) if jars.forall(jar => Files.isRegularFile(Paths.get(jar))) => Lookup.Ready(jars)
      case Resolved(_) =>
        // A jar was deleted under us (a cleared coursier cache, say): resolve again.
        states.remove(key)
        lookup(directives)
      case Failed(message) => Lookup.Failed(message)
      case Resolving => Lookup.Pending
      case null =>
        ScriptDependencies.cached(directives, cacheDir()) match {
          case Some(jars) =>
            states.put(key, Resolved(jars))
            Lookup.Ready(jars)
          case None =>
            if (states.putIfAbsent(key, Resolving) == null) executor.execute(() => resolve(key, directives))
            Lookup.Pending
        }
    }
  }

  /**
   * Forgets a failed resolution of this set, so the next [[lookup]] tries again. A failure
   * is otherwise kept for the life of the server, so a coordinate that does not exist is not
   * looked up on every keystroke; saving the file is the signal to retry (the network may
   * be back, or the artifact published since).
   */
  def retryIfFailed(directives: ScriptDirectives.Directives): Unit = {
    val key = keyOf(directives)
    states.get(key) match {
      case failed: Failed => states.remove(key, failed)
      case _ => ()
    }
  }

  private def resolve(key: String, directives: ScriptDirectives.Directives): Unit = {
    if (!wanted(key)) {
      // Nobody declares it any more; forget it so that a document that comes back to it
      // starts a fresh resolution.
      states.remove(key, Resolving)
    } else {
      val outcome =
        try resolver(directives, cacheDir()) match {
          case Right(jars) => Resolved(jars)
          case Left(message) => Failed(message)
        }
        catch { case NonFatal(e) => Failed(s"${e.getClass.getSimpleName}: ${e.getMessage}") }
      outcome match {
        case Failed(message) => warn(s"could not resolve `//> using` dependencies: $message")
        case _ => ()
      }
      states.put(key, outcome)
      try onSettled(key)
      catch { case NonFatal(e) => warn(s"revalidation after dependency resolution failed: $e") }
    }
  }

  private def warn(message: String): Unit =
    // stdout is the LSP wire protocol; stderr is the server's log, as for LspProjectClasspath.
    System.err.println(s"[onion-lsp] $message")
}

object LspScriptClasspath {

  /** The answer to [[LspScriptClasspath.lookup]]. */
  enum Lookup {
    /** Resolved: these jars join the classpath. */
    case Ready(jars: Seq[String])
    /** Resolution is running in the background; `onSettled` will follow. */
    case Pending
    /** Resolution failed, with the resolver's message (the one `onion script.on` prints). */
    case Failed(message: String)
  }
}
