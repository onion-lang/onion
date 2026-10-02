package onion.tools.project

import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal

import onion.tools.OnionVersion
import onion.tools.ScriptDependencies

/**
 * What "the same compiler" means to the build cache.
 *
 * The version string alone is not enough: a locally built or upgraded jar often reports the
 * same version, and a change to a compiler resource (`onion/effect-table.txt`, the error
 * message bundles) changes what a build reports without changing any version at all. With
 * only the version in the fingerprint, `onion build` kept answering "(cached)" after such a
 * change, so new diagnostics and effect checks never ran until `onion clean`.
 *
 * The identity is therefore the version plus a digest of where the compiler was loaded from:
 *
 *  - a **jar** (the normal, installed case) contributes a SHA-256 of its bytes. Hashing a
 *    ~30 MB jar on every build would cost more than the cached build it guards, so the
 *    digest is cached under the user cache directory, keyed by the jar's absolute path,
 *    size and modification time, and recomputed only when one of those changes;
 *  - a **directory** of classes (running from sbt) contributes each file's relative path,
 *    size and modification time — cheap, and a recompile touches exactly those.
 *
 * Anything that cannot be read contributes nothing rather than failing the build; at worst
 * the identity degrades to the version string, which is what it was before.
 */
object CompilerIdentity:

  /** Bumped when the cache file's format or what the digest covers changes. */
  private val CacheFormat = "onion-compiler-identity-v1"

  /** Classes whose code sources make up the compiler and its runtime library. */
  private val AnchorClasses: Seq[Class[?]] = Seq(classOf[onion.compiler.OnionCompiler], classOf[onion.IO])

  /** Compiler resources that are read at compile time and could live somewhere else. */
  private val AnchorResources: Seq[String] = Seq("onion/effect-table.txt")

  /** This JVM's compiler. Computed once: the classes it describes cannot change under it. */
  lazy val current: String = compute(OnionVersion.value, locations(), ScriptDependencies.cacheDirectory())

  /**
   * The identity of the compiler `version` loaded from `locations` (jars or class
   * directories), caching jar digests under `cacheDir` when there is one.
   */
  def compute(version: String, locations: Seq[Path], cacheDir: Option[Path]): String =
    val parts = locations.map(_.toAbsolutePath.normalize).distinct.flatMap(location => digestOf(location, cacheDir))
    if parts.isEmpty then version
    else version + "+" + sha256Hex(parts.mkString("\n").getBytes(UTF_8))

  /** Where the anchor classes and resources were loaded from: jars or class directories. */
  private[project] def locations(): Seq[Path] =
    val fromClasses = AnchorClasses.flatMap { cls =>
      try Option(cls.getProtectionDomain.getCodeSource).flatMap(cs => Option(cs.getLocation)).flatMap(fileOf)
      catch case NonFatal(_) => None
    }
    val fromResources = AnchorResources.flatMap { name =>
      try Option(getClass.getClassLoader.getResource(name)).flatMap(containerOf(_, name))
      catch case NonFatal(_) => None
    }
    (fromClasses ++ fromResources).distinct

  private def fileOf(url: URL): Option[Path] =
    if url.getProtocol != "file" then None
    else Some(Paths.get(url.toURI))

  /** A `jar:file:/x.jar!/name` URL to `/x.jar`, a `file:/dir/name` URL to `/dir`. */
  private def containerOf(url: URL, name: String): Option[Path] =
    url.getProtocol match
      case "jar" =>
        val spec = url.getPath // file:/x.jar!/onion/effect-table.txt
        val bang = spec.indexOf("!/")
        if bang < 0 || !spec.startsWith("file:") then None
        else Some(Paths.get(java.net.URI(spec.substring(0, bang))))
      case "file" =>
        fileOf(url).map { file =>
          var root = file
          name.split('/').foreach(_ => root = root.getParent)
          root
        }
      case _ => None

  private def digestOf(location: Path, cacheDir: Option[Path]): Option[String] =
    try
      if Files.isRegularFile(location) then jarDigest(location, cacheDir).map(d => s"jar:$d")
      else if Files.isDirectory(location) then Some(s"dir:${directoryDigest(location)}")
      else None
    catch case NonFatal(_) => None

  /** The SHA-256 of a jar's bytes, through the (path, size, mtime)-keyed cache. */
  private[project] def jarDigest(jar: Path, cacheDir: Option[Path]): Option[String] =
    val attributes = Files.readAttributes(jar, classOf[BasicFileAttributes])
    val key = Seq(CacheFormat, jar.toString, attributes.size.toString, attributes.lastModifiedTime.toMillis.toString)
    val cacheFile = cacheDir.map(_.resolve("compiler-identity").resolve(sha256Hex(jar.toString.getBytes(UTF_8)) + ".txt"))
    cacheFile.flatMap(readCache(_, key)).orElse {
      val digest = Using.resource(Files.newInputStream(jar))(sha256Hex)
      cacheFile.foreach(writeCache(_, key :+ digest))
      Some(digest)
    }

  /** Relative path, size and mtime of every regular file under `root`, in path order. */
  private def directoryDigest(root: Path): String =
    val lines = Using.resource(Files.walk(root)) { stream =>
      stream.iterator.asScala
        .filter(Files.isRegularFile(_))
        .map { file =>
          val attributes = Files.readAttributes(file, classOf[BasicFileAttributes])
          val relative = root.relativize(file).iterator.asScala.mkString("/")
          s"$relative\t${attributes.size}\t${attributes.lastModifiedTime.toMillis}"
        }
        .toVector
        .sorted
    }
    sha256Hex(lines.mkString("\n").getBytes(UTF_8))

  /** The cached digest, when the file's key lines are exactly `key`. */
  private def readCache(file: Path, key: Seq[String]): Option[String] =
    try
      if !Files.isRegularFile(file) then None
      else
        val lines = Files.readAllLines(file, UTF_8).asScala.toSeq
        if lines.length == key.length + 1 && lines.take(key.length) == key && lines.last.matches("[0-9a-f]{64}")
        then Some(lines.last)
        else None
    catch case NonFatal(_) => None

  /** Written to a temporary file and moved into place; a cache that cannot be written is skipped. */
  private def writeCache(file: Path, lines: Seq[String]): Unit =
    try
      Files.createDirectories(file.getParent)
      val temp = Files.createTempFile(file.getParent, file.getFileName.toString, ".tmp")
      try
        Files.write(temp, lines.asJava, UTF_8)
        try Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        catch
          case _: java.nio.file.AtomicMoveNotSupportedException =>
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
      finally Files.deleteIfExists(temp)
    catch case NonFatal(_) => ()

  private def sha256Hex(bytes: Array[Byte]): String =
    hex(MessageDigest.getInstance("SHA-256").digest(bytes))

  private def sha256Hex(in: InputStream): String =
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = new Array[Byte](1 << 16)
    var read = in.read(buffer)
    while read >= 0 do
      digest.update(buffer, 0, read)
      read = in.read(buffer)
    hex(digest.digest())

  private def hex(bytes: Array[Byte]): String = bytes.map(b => f"${b & 0xff}%02x").mkString
