package onion.compiler.tools

import onion.tools.Shell

import scala.io.{Codec, Source}

class RunSamplesSpec extends AbstractShellSpec {
  private def load(path: String): String = {
    val source = Source.fromFile(path)(Codec.UTF8)
    try source.mkString
    finally source.close()
  }

  private def runSample(path: String): Shell.Result =
    shell.run(load(path), path, Array())

  // Only for samples that build their own `BufferedReader` over `System::in` at
  // execution time (not `onion.IO`, whose stdin reader is a static field bound to
  // whatever `System.in` was when that class first loaded).
  private def runSampleWithStdin(path: String, stdin: String): Shell.Result = {
    val originalIn = System.in
    System.setIn(new java.io.ByteArrayInputStream(stdin.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
    try runSample(path)
    finally System.setIn(originalIn)
  }

  private def runSampleWithStdinCapturingStdout(path: String, stdin: String): (Shell.Result, String) = {
    val originalOut = System.out
    val buffer = new java.io.ByteArrayOutputStream()
    System.setOut(new java.io.PrintStream(buffer, true, "UTF-8"))
    try (runSampleWithStdin(path, stdin), buffer.toString("UTF-8"))
    finally System.setOut(originalOut)
  }

  describe("run/ samples") {
    it("runs ValVarInference.on") {
      assert(Shell.Success(60) == runSample("run/ValVarInference.on"))
    }

    it("runs FunctionTypesSample.on") {
      assert(Shell.Success(12) == runSample("run/FunctionTypesSample.on"))
    }

    it("runs PairSample.on") {
      assert(Shell.Success("x") == runSample("run/PairSample.on"))
    }

    it("runs JavaCollectionsSample.on") {
      assert(Shell.Success("a") == runSample("run/JavaCollectionsSample.on"))
    }

    it("runs PrimitivePrint.on") {
      assert(Shell.Success(0) == runSample("run/PrimitivePrint.on"))
    }

    it("runs RecordLaws.on") {
      assert(Shell.Success("ok") == runSample("run/RecordLaws.on"))
    }

    it("runs ToolDemo.on: contract, plan (no effects), then execution") {
      val dir = java.nio.file.Files.createTempDirectory("tool-demo-spec")
      val src = dir.resolve("access.log"); val out = dir.resolve("report.txt")
      java.nio.file.Files.writeString(src,
        "10.0.0.1 GET /index 200\nbroken\n10.0.0.2 GET /about 404\n")
      def run(args: String*): Shell.Result =
        shell.run(load("run/ToolDemo.on"), "run/ToolDemo.on", args.toArray)

      assert(Shell.Success(0) == run("--contract"))
      // --plan binds the real arguments and performs nothing.
      assert(Shell.Success(0) == run(src.toString, out.toString, "--plan"))
      assert(!java.nio.file.Files.exists(out), "--plan wrote the report")
      // The real run writes it.
      assert(Shell.Success(0) == run(src.toString, out.toString, "--top", "1"))
      val report = java.nio.file.Files.readString(out)
      assert(report.contains("lines read:    2"), report)
      assert(report.contains("lines skipped: 1"), report)
      assert(report.contains("line 2"), report)
    }

    it("runs ConfigEditDemo.on: the edit changes exactly one line") {
      val dir = java.nio.file.Files.createTempDirectory("config-edit-spec")
      val conf = dir.resolve("app.conf")
      val original =
        "# app config \u2014 do not commit secrets\nhost = prod.example.com\n\n" +
        "# port history: 8080 until 2025\nport   =   8080\ndebug = false\nowner = kota\n"
      java.nio.file.Files.writeString(conf, original)
      def run(args: String*): Shell.Result =
        shell.run(load("run/ConfigEditDemo.on"), "run/ConfigEditDemo.on", args.toArray)

      // Plan first: nothing changes.
      assert(Shell.Success(0) == run(conf.toString, "9090", "--plan"))
      assert(java.nio.file.Files.readString(conf) == original, "--plan modified the file")
      // The edit changes exactly the port line; every other byte survives.
      assert(Shell.Success(0) == run(conf.toString, "9090"))
      val edited = java.nio.file.Files.readString(conf)
      val diff = original.split("\n", -1).zip(edited.split("\n", -1)).filter { case (a, b) => a != b }
      assert(diff.toSeq == Seq(("port   =   8080", "port   =   9090")), diff.toSeq.toString)
    }

    it("runs FixedWidthDemo.on") {
      // A user-written Shape: prints the padded record, then eachLine splits
      // 2 good rows from 1 bad one. Compiling at all proves the L1 example held.
      assert(runSample("run/FixedWidthDemo.on").isInstanceOf[Shell.Success])
    }

    it("runs BrokenLogDemo.on") {
      // Returns the number of lines it refused to pretend it had read: a thousand-line
      // log with every 200th line truncated.
      assert(Shell.Success(5) == runSample("run/BrokenLogDemo.on"))
    }

    it("runs Primes.on") {
      assert(Shell.Success(null) == runSample("run/Primes.on"))
    }

    it("runs LogSummary.on") {
      assert(Shell.Success(null) == runSample("run/LogSummary.on"))
    }

    it("runs DeptReport.on") {
      assert(Shell.Success(null) == runSample("run/DeptReport.on"))
    }

    it("runs WordStats.on") {
      assert(Shell.Success(null) == runSample("run/WordStats.on"))
    }

    it("runs AdtExpr.on") {
      assert(Shell.Success(null) == runSample("run/AdtExpr.on"))
    }

    it("runs SetOperations.on") {
      assert(Shell.Success("union=[apple, banana, cherry, date] intersection=[banana, cherry] diff=[apple]") ==
        runSample("run/SetOperations.on"))
    }

    it("runs PrimitivePredicate.on") {
      assert(Shell.Success("[1, 3, 5]") == runSample("run/PrimitivePredicate.on"))
    }

    it("runs PrimitiveFunctionalInterfaces.on") {
      assert(Shell.Success("[isPositive(5) = true, answer.get() = 42, doubleIt(21) = 42]") ==
        runSample("run/PrimitiveFunctionalInterfaces.on"))
    }

    it("runs SortWithPrimitiveComparator.on") {
      assert(Shell.Success("[10, 20, 30] [10, 20, 30]") == runSample("run/SortWithPrimitiveComparator.on"))
    }

    it("runs CollectionUtilities.on") {
      assert(Shell.Success("[Bob, Alice, Charlie]") == runSample("run/CollectionUtilities.on"))
    }

    it("runs Bidirectional.on") {
      assert(Shell.Success(null) == runSample("run/Bidirectional.on"))
    }

    it("runs CsvProcessor.on") {
      assert(Shell.Success("Processed 4 rows, average score = 87") == runSample("run/CsvProcessor.on"))
    }

    it("runs DataClass.on") {
      assert(Shell.Success(null) == runSample("run/DataClass.on"))
    }

    it("runs Fibonacci.on") {
      assert(Shell.Success(null) == runSample("run/Fibonacci.on"))
    }

    it("runs FizzBuzz.on") {
      assert(Shell.Success(null) == runSample("run/FizzBuzz.on"))
    }

    it("runs CliArgsDemo.on") {
      def run(args: String*): Shell.Result =
        shell.run(load("run/CliArgsDemo.on"), "run/CliArgsDemo.on", args.toArray)
      assert(Shell.Success("output=out.txt, paths=[a.txt, b.txt], verbose=true") ==
        run("--verbose", "--output=out.txt", "a.txt", "b.txt"))
    }

    it("runs FileWordCounter.on") {
      val dir = java.nio.file.Files.createTempDirectory("file-word-counter-spec")
      val f = dir.resolve("words.txt")
      java.nio.file.Files.writeString(f, "hello world\nfoo bar baz\n\ngood day\n")
      def run(args: String*): Shell.Result =
        shell.run(load("run/FileWordCounter.on"), "run/FileWordCounter.on", args.toArray)
      assert(Shell.Success("Total words: 7") == run(f.toString))
    }

    it("runs Factorial.on") {
      assert(Shell.Success(null) == runSample("run/Factorial.on"))
    }

    it("runs Generics.on") {
      assert(Shell.Success(null) == runSample("run/Generics.on"))
    }

    it("runs List.on") {
      assert(Shell.Success(null) == runSample("run/List.on"))
    }

    it("runs Array.on") {
      assert(Shell.Success(null) == runSample("run/Array.on"))
    }

    it("runs StringCat.on") {
      assert(Shell.Success(null) == runSample("run/StringCat.on"))
    }

    it("runs Foreach.on") {
      assert(Shell.Success(null) == runSample("run/Foreach.on"))
    }

    it("runs Hello.on") {
      assert(Shell.Success(null) == runSample("run/Hello.on"))
    }

    it("runs NullSafety.on") {
      assert(Shell.Success(null) == runSample("run/NullSafety.on"))
    }

    it("runs StaticImports.on") {
      assert(Shell.Success(null) == runSample("run/StaticImports.on"))
    }

    it("runs Delegation.on") {
      assert(Shell.Success(null) == runSample("run/Delegation.on"))
    }

    it("runs Extension.on") {
      assert(Shell.Success(null) == runSample("run/Extension.on"))
    }

    it("runs ExprEval.on") {
      assert(Shell.Success(null) == runSample("run/ExprEval.on"))
    }

    it("runs JavaGenerics.on") {
      assert(Shell.Success(null) == runSample("run/JavaGenerics.on"))
    }

    it("runs Bean.on") {
      assert(Shell.Success(null) == runSample("run/Bean.on"))
    }

    it("runs ResultValidation.on") {
      assert(Shell.Success(null) == runSample("run/ResultValidation.on"))
    }

    it("runs ShapeProcessor.on") {
      assert(Shell.Success(null) == runSample("run/ShapeProcessor.on"))
    }

    it("runs OrderReport.on") {
      assert(Shell.Success(null) == runSample("run/OrderReport.on"))
    }

    it("runs RegexLogParser.on") {
      assert(Shell.Success("Average response time: 65ms") == runSample("run/RegexLogParser.on"))
    }

    it("runs SchemePrefix.on") {
      assert(Shell.Success(null) == runSample("run/SchemePrefix.on"))
    }

    it("runs TextAnalyzer.on") {
      assert(Shell.Success(null) == runSample("run/TextAnalyzer.on"))
    }

    it("runs StatsApp.on") {
      assert(Shell.Success(null) == runSample("run/StatsApp.on"))
    }

    it("runs TodoManager.on") {
      assert(Shell.Success(null) == runSample("run/TodoManager.on"))
    }

    it("runs UnitConverter.on") {
      assert(Shell.Success(null) == runSample("run/UnitConverter.on"))
    }

    it("runs ConfigApp.on") {
      assert(Shell.Success(null) == runSample("run/ConfigApp.on"))
    }

    it("runs JsonYamlShapeDemo.on") {
      assert(Shell.Success(0) == runSample("run/JsonYamlShapeDemo.on"))
    }

    it("runs ShapeFirst.on") {
      assert(Shell.Success(null) == runSample("run/ShapeFirst.on"))
    }

    it("runs HttpJsonClient.on with no args (usage message, no network call)") {
      assert(Shell.Success("Usage: HttpJsonClient <url>") == runSample("run/HttpJsonClient.on"))
    }

    it("runs AsyncDownloader.on") {
      assert(Shell.Success(null) == runSample("run/AsyncDownloader.on"))
    }

    it("runs JsonApiClient.on (network call falls back gracefully when unreachable)") {
      assert(Shell.Success(null) == runSample("run/JsonApiClient.on"))
    }

    it("runs ShellPipeline.on") {
      assert(Shell.Success(null) == runSample("run/ShellPipeline.on"))
    }

    it("runs Select.on") {
      assert(Shell.Success(null) == runSample("run/Select.on"))
    }

    it("runs LineCounter.on") {
      assert(Shell.Success(null) == runSample("run/LineCounter.on"))
    }

    it("runs GuessNumber.on: EOF on the first prompt exits immediately regardless of the random answer") {
      assert(Shell.Success(null) == runSampleWithStdin("run/GuessNumber.on", ""))
    }

    it("runs LineFilter.on") {
      assert(Shell.Success(null) == runSampleWithStdin("run/LineFilter.on", "hello\nworld\n"))
    }

    it("runs TodoApp.on: add, list, done, then quit") {
      assert(Shell.Success(null) ==
        runSampleWithStdin("run/TodoApp.on", "add buy milk\nlist\ndone 1\nlist\nquit\n"))
    }

    it("runs ReadLine.on, reading its answer via onion.IO from the redirected stdin") {
      val (result, output) = runSampleWithStdinCapturingStdout("run/ReadLine.on", "hello there\n")
      assert(Shell.Success(null) == result)
      assert(output.contains("You input: hello there"))
    }

    it("runs SpellCheck.on") {
      assert(Shell.Success(null) == runSample("run/SpellCheck.on"))
    }

    it("runs WeatherReport.on") {
      assert(Shell.Success(null) == runSample("run/WeatherReport.on"))
    }

    it("runs ClinicRecords.on") {
      assert(Shell.Success(null) == runSample("run/ClinicRecords.on"))
    }

    it("runs GradeReport.on") {
      assert(Shell.Success(null) == runSample("run/GradeReport.on"))
    }

    it("runs SocialNetwork.on") {
      assert(Shell.Success(null) == runSample("run/SocialNetwork.on"))
    }

    it("runs TextAnalytics.on") {
      assert(Shell.Success(null) == runSample("run/TextAnalytics.on"))
    }

    it("runs PetShelter.on") {
      assert(Shell.Success(null) == runSample("run/PetShelter.on"))
    }

    it("runs PerfReview.on") {
      assert(Shell.Success(null) == runSample("run/PerfReview.on"))
    }

    it("runs GameOfLife.on") {
      assert(Shell.Success(null) == runSample("run/GameOfLife.on"))
    }

    it("runs RecipeBook.on") {
      assert(Shell.Success(null) == runSample("run/RecipeBook.on"))
    }

    it("runs MusicFestival.on") {
      assert(Shell.Success(null) == runSample("run/MusicFestival.on"))
    }

    it("runs LibrarySystem.on") {
      assert(Shell.Success(null) == runSample("run/LibrarySystem.on"))
    }

    it("runs PharmacySystem.on") {
      assert(Shell.Success(null) == runSample("run/PharmacySystem.on"))
    }

    it("runs PropertyManager.on") {
      assert(Shell.Success(null) == runSample("run/PropertyManager.on"))
    }
  }
}
