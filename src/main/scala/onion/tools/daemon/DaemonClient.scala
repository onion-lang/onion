package onion.tools.daemon

import java.io.{DataInputStream, DataOutputStream, File}
import java.net.UnixDomainSocketAddress
import java.nio.channels.{Channels, SocketChannel}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.util.control.NonFatal

/**
 * The client side of the compile daemon. `onionc` uses it when `ONION_DAEMON` is set to
 * anything but `0`/`off`/`false`: the command line is sent to a resident [[OnionDaemon]]
 * (started on first use, one per user, JDK and Onion installation) and its output and exit
 * code are relayed. Whenever the daemon cannot be reached or started, the caller compiles
 * in-process as before -- the daemon is only ever a shortcut.
 *
 * Paths in the command line are made absolute here, because the daemon has a different
 * working directory: source files, `-d` and `-classpath` entries, and the defaults for the
 * latter two (`.`) when they are absent.
 *
 * `java -cp onion.jar onion.tools.daemon.DaemonClient stop|status` controls the daemon.
 */
object DaemonClient {

  /** Whether `onionc` should try the daemon, per the `ONION_DAEMON` environment variable. */
  def enabledByEnvironment: Boolean = {
    val v = System.getenv("ONION_DAEMON")
    v != null && !Set("", "0", "off", "false", "no").contains(v.trim.toLowerCase)
  }

  def main(args: Array[String]): Unit = {
    val code = args.headOption match {
      case Some("stop") => control("stop")
      case Some("status") => control("ping")
      case Some("start") => if (ensureRunning(socketPath())) 0 else 1
      case _ =>
        System.err.println("usage: DaemonClient start|stop|status")
        2
    }
    System.exit(code)
  }

  /** Runs an `onionc` command line through the daemon; None when that is not possible. */
  def compile(args: Array[String]): Option[Int] =
    try {
      val socket = socketPath()
      if (!ensureRunning(socket)) None
      else {
        val cwd = Paths.get("").toAbsolutePath
        val response = request(socket, DaemonProtocol.Request("compile", cwd.toString, absolutize(args, cwd)))
        System.out.print(response.out)
        System.err.print(response.err)
        System.out.flush(); System.err.flush()
        Some(response.exitCode)
      }
    } catch { case NonFatal(_) => None }

  /**
   * The compile half of `onion` through the daemon: Some(Left(code)) when the daemon answered
   * but there is nothing to run (a compile error, `--help`...), Some(Right(prepared)) when the
   * classes came back, None when the daemon is not available. Runner options before the
   * script get their paths made absolute; the script's own arguments are passed verbatim.
   */
  def compileScript(args: Array[String]): Option[Either[Int, onion.tools.ScriptRunner.Prepared]] =
    try {
      val socket = socketPath()
      if (!ensureRunning(socket)) None
      else {
        val cwd = Paths.get("").toAbsolutePath
        val si = onion.tools.ScriptRunner.scriptIndex(args)
        if (si >= args.length) return None // no script: let the in-process runner print usage
        val runner = absolutizeRunnerOptions(args.take(si), cwd) :+ abs(args(si), cwd)
        val sent = runner ++ args.drop(si + 1)
        val channel = SocketChannel.open(UnixDomainSocketAddress.of(socket))
        try {
          val out = new DataOutputStream(Channels.newOutputStream(channel))
          val in = new DataInputStream(Channels.newInputStream(channel))
          DaemonProtocol.writeRequest(out, DaemonProtocol.Request("compile-script", cwd.toString, sent))
          val response = DaemonProtocol.readResponse(in)
          System.out.print(response.out)
          System.err.print(response.err)
          System.out.flush(); System.err.flush()
          if (response.exitCode != 0) Some(Left(response.exitCode))
          else {
            val bundle = DaemonProtocol.readBundle(in)
            Some(Right(onion.tools.ScriptRunner.Prepared(bundle.scriptName, bundle.classPath.toSeq, bundle.classes.toSeq, args.drop(si + 1))))
          }
        } finally channel.close()
      }
    } catch { case NonFatal(_) => None }

  /** `-classpath` entries made absolute and the `.` default supplied; other runner options pass through. */
  private[daemon] def absolutizeRunnerOptions(options: Array[String], cwd: Path): Array[String] = {
    val out = scala.collection.mutable.ArrayBuffer[String]()
    var sawClasspath = false
    var i = 0
    while (i < options.length) {
      val a = options(i)
      if (a == "-classpath" && i + 1 < options.length) {
        sawClasspath = true
        out += a += options(i + 1).split(File.pathSeparator).map(abs(_, cwd)).mkString(File.pathSeparator); i += 1
      } else if ((a == "-encoding" || a == "-maxErrorReport" || a == "-super" || a == "--Wno" || a == "--warn" ||
                  a == "--law-seed" || a == "--law-samples" || a == "--profile-format") && i + 1 < options.length) {
        out += a += options(i + 1); i += 1
      } else if (a == "--profile-output" && i + 1 < options.length) {
        val target = options(i + 1)
        out += a += (if (target == "stderr" || target == "stdout") target else abs(target, cwd)); i += 1
      } else out += a
      i += 1
    }
    if (!sawClasspath) out += "-classpath" += cwd.toString
    out.toArray
  }

