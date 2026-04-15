package onion.tools

import onion.compiler._
import onion.compiler.CompilationOutcome.{Failure, Success}

import java.io.StringReader
import java.nio.charset.Charset
import java.nio.file.Paths

object BenchmarkRunner {
  final case class SampleSet(name: String, files: Seq[String])
  final case class BenchmarkResult(name: String, elapsedMillis: Double, iterations: Int)

  def main(args: Array[String]): Unit = {
    val json = args.contains("--json")
    val iterations = parseIterations(args).getOrElse(3)
    val runner = new BenchmarkRunner(iterations)
    val results = runner.run()
    if (json) println(runner.renderJson(results))
    else println(runner.renderText(results))
  }

  private def parseIterations(args: Array[String]): Option[Int] = {
    args.sliding(2).collectFirst {
      case Array("--iterations", value) => value.toInt
    }
  }
}

final class BenchmarkRunner(private val iterations: Int) {
  import BenchmarkRunner._

  private val encoding = Charset.defaultCharset().name()
  private val repoRoot = Paths.get("").toAbsolutePath.normalize()
  private val compilerConfig = CompilerConfig(Seq("."), "", encoding, "", 20, warningLevel = WarningLevel.Off)
  private val sampleSets = Seq(
    SampleSet("small", Seq("run/Hello.on")),
    SampleSet("medium", Seq("run/TodoApp.on")),
    SampleSet("large", Seq("run/DataClass.on"))
  )

  def run(): Seq[BenchmarkResult] = {
    val compileResults =
      sampleSets.flatMap { sample =>
        Seq(
          benchmark(s"cold-compile:${sample.name}", iterations)(compileSample(sample)),
          benchmark(s"warm-compile:${sample.name}", iterations)(compileSample(sample), warmup = true)
        )
      }

    compileResults ++ Seq(
      benchmark("script-run", iterations)(runScript()),
      benchmark("repl-snippet-eval", iterations)(runReplSnippet())
    )
  }

  def renderText(results: Seq[BenchmarkResult]): String = {
    val builder = new StringBuilder
    builder.append("benchmark").append(System.lineSeparator())
    results.foreach { result =>
      builder.append(f"  ${result.name}%-24s ${result.elapsedMillis}%.2fms avg (${result.iterations} runs)")
      builder.append(System.lineSeparator())
    }
    builder.toString.trim
  }

  def renderJson(results: Seq[BenchmarkResult]): String =
    results.map { result =>
      s"""{"name":"${result.name}","elapsedMillis":${result.elapsedMillis},"iterations":${result.iterations}}"""
    }.mkString("[", ",", "]")

  private def benchmark(name: String, iterations: Int)(block: => Unit, warmup: Boolean = false): BenchmarkResult = {
    if (warmup) block
    val elapsed = (0 until iterations).map { _ =>
      val start = System.nanoTime()
      block
      (System.nanoTime() - start).toDouble / 1000000.0
    }.sum / iterations.toDouble
    BenchmarkResult(name, elapsed, iterations)
  }

  private def compileSample(sample: SampleSet): Unit = {
    val sources = sample.files.map(path => new FileInputSource(repoRoot.resolve(path).toString))
    new OnionCompiler(compilerConfig).compile(sources) match {
      case Success(_) =>
      case Failure(errors) =>
        throw new IllegalStateException(s"Compilation failed for ${sample.name}: ${errors.map(_.message).mkString(", ")}")
    }
  }

  private def runScript(): Unit = {
    val source = repoRoot.resolve("run/Factorial.on").toString
    new OnionCompiler(compilerConfig).compile(Seq(new FileInputSource(source))) match {
      case Success(classes) =>
        captureStdOut {
          new Shell(classOf[OnionClassLoader].getClassLoader, compilerConfig.classPath).run(classes, Array())
        }
      case Failure(errors) =>
        throw new IllegalStateException(s"Script benchmark failed: ${errors.map(_.message).mkString(", ")}")
    }
  }

  private def runReplSnippet(): Unit = {
    val source =
      """import {
        |  java.util.ArrayList;
        |}
        |val xs = new ArrayList();
        |xs.add("hello");
        |val res0 = {
        |  xs.size() + 2
        |}
        |IO::println("res0 = " + res0)
        |""".stripMargin
    new OnionCompiler(compilerConfig).compile(Seq(new StreamInputSource(() => new StringReader(source), "benchmark_repl.on"))) match {
      case Success(classes) =>
        captureStdOut {
          new Shell(classOf[OnionClassLoader].getClassLoader, compilerConfig.classPath).run(classes, Array())
        }
      case Failure(errors) =>
        throw new IllegalStateException(s"REPL benchmark failed: ${errors.map(_.message).mkString(", ")}")
    }
  }

  private def captureStdOut[A](block: => A): A = {
    val original = System.out
    val buffer = new java.io.ByteArrayOutputStream()
    val stream = new java.io.PrintStream(buffer, true, encoding)
    System.setOut(stream)
    try block
    finally {
      stream.flush()
      System.setOut(original)
      stream.close()
    }
  }
}
