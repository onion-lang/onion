package onion.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard over the two launcher implementations.
 *
 * Onion starts through one of two sets of scripts, and neither knows about the other:
 *
 *   - the `bin/` scripts — what the distribution zip ships, and what a source checkout runs
 *   - the launchers `install.sh` writes inline — what a `curl | sh` install gets
 *
 * They had already diverged. The installed launchers had class-data sharing and the
 * `--sun-misc-unsafe-memory-access=allow` workaround; the `bin/` scripts had neither, so the same
 * program started in roughly half the time depending on how you obtained Onion (0.38s
 * against 0.73s for `onion run/Hello.on` on JDK 25) and printed four lines of JVM
 * warnings on every run in one case and none in the other. Nothing failed, because
 * nothing was looking.
 *
 * So this looks. It does not check that the two are identical — one resolves paths
 * relative to `ONION_HOME`, the other bakes in absolute paths — only that every
 * capability present in one is present in the other.
 */
class LauncherParitySpec extends AnyFunSpec {

  private def read(path: String): String =
    val file = java.nio.file.Path.of(path)
    assert(java.nio.file.Files.exists(file), s"$path does not exist")
    java.nio.file.Files.readString(file)

  private val PosixLaunchers = Seq("bin/onion", "bin/onionc", "bin/onion-repl", "bin/onion-lsp")
  private val WindowsLaunchers =
    Seq("bin/onion.bat", "bin/onionc.bat", "bin/onion-repl.bat", "bin/onion-lsp.bat")

  /** The archive name both implementations must agree on, or each builds one the other ignores. */
  private val ArchiveName = "onion.jsa"

  describe("the bin/ launchers") {
    it("all delegate their JVM setup to one place instead of repeating it") {
      assert(PosixLaunchers.nonEmpty && WindowsLaunchers.nonEmpty, "the launcher list has rotted")
      for (launcher <- PosixLaunchers)
        assert(read(launcher).contains("onion-jvm.sh"),
          s"$launcher does not source bin/onion-jvm.sh, so its JVM flags will drift")
      for (launcher <- WindowsLaunchers)
        assert(read(launcher).contains("onion-jvm.bat"),
          s"$launcher does not call bin/onion-jvm.bat, so its JVM flags will drift")
    }

    it("all pass both the shared flags and the user's own to java") {
      for (launcher <- PosixLaunchers) {
        val text = read(launcher)
        assert(text.contains("$ONION_JVM_ARGS") && text.contains("$ONION_JAVA_OPTS"),
          s"$launcher must pass $$ONION_JVM_ARGS and $$ONION_JAVA_OPTS before -classpath")
      }
      for (launcher <- WindowsLaunchers) {
        val text = read(launcher)
        assert(text.contains("%ONION_JVM_ARGS%") && text.contains("%ONION_JAVA_OPTS%"),
          s"$launcher must pass %ONION_JVM_ARGS% and %ONION_JAVA_OPTS% before -classpath")
      }
    }
  }

  describe("the two launcher implementations") {
    // Each capability is named by what it does, so a failure says what a user loses.
    val capabilities = Seq(
      "user-supplied JVM flags"        -> "ONION_JAVA_OPTS",
      "class-data sharing"             -> "SharedArchiveFile",
      "tolerating a stale archive"     -> "-Xshare:auto",
      "silencing stale-archive noise"  -> "-Xlog:cds",
      "the sun.misc.Unsafe workaround" -> "sun-misc-unsafe-memory-access"
    )

    for ((what, marker) <- capabilities) {
      it(s"both support $what") {
        assert(read("bin/onion-jvm.sh").contains(marker),
          s"bin/onion-jvm.sh is missing $what ($marker)")
        assert(read("install.sh").contains(marker),
          s"install.sh is missing $what ($marker)")
      }
    }

    it("agree on the archive filename, or each writes one the other ignores") {
      assert(read("bin/onion-jvm.sh").contains(ArchiveName),
        s"bin/onion-jvm.sh does not use $ArchiveName")
      assert(read("install.sh").contains(ArchiveName),
        s"install.sh does not use $ArchiveName")
    }
  }

  describe("the Windows shared setup") {
    it("mirrors the POSIX one, since it cannot source it") {
      val bat = read("bin/onion-jvm.bat")
      for (marker <- Seq("ONION_JAVA_OPTS", "SharedArchiveFile", "-Xshare:auto", "-Xlog:cds",
                         "ArchiveClassesAtExit", ArchiveName))
        assert(bat.contains(marker), s"bin/onion-jvm.bat is missing $marker")
    }
  }
}
