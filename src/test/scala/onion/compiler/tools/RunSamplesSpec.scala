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

    it("runs ArchiveDemo.on") {
      assert(Shell.Success(null) == runSample("run/ArchiveDemo.on"))
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

    it("runs Trie.on") {
      assert(Shell.Success(null) == runSample("run/Trie.on"))
    }

    it("runs RegexEngine.on") {
      assert(Shell.Success(null) == runSample("run/RegexEngine.on"))
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

    it("runs SprintPlanner.on") {
      assert(Shell.Success(null) == runSample("run/SprintPlanner.on"))
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

    it("runs BankLedger.on") {
      assert(Shell.Success(null) == runSample("run/BankLedger.on"))
    }

    it("runs Inventory.on") {
      assert(Shell.Success(null) == runSample("run/Inventory.on"))
    }

    it("runs MathParser.on") {
      assert(Shell.Success(null) == runSample("run/MathParser.on"))
    }

    it("runs TicTacToe.on") {
      assert(Shell.Success(null) == runSample("run/TicTacToe.on"))
    }

    it("runs GraphAlgorithms.on") {
      assert(Shell.Success(null) == runSample("run/GraphAlgorithms.on"))
    }

    it("runs GradeBook.on") {
      assert(Shell.Success(null) == runSample("run/GradeBook.on"))
    }

    it("runs GraphSearch.on") {
      assert(Shell.Success(null) == runSample("run/GraphSearch.on"))
    }

    it("runs InventoryReport.on") {
      assert(Shell.Success(null) == runSample("run/InventoryReport.on"))
    }

    it("runs BudgetTracker.on") {
      assert(Shell.Success(null) == runSample("run/BudgetTracker.on"))
    }

    it("runs CensusAnalyzer.on") {
      assert(Shell.Success(null) == runSample("run/CensusAnalyzer.on"))
    }

    it("runs AirlineReservation.on") {
      assert(Shell.Success(null) == runSample("run/AirlineReservation.on"))
    }

    it("runs AuctionHouse.on") {
      assert(Shell.Success(null) == runSample("run/AuctionHouse.on"))
    }

    it("runs BankSystem.on") {
      assert(Shell.Success(null) == runSample("run/BankSystem.on"))
    }

    it("runs Blackjack.on") {
      assert(Shell.Success(null) == runSample("run/Blackjack.on"))
    }

    it("runs BookClub.on") {
      assert(Shell.Success(null) == runSample("run/BookClub.on"))
    }

    it("runs BugTracker.on") {
      assert(Shell.Success(null) == runSample("run/BugTracker.on"))
    }

    it("runs CarRentalFleet.on") {
      assert(Shell.Success(null) == runSample("run/CarRentalFleet.on"))
    }

    it("runs CinemaBooking.on") {
      assert(Shell.Success(null) == runSample("run/CinemaBooking.on"))
    }

    it("runs CipherSuite.on") {
      assert(Shell.Success(null) == runSample("run/CipherSuite.on"))
    }

    it("runs ConferenceSchedule.on") {
      assert(Shell.Success(null) == runSample("run/ConferenceSchedule.on"))
    }

    it("runs ConwayLife.on") {
      assert(Shell.Success(null) == runSample("run/ConwayLife.on"))
    }

    it("runs CourseRegistration.on") {
      assert(Shell.Success(null) == runSample("run/CourseRegistration.on"))
    }

    it("runs DoctorScheduler.on") {
      assert(Shell.Success(null) == runSample("run/DoctorScheduler.on"))
    }

    it("runs EspressoShop.on") {
      assert(Shell.Success(null) == runSample("run/EspressoShop.on"))
    }

    it("runs EventTicketing.on") {
      assert(Shell.Success(null) == runSample("run/EventTicketing.on"))
    }

    it("runs FitnessTracker.on") {
      assert(Shell.Success(null) == runSample("run/FitnessTracker.on"))
    }

    it("runs FleetManager.on") {
      assert(Shell.Success(null) == runSample("run/FleetManager.on"))
    }

    it("runs HRSystem.on") {
      assert(Shell.Success(null) == runSample("run/HRSystem.on"))
    }

    it("runs HotelReservation.on") {
      assert(Shell.Success(null) == runSample("run/HotelReservation.on"))
    }

    it("runs HuffmanCoding.on") {
      assert(Shell.Success(null) == runSample("run/HuffmanCoding.on"))
    }

    it("runs InventoryManager.on") {
      assert(Shell.Success(null) == runSample("run/InventoryManager.on"))
    }

    it("runs JobScheduler.on") {
      assert(Shell.Success(null) == runSample("run/JobScheduler.on"))
    }

    it("runs KingdomSim.on") {
      assert(Shell.Success(null) == runSample("run/KingdomSim.on"))
    }

    it("runs LibraryCatalog.on") {
      assert(Shell.Success(null) == runSample("run/LibraryCatalog.on"))
    }

    it("runs LogAnalytics.on") {
      assert(Shell.Success(null) == runSample("run/LogAnalytics.on"))
    }

    it("runs AccessLogAnalyzer.on and reports deterministic aggregate stats") {
      val (result, output) = runSampleWithStdinCapturingStdout("run/AccessLogAnalyzer.on", "")
      assert(Shell.Success(null) == result)
      assert(output.contains("Parsed 15 entries"))
      assert(output.contains("Total requests : 15"))
      assert(output.contains("Successful     : 9 (60.0%)"))
      assert(output.contains("Errors         : 5 (33.3%)"))
      assert(output.contains("Total bytes    : 25353"))
      assert(output.contains("/api/data                            3 hits  33.0% err  avg 5896.0 B"))
      assert(output.contains("192.168.1.10       5 req  2 err"))
      assert(output.contains("[ALERT] 192.168.1.10 -- multiple errors detected"))
      assert(output.contains("[ALERT] 10.0.0.99 -- multiple errors detected"))
      assert(output.contains("Last error from 192.168.1.10: 500 GET /api/data"))
      assert(output.contains("Successes: 9   Failures: 6"))
      assert(output.contains("Recursive total bytes: 25353"))
      assert(output.contains("Analysis complete."))
    }

    it("runs TryCatchEdgeCases.on") {
      assert(Shell.Success(138) == runSample("run/TryCatchEdgeCases.on"))
    }

    it("runs ChemCalculator.on") {
      assert(Shell.Success(null) == runSample("run/ChemCalculator.on"))
    }

    it("runs CodeContest.on") {
      assert(Shell.Success(null) == runSample("run/CodeContest.on"))
    }

    it("runs CryptoPortfolio.on") {
      assert(Shell.Success(null) == runSample("run/CryptoPortfolio.on"))
    }

    it("runs DependencyResolver.on") {
      assert(Shell.Success(null) == runSample("run/DependencyResolver.on"))
    }

    it("runs DnaAnalyzer.on") {
      assert(Shell.Success(null) == runSample("run/DnaAnalyzer.on"))
    }

    it("runs ElevatorDispatcher.on") {
      assert(Shell.Success(null) == runSample("run/ElevatorDispatcher.on"))
    }

    it("runs GenericLeaderboard.on") {
      assert(Shell.Success(null) == runSample("run/GenericLeaderboard.on"))
    }

    it("runs GeneticSequencer.on") {
      assert(Shell.Success(null) == runSample("run/GeneticSequencer.on"))
    }

    it("runs HospitalWard.on") {
      assert(Shell.Success(null) == runSample("run/HospitalWard.on"))
    }

    it("runs InsuranceClaims.on") {
      assert(Shell.Success(null) == runSample("run/InsuranceClaims.on"))
    }

    it("runs KaraokeNight.on") {
      assert(Shell.Success(null) == runSample("run/KaraokeNight.on"))
    }

    it("runs MarkdownConverter.on") {
      assert(Shell.Success(null) == runSample("run/MarkdownConverter.on"))
    }

    it("runs MiniTypeChecker.on") {
      assert(Shell.Success(null) == runSample("run/MiniTypeChecker.on"))
    }

    it("runs NetworkMonitor.on") {
      assert(Shell.Success(null) == runSample("run/NetworkMonitor.on"))
    }

    it("runs PlantCare.on") {
      assert(Shell.Success(null) == runSample("run/PlantCare.on"))
    }

    it("runs RecipeVault.on") {
      assert(Shell.Success(null) == runSample("run/RecipeVault.on"))
    }

    it("runs RuleEngine.on") {
      assert(Shell.Success(null) == runSample("run/RuleEngine.on"))
    }

    it("runs SortAlgorithms.on") {
      assert(Shell.Success(null) == runSample("run/SortAlgorithms.on"))
    }

    it("runs SupplyChain.on") {
      assert(Shell.Success(null) == runSample("run/SupplyChain.on"))
    }

    it("runs TransitPlanner.on") {
      assert(Shell.Success(null) == runSample("run/TransitPlanner.on"))
    }

    it("runs Automaton.on") {
      assert(Shell.Success(null) == runSample("run/Automaton.on"))
    }

    it("runs EmployeeManager.on") {
      assert(Shell.Success(null) == runSample("run/EmployeeManager.on"))
    }

    it("runs ExpenseAuditor.on: audits expenses and writes a report") {
      val dir = java.nio.file.Files.createTempDirectory("expense-auditor-spec")
      val src = dir.resolve("expenses.txt"); val out = dir.resolve("report.txt")
      java.nio.file.Files.writeString(src,
        "2026-01-05|Travel|120.50|taxi to airport\n2026-01-06|Meals|500.00|team dinner\n")
      def run(args: String*): Shell.Result =
        shell.run(load("run/ExpenseAuditor.on"), "run/ExpenseAuditor.on", args.toArray)

      assert(Shell.Success(0) == run(src.toString, out.toString))
      val report = java.nio.file.Files.readString(out)
      assert(report.nonEmpty, report)
    }

    it("runs GameStore.on") {
      assert(Shell.Success(null) == runSample("run/GameStore.on"))
    }

    it("runs MatrixCalc.on") {
      assert(Shell.Success(null) == runSample("run/MatrixCalc.on"))
    }

    it("runs MazeSolver.on") {
      assert(Shell.Success(null) == runSample("run/MazeSolver.on"))
    }

    it("runs MiniRpg.on") {
      assert(Shell.Success(null) == runSample("run/MiniRpg.on"))
    }

    it("runs MovieRecommender.on") {
      assert(Shell.Success(null) == runSample("run/MovieRecommender.on"))
    }

    it("runs MuseumCollection.on") {
      assert(Shell.Success(null) == runSample("run/MuseumCollection.on"))
    }

    it("runs MusicLibrary.on") {
      assert(Shell.Success(null) == runSample("run/MusicLibrary.on"))
    }

    it("runs NationalParkTracker.on") {
      assert(Shell.Success(null) == runSample("run/NationalParkTracker.on"))
    }

    it("runs NutritionTracker.on") {
      assert(Shell.Success(null) == runSample("run/NutritionTracker.on"))
    }

    it("runs ParkingGarage.on") {
      assert(Shell.Success(null) == runSample("run/ParkingGarage.on"))
    }

    it("runs PayrollReport.on") {
      assert(Shell.Success(null) == runSample("run/PayrollReport.on"))
    }

    it("runs PlaylistManager.on") {
      assert(Shell.Success(null) == runSample("run/PlaylistManager.on"))
    }

    it("runs PokerHands.on") {
      assert(Shell.Success(null) == runSample("run/PokerHands.on"))
    }

    it("runs RankedChoice.on") {
      assert(Shell.Success(null) == runSample("run/RankedChoice.on"))
    }

    it("runs RecipeManager.on") {
      assert(Shell.Success(null) == runSample("run/RecipeManager.on"))
    }

    it("runs RestaurantOrders.on") {
      assert(Shell.Success(null) == runSample("run/RestaurantOrders.on"))
    }

    it("runs ShipmentTracker.on") {
      assert(Shell.Success(null) == runSample("run/ShipmentTracker.on"))
    }

    it("runs ShoppingCart.on") {
      assert(Shell.Success(null) == runSample("run/ShoppingCart.on"))
    }

    it("runs SnippetLibrary.on") {
      assert(Shell.Success(null) == runSample("run/SnippetLibrary.on"))
    }

    it("runs SortingShowcase.on") {
      assert(Shell.Success(null) == runSample("run/SortingShowcase.on"))
    }

    it("runs SpaceMission.on") {
      assert(Shell.Success(null) == runSample("run/SpaceMission.on"))
    }

    it("runs StockPortfolio.on") {
      assert(Shell.Success(null) == runSample("run/StockPortfolio.on"))
    }

    it("runs StudentGradeBook.on") {
      assert(Shell.Success(null) == runSample("run/StudentGradeBook.on"))
    }

    it("runs Sudoku.on") {
      assert(Shell.Success(null) == runSample("run/Sudoku.on"))
    }

    it("runs SudokuSolver.on") {
      assert(Shell.Success(null) == runSample("run/SudokuSolver.on"))
    }

    it("runs TaskPlanner.on") {
      assert(Shell.Success(null) == runSample("run/TaskPlanner.on"))
    }

    it("runs TimesheetTracker.on") {
      assert(Shell.Success(null) == runSample("run/TimesheetTracker.on"))
    }

    it("runs TournamentStandings.on") {
      assert(Shell.Success(null) == runSample("run/TournamentStandings.on"))
    }

    it("runs TournamentTracker.on") {
      assert(Shell.Success(null) == runSample("run/TournamentTracker.on"))
    }

    it("runs LambdaArrows.on") {
      assert(Shell.Success(null) == runSample("run/LambdaArrows.on"))
    }

    it("runs VirtualMachine.on") {
      assert(Shell.Success(null) == runSample("run/VirtualMachine.on"))
    }

    it("runs CacheSimulator.on") {
      assert(Shell.Success(null) == runSample("run/CacheSimulator.on"))
    }

    it("runs VirtualShell.on") {
      assert(Shell.Success(null) == runSample("run/VirtualShell.on"))
    }

    it("runs MiniWebService.on: it answers its own requests and stops") {
      // Binds port 0 on localhost, so this needs no fixed port and is not reachable from
      // the network. The assertions cover the regex route capturing its group, the
      // catch-all falling through to 404, and -- because a non-daemon handler pool would
      // keep the JVM alive after `main` returned -- that the program actually terminates.
      val (result, output) = runSampleWithStdinCapturingStdout("run/MiniWebService.on", "")
      assert(Shell.Success(null) == result)
      assert(output.contains("/health -> HTTP/1.1 200 OK | ok"))
      assert(output.contains("""/users/42 -> HTTP/1.1 200 OK | {"id": 42}"""))
      assert(output.contains("/missing -> HTTP/1.1 404 Not Found | Not Found"))
      assert(output.contains("stopped"))
    }
  }
}
