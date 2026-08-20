package onion.compiler.tools

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}

import onion.tools.Shell

/**
 * `onion.Archive`: zip and gzip.
 *
 * Onion could read and write files but not archives, which is most of what a release, a
 * backup or a log rotation actually touches.
 *
 * The zip-slip case is built in Scala rather than in Onion, because it needs a
 * deliberately malformed archive that no normal API would produce.
 */
class ArchiveSpec extends AbstractShellSpec {

  describe("Archive") {
    it("round-trips files through a zip") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val dir = System::getProperty("java.io.tmpdir") + "/onion-zip-" + Rand::uuid()
          |    Files::mkdirs(dir)
          |    Files::writeText(dir + "/a.txt", "alpha")
          |    Files::writeText(dir + "/b.txt", "beta")
          |
          |    val zip = dir + "/out.zip"
          |    Archive::zip(zip, [dir + "/a.txt", dir + "/b.txt"])
          |
          |    val names = Archive::entries(zip)
          |    val out = dir + "/extracted"
          |    Archive::unzip(zip, out)
          |    val text = Files::readText(out + "/a.txt") + Files::readText(out + "/b.txt")
          |    return names.size + ":" + names[0] + "," + names[1] + ":" + text
          |  }
          |}
          |""".stripMargin,
        "ArchiveZipRoundTrip.on",
        Array()
      )
      assert(Shell.Success("2:a.txt,b.txt:alphabeta") == result)
    }

    it("keeps relative paths when zipping a directory tree") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val dir = System::getProperty("java.io.tmpdir") + "/onion-zipdir-" + Rand::uuid()
          |    Files::mkdirs(dir + "/site/css")
          |    Files::writeText(dir + "/site/index.html", "<h1>hi</h1>")
          |    Files::writeText(dir + "/site/css/main.css", "body{}")
          |
          |    val zip = dir + "/site.zip"
          |    Archive::zipDir(zip, dir + "/site")
          |    val names = Archive::entries(zip)
          |    return names[0] + "|" + names[1]
          |  }
          |}
          |""".stripMargin,
        "ArchiveZipDir.on",
        Array()
      )
      assert(Shell.Success("css/main.css|index.html") == result)
    }

    it("round-trips bytes and files through gzip") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val dir = System::getProperty("java.io.tmpdir") + "/onion-gzip-" + Rand::uuid()
          |    Files::mkdirs(dir)
          |    val original = "the quick brown fox jumps over the lazy dog"
          |    Files::writeText(dir + "/plain.txt", original)
          |
          |    Archive::gzipFile(dir + "/plain.txt", dir + "/plain.txt.gz")
          |    Archive::gunzipFile(dir + "/plain.txt.gz", dir + "/back.txt")
          |    val viaFile = Files::readText(dir + "/back.txt")
          |
          |    val viaBytes = new String(Archive::gunzip(Archive::gzip(original.getBytes())))
          |    if viaFile == original && viaBytes == original { return "round-tripped" }
          |    return viaFile + "|" + viaBytes
          |  }
          |}
          |""".stripMargin,
        "ArchiveGzip.on",
        Array()
      )
      assert(Shell.Success("round-tripped") == result)
    }

    it("produces the same bytes for the same inputs, so an artefact can be checksummed") {
      // Zip stores a modification time per entry. Left at "now", zipping identical inputs
      // twice yields different bytes, and nothing downstream can cache or verify them.
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val dir = System::getProperty("java.io.tmpdir") + "/onion-zipdet-" + Rand::uuid()
          |    Files::mkdirs(dir)
          |    Files::writeText(dir + "/a.txt", "alpha")
          |    Archive::zip(dir + "/one.zip", [dir + "/a.txt"])
          |    Archive::zip(dir + "/two.zip", [dir + "/a.txt"])
          |    val first = Hash::sha256(new String(Files::readBytes(dir + "/one.zip"), "ISO-8859-1"))
          |    val second = Hash::sha256(new String(Files::readBytes(dir + "/two.zip"), "ISO-8859-1"))
          |    if first == second { return "deterministic" }
          |    return first + " != " + second
          |  }
          |}
          |""".stripMargin,
        "ArchiveDeterministic.on",
        Array()
      )
      assert(Shell.Success("deterministic") == result)
    }

    it("refuses a zip entry that would land outside the target directory") {
      // "Zip slip": an entry named ../../… makes a naive extractor write wherever it is
      // told. Built here by hand, because no normal API produces such an archive.
      val work = Files.createTempDirectory("onion-zipslip")
      val malicious = work.resolve("evil.zip")
      val stream = new ZipOutputStream(Files.newOutputStream(malicious))
      try {
        stream.putNextEntry(new ZipEntry("../escaped.txt"))
        stream.write("owned".getBytes(UTF_8))
        stream.closeEntry()
      } finally stream.close()

      val target = work.resolve("target")
      val escaped: Path = work.resolve("escaped.txt")

      val thrown = intercept[SecurityException] {
        onion.Archive.unzip(malicious.toString, target.toString)
      }
      assert(thrown.getMessage.contains("escaped.txt"))
      assert(!Files.exists(escaped), s"the entry escaped the target directory to $escaped")
    }
  }
}
