package onion.tools.doc

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * CLI entry point for `oniondoc`, a javadoc/scaladoc-like documentation
 * generator for Onion source files.
 *
 * Usage: `oniondoc -d <outdir> <source.on...>`
 */
object OnionDoc {

  private val DefaultOutDir = "./doc"

  def main(args: Array[String]): Unit = {
    val exit = run(args)
    if (exit != 0) System.exit(exit)
  }

  /** Run the generator; returns a process exit code (0 = success). */
  def run(args: Array[String]): Int = {
    var outDir = DefaultOutDir
    val sources = scala.collection.mutable.ListBuffer[String]()

    var i = 0
    while (i < args.length) {
      args(i) match {
        case "-h" | "--help" =>
          printUsage(); return 0
        case "-d" =>
          if (i + 1 >= args.length) {
            System.err.println("error: -d requires an output directory")
            return 1
          }
          outDir = args(i + 1)
          i += 2
        case flag if flag.startsWith("-") =>
          System.err.println(s"error: unknown option: $flag")
          return 1
        case path =>
          sources += path
          i += 1
      }
    }

    if (sources.isEmpty) {
      System.err.println("error: no source files given")
      printUsage()
      return 1
    }

    val files = scala.collection.mutable.ListBuffer[DocFile]()
    for (path <- sources) {
      val f = new File(path)
      if (!f.exists()) {
        System.err.println(s"error: file not found: $path")
        return 1
      }
      val text = new String(Files.readAllBytes(f.toPath), StandardCharsets.UTF_8)
      try {
        files += DocModel.fromSource(text, f.getName)
      } catch {
        case e: Throwable =>
          System.err.println(s"error: failed to parse $path: ${e.getMessage}")
          return 1
      }
    }

    val out = new File(outDir)
    HtmlWriter.write(files.toList, out)
    println(s"Documentation written to ${out.getAbsolutePath}")
    0
  }

  private def printUsage(): Unit = {
    println("Usage: oniondoc -d <outdir> <source.on...>")
    println("  -d <outdir>   output directory for the generated site (default: ./doc)")
    println("  -h, --help    show this help")
  }
}
