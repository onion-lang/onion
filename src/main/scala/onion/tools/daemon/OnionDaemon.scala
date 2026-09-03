package onion.tools.daemon

import onion.tools.CompilerFrontend

import java.io.{ByteArrayOutputStream, DataInputStream, DataOutputStream, PrintStream}
import java.net.{StandardProtocolFamily, UnixDomainSocketAddress}
import java.nio.channels.{Channels, ServerSocketChannel, SocketChannel}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.control.NonFatal

/**
 * The compile daemon: a resident JVM that runs `onionc` command lines sent to it over a
 * Unix domain socket, so the JIT-compiled compiler and the shared class universe are reused
 * across invocations instead of being rebuilt by a fresh JVM each time.
 *
 * One request at a time. Standard output and error are captured per request and sent back
 * with the exit code; the daemon exits after `idleMillis` without a request, or on `stop`.
 * The socket file lives in a directory only the user can read, and is removed on exit.
 *
 * Started on demand by [[DaemonClient]]; not meant to be run by hand, but
 * `java -cp onion.jar onion.tools.daemon.OnionDaemon <socket-path> [idle-millis]` works.
 */
object OnionDaemon {
  val DefaultIdleMillis: Long = 30L * 60 * 1000

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      System.err.println("usage: OnionDaemon <socket-path> [idle-millis]")
      System.exit(2)
    }
    val idle = if (args.length > 1) args(1).toLong else DefaultIdleMillis
    serve(Path.of(args(0)), idle)
    System.exit(0)
  }

  /** Serves until `stop` or `idleMillis` of inactivity. `ready` runs once the socket accepts connections. */
  def serve(socket: Path, idleMillis: Long, ready: () => Unit = () => ()): Unit = {
    Files.createDirectories(socket.getParent)
    restrictToOwner(socket.getParent)
    Files.deleteIfExists(socket)
    val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    server.bind(UnixDomainSocketAddress.of(socket))
    restrictToOwner(socket)
    @volatile var lastActivity = System.currentTimeMillis()
    @volatile var stopping = false
    val watchdog = new Thread(() => {
      try {
        while (!stopping) {
          Thread.sleep(math.min(idleMillis, 60000L))
          if (!stopping && System.currentTimeMillis() - lastActivity > idleMillis) {
            stopping = true
            server.close() // makes accept() throw, ending the loop below
          }
        }
      } catch { case _: InterruptedException => () }
    }, "onion-daemon-idle")
    watchdog.setDaemon(true)
    watchdog.start()
    ready()
    try {
      while (!stopping) {
        val channel = try server.accept() catch { case _: java.nio.channels.AsynchronousCloseException | _: java.nio.channels.ClosedChannelException => null }
        if (channel == null) stopping = true
        else {
          lastActivity = System.currentTimeMillis()
          try {
            if (!handle(channel)) stopping = true
          } catch { case NonFatal(_) => () }
          finally { try channel.close() catch { case NonFatal(_) => () } }
          lastActivity = System.currentTimeMillis()
        }
      }
    } finally {
      stopping = true
      try server.close() catch { case NonFatal(_) => () }
      try Files.deleteIfExists(socket) catch { case NonFatal(_) => () }
    }
  }

  /** Handles one connection; false when the client asked the daemon to stop. */
  private def handle(channel: SocketChannel): Boolean = {
    val in = new DataInputStream(Channels.newInputStream(channel))
    val out = new DataOutputStream(Channels.newOutputStream(channel))
    val request = DaemonProtocol.readRequest(in)
    request.command match {
      case "ping" =>
        DaemonProtocol.writeResponse(out, DaemonProtocol.Response(0, "pong\n", ""))
        true
      case "stop" =>
        DaemonProtocol.writeResponse(out, DaemonProtocol.Response(0, "stopped\n", ""))
        false
      case "compile" =>
        DaemonProtocol.writeResponse(out, compile(request.args))
        true
      case other =>
        DaemonProtocol.writeResponse(out, DaemonProtocol.Response(2, "", s"unknown daemon command: $other\n"))
        true
    }
  }

  /** Runs one `onionc` command line with its output captured. Serialized: the streams are process-wide. */
  private[daemon] def compile(args: Array[String]): DaemonProtocol.Response = synchronized {
    val outBytes = new ByteArrayOutputStream()
    val errBytes = new ByteArrayOutputStream()
    val outStream = new PrintStream(outBytes, true, StandardCharsets.UTF_8)
    val errStream = new PrintStream(errBytes, true, StandardCharsets.UTF_8)
    val previousOut = System.out
    val previousErr = System.err
    System.setOut(outStream)
    System.setErr(errStream)
    val code =
      try Console.withOut(outStream) { Console.withErr(errStream) { CompilerFrontend.runCommandLine(args) } }
      catch {
        case NonFatal(e) =>
          errStream.println(s"onion daemon: internal error: $e")
          -1
      } finally {
        System.setOut(previousOut)
        System.setErr(previousErr)
      }
    outStream.flush(); errStream.flush()
    DaemonProtocol.Response(code, outBytes.toString(StandardCharsets.UTF_8), errBytes.toString(StandardCharsets.UTF_8))
  }

  private def restrictToOwner(path: Path): Unit =
    try {
      import java.nio.file.attribute.PosixFilePermissions
      val perms = if (Files.isDirectory(path)) "rwx------" else "rw-------"
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(perms))
    } catch { case NonFatal(_) => () } // not a POSIX file system (Windows): the user directory is private anyway
}