  private def control(command: String): Int = {
    val socket = socketPath()
    if (!Files.exists(socket)) { println("onion daemon: not running"); return if (command == "ping") 1 else 0 }
    try {
      val r = request(socket, DaemonProtocol.Request(command, "", Array.empty))
      print(r.out); System.err.print(r.err)
      r.exitCode
    } catch {
      case NonFatal(_) =>
        // A stale socket file: the daemon died without cleaning up.
        try Files.deleteIfExists(socket) catch { case NonFatal(_) => () }
        println("onion daemon: not running")
        if (command == "ping") 1 else 0
    }
  }

  /** The command line with every path made absolute against `cwd` (see the class comment). */
  private[daemon] def absolutize(args: Array[String], cwd: Path): Array[String] = {
    val out = scala.collection.mutable.ArrayBuffer[String]()
    var sawOutput = false
    var sawClasspath = false
    var i = 0
    while (i < args.length) {
      val a = args(i)
      a match {
        case "-d" if i + 1 < args.length =>
          sawOutput = true
          out += a += abs(args(i + 1), cwd); i += 1
        case "-classpath" if i + 1 < args.length =>
          sawClasspath = true
          out += a += args(i + 1).split(File.pathSeparator).map(abs(_, cwd)).mkString(File.pathSeparator); i += 1
        case "-encoding" | "-maxErrorReport" | "-super" | "--Wno" | "--warn" | "--law-seed" | "--law-samples" |
             "--profile-format" if i + 1 < args.length =>
          out += a += args(i + 1); i += 1
        case "--profile-output" if i + 1 < args.length =>
          val target = args(i + 1)
          out += a += (if (target == "stderr" || target == "stdout") target else abs(target, cwd)); i += 1
        case _ if !a.startsWith("-") => out += abs(a, cwd)
        case _ => out += a
      }
      i += 1
    }
    if (!sawOutput) out += "-d" += cwd.toString
    if (!sawClasspath) out += "-classpath" += cwd.toString
    out.toArray
  }

  private def abs(p: String, cwd: Path): String =
    if (p.isEmpty) p else try cwd.resolve(p).normalize().toString catch { case NonFatal(_) => p }

  /** One socket per user, JDK and Onion class path, in a directory only the user can read. */
  def socketPath(): Path = {
    val explicit = System.getenv("ONION_DAEMON_SOCKET")
    if (explicit != null && explicit.nonEmpty) return Paths.get(explicit)
    val runtimeDir = Option(System.getenv("XDG_RUNTIME_DIR")).filter(_.nonEmpty).getOrElse(System.getProperty("java.io.tmpdir"))
    val user = System.getProperty("user.name", "onion")
    val key = System.getProperty("java.class.path", "") + "|" + System.getProperty("java.version", "") + "|" + System.getProperty("java.home", "")
    val digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))
    val hash = digest.take(6).map(b => f"${b & 0xff}%02x").mkString
    Paths.get(runtimeDir, s"onion-daemon-$user", s"$hash.sock")
  }

  /** Connects to the daemon, starting it first if needed; false when it cannot be reached. */
  private def ensureRunning(socket: Path): Boolean = {
    if (canConnect(socket)) return true
    try Files.deleteIfExists(socket) catch { case NonFatal(_) => () }
    start(socket)
    val deadline = System.currentTimeMillis() + 8000
    while (System.currentTimeMillis() < deadline) {
      if (canConnect(socket)) return true
      Thread.sleep(25)
    }
    false
  }

  private def canConnect(socket: Path): Boolean =
    Files.exists(socket) && {
      try { val ch = SocketChannel.open(UnixDomainSocketAddress.of(socket)); ch.close(); true }
      catch { case NonFatal(_) => false }
    }

  /** Launches the daemon JVM with this JVM's java and class path, detached, logging to a file next to the socket. */
  private def start(socket: Path): Unit = {
    Files.createDirectories(socket.getParent)
    val java = Paths.get(System.getProperty("java.home"), "bin", if (File.separatorChar == '\\') "java.exe" else "java").toString
    val extra = Option(System.getenv("ONION_DAEMON_JAVA_OPTS")).filter(_.nonEmpty).map(_.trim.split("\\s+").toList).getOrElse(Nil)
    val command = List(java, "-XX:+UseParallelGC", "-Xss16m") ++ extra ++
      List("-cp", System.getProperty("java.class.path"), "onion.tools.daemon.OnionDaemon", socket.toString, OnionDaemon.DefaultIdleMillis.toString)
    val log = socket.resolveSibling(socket.getFileName.toString.stripSuffix(".sock") + ".log").toFile
    val builder = new ProcessBuilder(command*)
    builder.redirectErrorStream(true)
    builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log))
    builder.redirectInput(ProcessBuilder.Redirect.from(new File(if (File.separatorChar == '\\') "NUL" else "/dev/null")))
    builder.start()
  }

  private[daemon] def request(socket: Path, req: DaemonProtocol.Request): DaemonProtocol.Response = {
    val channel = SocketChannel.open(UnixDomainSocketAddress.of(socket))
    try {
      val out = new DataOutputStream(Channels.newOutputStream(channel))
      val in = new DataInputStream(Channels.newInputStream(channel))
      DaemonProtocol.writeRequest(out, req)
      DaemonProtocol.readResponse(in)
    } finally channel.close()
  }
}
