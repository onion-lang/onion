# Onion Practical-Quality Bar

"Practical quality" is intentionally vague, so this file pins it to a set of
**objectively measurable indicators**. Each row has a measurement you can run
and a threshold; the language has reached the bar when *every* row passes.

Baseline figures are the ground-truth values as of 2026-07-26 (develop @ c2126299),
**re-measured** rather than carried forward — the previous baseline (2026-07-26 @ 871fe5a1)
had already drifted after the effect-table / tool-capability / tool-contracts work (#356,
#357, #358) landed: it recorded 2590 tests against an actual 2644, 14/14 guides against
15/15 (`docs/guide/tools.md` shipped with #357), and 77 diagnostic codes against 80
(`E0077`–`E0079` added by the capability boundary).

| # | Dimension | How to measure | Current (2026-08-20) | Pass threshold |
|---|-----------|----------------|----------------------|----------------|
| 1 | Test suite | `sbt shutdown && sbt -Duser.language=en testFull` (see the note below) | 3886 pass / 0 fail / 1 cancelled | 0 failed, 0 skipped |
| 2 | Sample health | `SampleCompilesSpec` / `SampleProgramsSpec` (both compile every `run/*.on`) | 199 / 199 compile | all compile, no rot |
| 3 | Large programs | count of `run/*.on` ≥ 100 lines that run end-to-end as-is | 139 (AccessLogAnalyzer, AirlineReservation, AstronomyCatalog, AuctionHouse, Automaton, BankLedger, BankSystem, BattleshipSim, Blackjack, BookClub, BrainFuck, BrokenLogDemo, BudgetTracker, BugTracker, CarRentalFleet, CellularAutomata, CensusAnalyzer, ChemCalculator, CinemaBooking, CipherSuite, ClinicRecords, CodeContest, ColorPalette, ConferenceSchedule, ConnectFour, ConwayLife, CourseRegistration, CpuScheduler, CryptoPortfolio, DependencyResolver, DnaAnalyzer, DoctorScheduler, ElevatorDispatcher, EmployeeManager, EspressoShop, EventTicketing, ExpenseAuditor, ExprEval, FamilyTree, FileSystemSim, FitnessTracker, FlashcardDeck, FleetManager, GameOfLife, GameStore, GenericLeaderboard, GeneticAlgorithm, GeneticSequencer, GradeBook, GradeReport, GraphAlgorithms, GraphSearch, HRSystem, HospitalWard, HotelReservation, HuffmanCode, HuffmanCoding, InsuranceClaims, Inventory, InventoryManager, InventoryReport, JobScheduler, KaraokeNight, KingdomSim, LibraryCatalog, LibrarySystem, LispInterp, LogAnalytics, Mandelbrot, MarkdownConverter, MarkovText, Mastermind, MathParser, MatrixCalc, MazeSolver, MiniGit, MiniRpg, MiniTypeChecker, MovieRecommender, MuseumCollection, MusicFestival, MusicLibrary, MusicTheory, NationalParkTracker, NetworkMonitor, NutritionTracker, OrderReport, Othello, PackageDelivery, PackageInstaller, ParkingGarage, PaymentProcessor, PayrollReport, PerfReview, PetShelter, PharmacySystem, PlantCare, PlaylistManager, PokerHands, PolynomialAlgebra, PropertyManager, RankedChoice, RecipeBook, RecipeManager, RecipeVault, RestaurantOrders, RuleEngine, ShapeProcessor, ShipmentTracker, ShoppingCart, SnippetLibrary, SocialNetwork, SortAlgorithms, SortingShowcase, SpaceMission, SpellCheck, SpreadsheetCalc, SprintPlanner, StatsApp, StockPortfolio, StudentGradeBook, Sudoku, SudokuSolver, SupplyChain, TaskPlanner, TerrainGenerator, TextAnalytics, TextAnalyzer, TicTacToe, TimesheetTracker, TodoManager, ToolDemo, TournamentStandings, TournamentTracker, TransitPlanner, VirtualMachine, VirtualShell, WeatherReport, WordSearch) | ≥ 5 |
| 4 | Feature coverage | checklist below demonstrated inside the large samples | complete | every item ✓ |
| 5 | Known usability bugs | implemented-but-unreachable / broken features still open | 0 | 0 |
| 6 | Docs parity | `docs/guide` vs `docs/ja/guide` count + every code block compiles | 15 / 15 | parity + all blocks verified |
| 7 | Diagnostics | distinct `E00xx` codes with EN+JA messages | 88 | every common error has a dedicated code |

**Do not set `SBT_OPTS` to a heap below the project default.** `.jvmopts` pins the
default to `-Xmx10g` (raised from 4g as the `run/` sample corpus grew — see
CHANGELOG 0.10.18). Any `SBT_OPTS` heap lower than that — `-Xmx2g` was one
previous bad recommendation, `-Xmx4G` is another one seen in the wild — lowers
the effective heap below what the full suite needs and makes it die with an
`OutOfMemoryError` partway through (see #691). If you need to override
`SBT_OPTS` for another flag, keep the heap at `-Xmx10G` or higher.

`-Duser.language=en` matters: error messages are bilingual and resolved from the JVM
default locale, so a test asserting on message text passes in a Japanese locale and fails
in release CI, which runs in English. Assert on error **codes**, not localized text.

Two sbt 2 behaviours make that easy to get wrong locally, and both fail *quietly*:

- **`test` is incremental.** It delegates to `testQuick`, so running it a second time with
  no source change prints `No tests to run` and exits 0 — which reads exactly like a pass.
  `testFull` runs everything.
- **`-D` only reaches a fresh server.** The sbt server persists across invocations, so
  `-Duser.language=ja` handed to a server already started under `en` changes nothing;
  `java.util.Locale.getDefault()` keeps reporting the old locale. `sbt shutdown` first.

Together those mean the obvious `sbt -Duser.language=en test` followed by
`sbt -Duser.language=ja test` can report two green runs having tested one locale once.
CI is unaffected: the incremental state lives in `target/`, which `setup-java`'s
`cache: 'sbt'` does not cache, so every CI run starts cold.

The one cancelled test is the distribution smoke test, gated behind `-Donion.dist.path`.

**Practical quality is reached when rows 1–7 all pass.** This turns the open-ended
goal "reach practical quality" into a checkable state.

## Row 4 — feature coverage checklist

A feature counts as covered once it runs inside at least one large sample
(`run/`), not just a micro-test:

- [x] records (plain) and data-carrying enums
- [x] plain enums
- [x] classes with constructors and methods
- [x] interfaces + polymorphic dispatch (FleetManager)
- [x] top-level `def` with block and expression bodies
- [x] recursion (incl. tail position)
- [x] collection pipelines: map / filter / fold / reduce / sortedBy / groupBy / find / distinct / partition / zip / flatten
- [x] `select` / pattern matching
- [x] `if` / `else if` expressions
- [x] `while`, `foreach` over ranges and over `Map` (k, v)
- [x] nullable types with null checks
- [x] closures stored in vals
- [x] string interpolation `#{}`
- [x] try / catch
- [x] generics used non-trivially in a large sample (`StatsApp` — `SafeBox[T]`, `Pair[A,B]`, generic `countMatches`)
- [x] extension methods used in a large sample (`StatsApp`, `ShapeProcessor`, `TextAnalyzer`, `TodoManager`)

## Row 5 — currently open usability bugs (tracked)

None open.

Previously tracked and resolved:

1. **Primitive-type extensions** — fixed. `extension Int { def double(): Int = self * 2 }` and `(5).double()` now work. Extension methods on primitive receivers are registered under the boxed class name and the call target is unboxed before invoking the backing static method.
2. **Top-level function called from a class method** — fixed. Top-level `val`/`var` and functions are emitted as static members of the synthetic top-level class, and bare identifiers / unqualified calls in class methods fall back to these static members.
3. **Constant narrowing does not reach constructor arguments.** `val b: Byte = 100`
   narrows, but `new R(..., -3)` against a `Short` component was `E0021`
   ("constructor applicable for R(..., Int) is not found") and needed `(-3 as Short)`.
   Fixed for constructors ([#374](https://github.com/onion-lang/onion/issues/374)); the same
   gap in ordinary method/function overload resolution (`takesShort(-3)` against a
   `Short` parameter, reported as `E0005`) was found and fixed alongside it — both now
   share the `ConstantNarrowing` helper.
