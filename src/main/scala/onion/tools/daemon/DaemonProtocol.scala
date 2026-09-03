package onion.tools.daemon

import java.io.{DataInputStream, DataOutputStream}
import java.nio.charset.StandardCharsets

/**
 * The wire format between [[DaemonClient]] and [[OnionDaemon]]: length-prefixed UTF-8
 * strings over a Unix domain socket.
 *
 * Request: `command`, `cwd`, then `n` and `n` arguments. Commands are `compile` (the
 * arguments are an `onionc` command line, with every path already absolute), `ping` and
 * `stop`. Response: exit code, then captured standard output and standard error.
 */
private[daemon] object DaemonProtocol {
  final case class Request(command: String, cwd: String, args: Array[String])
  final case class Response(exitCode: Int, out: String, err: String)

  def writeString(out: DataOutputStream, s: String): Unit = {
    val bytes = s.getBytes(StandardCharsets.UTF_8)
    out.writeInt(bytes.length)
    out.write(bytes)
  }

  def readString(in: DataInputStream): String = {
    val n = in.readInt()
    if (n < 0 || n > (1 << 26)) throw new java.io.IOException(s"bad length $n")
    val bytes = new Array[Byte](n)
    in.readFully(bytes)
    new String(bytes, StandardCharsets.UTF_8)
  }

  def writeRequest(out: DataOutputStream, r: Request): Unit = {
    writeString(out, r.command)
    writeString(out, r.cwd)
    out.writeInt(r.args.length)
    r.args.foreach(writeString(out, _))
    out.flush()
  }

  def readRequest(in: DataInputStream): Request = {
    val command = readString(in)
    val cwd = readString(in)
    val n = in.readInt()
    if (n < 0 || n > 100000) throw new java.io.IOException(s"bad argument count $n")
    val args = new Array[String](n)
    var i = 0
    while (i < n) { args(i) = readString(in); i += 1 }
    Request(command, cwd, args)
  }

  def writeResponse(out: DataOutputStream, r: Response): Unit = {
    out.writeInt(r.exitCode)
    writeString(out, r.out)
    writeString(out, r.err)
    out.flush()
  }

  /** After a `compile-script` response: the classes to run and the class path they were compiled against. */
  final case class ClassBundle(scriptName: String, classPath: Array[String], classes: Array[onion.compiler.CompiledClass])

  def writeBundle(out: DataOutputStream, b: ClassBundle): Unit = {
    writeString(out, b.scriptName)
    out.writeInt(b.classPath.length)
    b.classPath.foreach(writeString(out, _))
    out.writeInt(b.classes.length)
    b.classes.foreach { c =>
      writeString(out, c.className)
      writeString(out, if (c.outputPath == null) "" else c.outputPath)
      out.writeInt(c.content.length)
      out.write(c.content)
    }
    out.flush()
  }

  def readBundle(in: DataInputStream): ClassBundle = {
    val scriptName = readString(in)
    val cp = Array.fill(in.readInt())(readString(in))
    val n = in.readInt()
    if (n < 0 || n > 100000) throw new java.io.IOException(s"bad class count $n")
    val classes = new Array[onion.compiler.CompiledClass](n)
    var i = 0
    while (i < n) {
      val name = readString(in)
      val path = readString(in)
      val len = in.readInt()
      if (len < 0 || len > (1 << 28)) throw new java.io.IOException(s"bad class size $len")
      val bytes = new Array[Byte](len)
      in.readFully(bytes)
      classes(i) = onion.compiler.CompiledClass(name, if (path.isEmpty) null else path, bytes)
      i += 1
    }
    ClassBundle(scriptName, cp, classes)
  }

  def readResponse(in: DataInputStream): Response = {
    val code = in.readInt()
    val out = readString(in)
    val err = readString(in)
    Response(code, out, err)
  }
}
