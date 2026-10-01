package onion.tools

import java.io.{BufferedOutputStream, FileDescriptor, FileOutputStream, PrintStream}
import java.nio.charset.{Charset, StandardCharsets}

/**
 * The encoding Onion's launchers write stdout and stderr in.
 *
 * On Windows the JVM writes both streams in the ANSI code page (MS932 under a Japanese
 * locale) whenever they are not a console: a pipe, a file, or a terminal that is not a
 * Windows console, such as mintty (Git Bash). Those consumers expect UTF-8, so every
 * Japanese `println` and every Japanese diagnostic came out as mojibake there. A real
 * console (cmd.exe, PowerShell, Windows Terminal, the VS Code terminal) is different: the
 * JVM already writes it in the console's own code page, which the console then renders
 * correctly, so it is left exactly as it is.
 *
 * The rule, applied to stdout and stderr separately:
 *
 *  - `ONION_CONSOLE_ENCODING=native`: never change anything (the JVM's choice, as before).
 *  - `ONION_CONSOLE_ENCODING=utf-8`: write UTF-8 on every platform, console or not.
 *  - unset or `auto`: on Windows, a stream that is not attached to a console is written in
 *    UTF-8; a console keeps its code page. Other platforms are left alone (their native
 *    encoding is UTF-8 in practice, and a deliberately non-UTF-8 locale is respected).
 *
 * Whether a stream is a console is read from what the JVM itself decided at startup, so the
 * heuristic agrees with how the JVM encodes the stream:
 *
 *  - JDK 17/18 set `sun.stdout.encoding` / `sun.stderr.encoding` exactly when that handle is
 *    a console (to the console code page), and leave them unset otherwise.
 *  - JDK 19+ always set `stdout.encoding` / `stderr.encoding`: to the console code page for a
 *    console (spelled `ms932`, `cp437`, ...), to `native.encoding` (`MS932`, `Cp1252`, ...)
 *    otherwise. A value that differs from `native.encoding` therefore means a console. When
 *    the two coincide, `System.console()` (and `Console.isTerminal()` on JDK 22+) decides.
 *
 * A `sun.*` property given with `-D`, or a `stdout.encoding`/`stderr.encoding` that names
 * something other than `native.encoding`, therefore also counts as "keep what the JVM chose".
 *
 * Only the launchers' `main` methods call [[install]]; embedding hosts, tests and the language
 * server (whose stdout is a protocol channel) are never affected.
 */
object ConsoleEncoding {
  val EnvironmentVariable = "ONION_CONSOLE_ENCODING"

  enum Mode {
    case Auto, Native, Utf8
  }

  /** Which streams to re-encode as UTF-8. */
  final case class Decision(stdout: Boolean, stderr: Boolean)

  /** Parses the environment variable; Left carries the warning for an unrecognized value. */
  def parseMode(raw: String | Null): Either[String, Mode] =
    if (raw == null) Right(Mode.Auto)
    else raw.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "" | "auto" => Right(Mode.Auto)
      case "native" => Right(Mode.Native)
      case "utf-8" | "utf8" => Right(Mode.Utf8)
      case _ =>
        Left(s"onion: ignoring $EnvironmentVariable=$raw (expected auto, native or utf-8)")
    }

  /**
   * The decision for a given environment. Pure: `property` reads system properties,
   * `consoleIsTerminal` reports `System.console()`'s view, and both are passed in so the
   * heuristic can be tested without a console.
   */
  def decide(
    mode: Mode,
    osName: String,
    property: String => Option[String],
    consoleIsTerminal: () => Boolean
  ): Decision =
    mode match {
      case Mode.Native => Decision(stdout = false, stderr = false)
      case Mode.Utf8 => Decision(stdout = true, stderr = true)
      case Mode.Auto =>
        if (!osName.startsWith("Windows")) Decision(stdout = false, stderr = false)
        else {
          def reencode(stream: String): Boolean =
            !attachedToConsole(stream, property, consoleIsTerminal) &&
              !isUtf8(effectiveEncoding(stream, property))
          Decision(reencode("stdout"), reencode("stderr"))
        }
    }

  private def attachedToConsole(
    stream: String,
    property: String => Option[String],
    consoleIsTerminal: () => Boolean
  ): Boolean =
    if (property(s"sun.$stream.encoding").isDefined) true
    else property(s"$stream.encoding") match {
      case None => false
      case Some(encoding) =>
        if (property("native.encoding").exists(_ != encoding)) true
        else consoleIsTerminal()
    }

  private def effectiveEncoding(stream: String, property: String => Option[String]): Option[String] =
    property(s"sun.$stream.encoding")
      .orElse(property(s"$stream.encoding"))
      .orElse(property("native.encoding"))
      .orElse(property("file.encoding"))

  private def isUtf8(encoding: Option[String]): Boolean =
    encoding.exists { name =>
      try Charset.forName(name) == StandardCharsets.UTF_8
      catch { case _: Exception => false }
    }

  @volatile private var installed: Option[Decision] = None

  /** True when [[install]] switched stdout to UTF-8 (so a terminal library should follow). */
  def stdoutIsUtf8: Boolean = installed.exists(_.stdout)

  /**
   * Applies the decision to `System.out` / `System.err`. Idempotent: the REPL is reachable
   * both directly and through `onion repl`, and only the first call acts.
   */
  def install(): Unit = synchronized {
    if (installed.isEmpty) {
      val mode = parseMode(System.getenv(EnvironmentVariable)) match {
        case Right(m) => m
        case Left(warning) =>
          System.err.println(warning)
          Mode.Auto
      }
      val decision = decide(
        mode,
        System.getProperty("os.name", ""),
        key => Option(System.getProperty(key)),
        () => consoleIsTerminal()
      )
      if (decision.stdout) {
        System.out.flush()
        System.setOut(utf8Stream(FileDescriptor.out))
      }
      if (decision.stderr) {
        System.err.flush()
        System.setErr(utf8Stream(FileDescriptor.err))
      }
      installed = Some(decision)
    }
  }

  // The same shape as the JVM's own System.out/err: a small buffer and autoflush on println.
  private def utf8Stream(descriptor: FileDescriptor): PrintStream =
    new PrintStream(new BufferedOutputStream(new FileOutputStream(descriptor), 128), true, StandardCharsets.UTF_8)

  private def consoleIsTerminal(): Boolean = {
    val console = System.console()
    if (console == null) false
    else
      // JDK 22+ returns a Console even when redirected and says so through isTerminal();
      // before 22 a non-null console already means a terminal.
      try classOf[java.io.Console].getMethod("isTerminal").invoke(console) == java.lang.Boolean.TRUE
      catch { case _: Exception => true }
  }
}
