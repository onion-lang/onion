package onion.tools.daemon

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}
import java.util.concurrent.CountDownLatch

/**
 * The compile daemon end to end, in-process: a server on a temporary socket, requests
 * through the client's wire code, a compile that produces class files, a compile that
 * fails with diagnostics relayed, and `stop`.
 */
class DaemonSpec extends AnyFunSpec with Matchers {

  private def unixSocketsSupported: Boolean =
    try { java.nio.channels.ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX).close(); true }
    catch { case _: Throwable => false }

  private def withDaemon[A](body: Path => A): A = {
    val dir = Files.createTempDirectory("onion-daemon-spec")
    val socket = dir.resolve("d.sock")
    val ready = new CountDownLatch(1)
    val server = new Thread(() => OnionDaemon.serve(socket, 60000L, () => ready.countDown()), "daemon-under-test")
    server.setDaemon(true)
    server.start()
    ready.await()
    try body(socket)
    finally {
      try DaemonClient.request(socket, DaemonProtocol.Request("stop", "", Array.empty)) catch { case _: Throwable => () }
      server.join(10000)
    }
  }

  describe("the compile daemon") {
    it("answers ping, compiles a file to class files, relays diagnostics, and stops") {
      assume(unixSocketsSupported, "Unix domain sockets are not available on this platform")
      withDaemon { socket =>
        val pong = DaemonClient.request(socket, DaemonProtocol.Request("ping", "", Array.empty))
        pong.exitCode shouldBe 0
        pong.out.trim shouldBe "pong"

        val work = Files.createTempDirectory("onion-daemon-work")
        val source = work.resolve("Hello.on")
        Files.writeString(source, "IO::println(\"hello from the daemon\")\n")
        val out = work.resolve("out")
        val args = DaemonClient.absolutize(Array("Hello.on", "-d", "out"), work)
        val ok = DaemonClient.request(socket, DaemonProtocol.Request("compile", work.toString, args))
        withClue(ok.err) { ok.exitCode shouldBe 0 }
        Files.exists(out.resolve("HelloMain.class")) shouldBe true

        Files.writeString(source, "val x: Int = \"not an int\"\n")
        val bad = DaemonClient.request(socket, DaemonProtocol.Request("compile", work.toString, args))
        bad.exitCode should not be 0
        bad.err should include ("E0")
      }
    }

    it("compiles a script for the client to run, returning its classes and class path") {
      assume(unixSocketsSupported, "Unix domain sockets are not available on this platform")
      withDaemon { socket =>
        val work = Files.createTempDirectory("onion-daemon-script")
        val source = work.resolve("Greet.on")
        Files.writeString(source, "def main(name: String): Int {\n  IO::println(\"hi \" + name)\n  return 3\n}\n")
        val runner = DaemonClient.absolutizeRunnerOptions(Array.empty, work) :+ source.toString
        val channel = java.nio.channels.SocketChannel.open(java.net.UnixDomainSocketAddress.of(socket))
        try {
          val out = new java.io.DataOutputStream(java.nio.channels.Channels.newOutputStream(channel))
          val in = new java.io.DataInputStream(java.nio.channels.Channels.newInputStream(channel))
          DaemonProtocol.writeRequest(out, DaemonProtocol.Request("compile-script", work.toString, runner :+ "world"))
          val response = DaemonProtocol.readResponse(in)
          withClue(response.err) { response.exitCode shouldBe 0 }
          val bundle = DaemonProtocol.readBundle(in)
          bundle.scriptName shouldBe "Greet.on"
          bundle.classPath.toList shouldBe List(work.toString)
          bundle.classes.map(_.className) should contain ("GreetMain")
          bundle.classes.forall(_.content.length > 0) shouldBe true
        } finally channel.close()
      }
    }

    it("makes source, -d and -classpath paths absolute and supplies the defaults") {
      val cwd = Path.of("/work/dir")
      val args = DaemonClient.absolutize(Array("--warn", "off", "a.on", "sub/b.on"), cwd)
      args.toList shouldBe List("--warn", "off", "/work/dir/a.on", "/work/dir/sub/b.on", "-d", "/work/dir", "-classpath", "/work/dir")
      val explicit = DaemonClient.absolutize(Array("-d", "out", "-classpath", "lib/a.jar:/abs/b.jar", "x.on"), cwd)
      explicit.toList shouldBe List("-d", "/work/dir/out", "-classpath", "/work/dir/lib/a.jar:/abs/b.jar", "/work/dir/x.on")
    }
  }
}
