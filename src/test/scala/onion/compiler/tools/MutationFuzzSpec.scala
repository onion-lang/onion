package onion.compiler.tools

import java.io.{File, StringReader}

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.exceptions.CompilationException
import org.scalatest.funspec.AnyFunSpec

import scala.util.Random

/**
 * Deterministic mutation fuzzing of the compiler: mutates the run/ example
 * programs with a fixed seed and asserts that no mutant crashes the compiler
 * (escaped Throwable or I0000 internal-error diagnostic). Mutants that crash
 * are written to /tmp/onion-fuzz/ for minimization; reduced reproducers
 * belong in src/test/resources/crash-corpus/.
 *
 * The seed is fixed so this is an ordinary regression test, not a flaky one.
 * Bump MUTATIONS_PER_SEED or change the seed locally to hunt for new crashes.
 */
class MutationFuzzSpec extends AnyFunSpec {

  private val SEED = 20260610L
  private val MUTATIONS_PER_SEED = 60

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  /** Returns Some(crashDescription) if the compiler crashed on the input. */
  private def compileForCrash(source: String): Option[String] =
    try {
      val compiler = new OnionCompiler(newConfig)
      val result = compiler.compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Fuzz.on")))
      result.allErrors.find(_.errorCode.contains("I0000")).map(e => s"I0000: ${e.message}")
    } catch {
      case _: CompilationException => None
      case e: Throwable => Some(s"ESCAPED ${e.getClass.getName}: ${e.getMessage}")
    }

  private def mutate(src: String, rng: Random): String = {
    val lines = src.linesIterator.toVector
    rng.nextInt(7) match {
      case 0 => // delete a random span of characters
        if (src.length < 10) src
        else {
          val start = rng.nextInt(src.length - 5)
          val len = 1 + rng.nextInt(math.min(20, src.length - start - 1))
          src.substring(0, start) + src.substring(start + len)
        }
      case 1 => // swap two random lines
        if (lines.size < 2) src
        else {
          val i = rng.nextInt(lines.size); val j = rng.nextInt(lines.size)
          lines.updated(i, lines(j)).updated(j, lines(i)).mkString("\n")
        }
      case 2 => // remove a random brace
        val braces = src.zipWithIndex.collect { case (c, i) if c == '{' || c == '}' => i }
        if (braces.isEmpty) src
        else { val i = braces(rng.nextInt(braces.size)); src.substring(0, i) + src.substring(i + 1) }
      case 3 => // truncate at a random point
        if (src.length < 10) src else src.substring(0, 5 + rng.nextInt(src.length - 5))
      case 4 => // replace a random identifier-ish token with null
        val words = "[A-Za-z][A-Za-z0-9]*".r.findAllMatchIn(src).toVector
        if (words.isEmpty) src
        else {
          val m = words(rng.nextInt(words.size))
          src.substring(0, m.start) + "null" + src.substring(m.end)
        }
      case 5 => // duplicate a random line
        if (lines.isEmpty) src
        else { val i = rng.nextInt(lines.size); (lines.take(i + 1) ++ Vector(lines(i)) ++ lines.drop(i + 1)).mkString("\n") }
      case 6 => // replace a random type-ish word with a primitive
        src.replaceFirst("String", "Int")
    }
  }

  it("no mutant of the run/ examples crashes the compiler") {
    val rng = new Random(SEED)
    val seeds = Option(new File("run").listFiles())
      .map(_.toSeq.filter(_.getName.endsWith(".on")).sortBy(_.getName)).getOrElse(Seq.empty)
    assert(seeds.nonEmpty, "no seed programs found in run/")

    val outDir = new File("/tmp/onion-fuzz")
    val crashers = scala.collection.mutable.Buffer[(String, String)]()
    for (seed <- seeds) {
      val original = scala.io.Source.fromFile(seed, "UTF-8").mkString
      for (i <- 0 until MUTATIONS_PER_SEED) {
        var mutated = original
        val mutations = 1 + rng.nextInt(3)
        for (_ <- 0 until mutations) mutated = mutate(mutated, rng)
        compileForCrash(mutated).foreach { msg =>
          outDir.mkdirs()
          val name = s"${seed.getName.stripSuffix(".on")}-$i.on"
          java.nio.file.Files.writeString(new File(outDir, name).toPath, mutated)
          crashers += ((name, msg))
        }
      }
    }

    if (crashers.nonEmpty) {
      val bySignature = crashers.groupBy(_._2.replaceAll("Location\\([^)]*\\)", "Location(..)").take(140))
      val report = bySignature.toSeq.sortBy(-_._2.size).map { case (sig, items) =>
        s"${items.size}x: $sig (e.g. /tmp/onion-fuzz/${items.head._1})"
      }.mkString("\n  ")
      fail(s"${crashers.size} mutants crashed the compiler:\n  $report")
    }
  }
}
