# Onion の実用的品質基準

「実用的品質」は意図的に曖昧な表現なので、このファイルではそれを**客観的に測定可能な指標**のセットに固定します。各行には実行可能な測定方法と閾値があり、すべての行が通過したときに言語は基準に達したとみなされます。

ベースライン数値は 2026-07-26 時点（develop @ c2126299）の実測値です。前回のベースライン
（2026-07-26 @ 871fe5a1）は、effect-table / tool-capability / tool-contracts の作業
（#356、#357、#358）が入った後にはすでに乖離していました——テスト数は 2590 と記録されて
いましたが実際は 2644、ガイドは 14/14 に対し 15/15（`docs/guide/tools.md` が #357 で追加）、
診断コードは 77 に対し 80（capability boundary が追加した `E0077`–`E0079`）でした。

| # | 次元 | 測定方法 | 現在値（2026-08-10） | 合格閾値 |
|---|-----------|----------------|----------------------|----------------|
| 1 | テストスイート | `sbt -batch -Duser.language=en test` | 3396 pass / 0 fail / 1 cancelled | 0 failed, 0 skipped |
| 2 | サンプルの健全性 | `SampleCompilesSpec` / `SampleProgramsSpec`（どちらも `run/*.on` 全件をコンパイル） | 145 / 145 compile | すべてコンパイル、rot なし |
| 3 | 大規模プログラム | 100行以上の `run/*.on` をそのまま end-to-end で実行できる数 | 88（AirlineReservation、AuctionHouse、Automaton、BankLedger、BankSystem、Blackjack、BookClub、BrokenLogDemo、BudgetTracker、BugTracker、CarRentalFleet、CensusAnalyzer、CinemaBooking、CipherSuite、ClinicRecords、ConferenceSchedule、ConwayLife、CourseRegistration、DoctorScheduler、EmployeeManager、EspressoShop、EventTicketing、ExpenseAuditor、FitnessTracker、FleetManager、GameOfLife、GameStore、GradeBook、GradeReport、GraphAlgorithms、GraphSearch、HotelReservation、HRSystem、HuffmanCoding、Inventory、InventoryManager、InventoryReport、JobScheduler、KingdomSim、LibraryCatalog、LibrarySystem、LogAnalytics、MathParser、MatrixCalc、MazeSolver、MiniRpg、MovieRecommender、MuseumCollection、MusicFestival、MusicLibrary、NutritionTracker、OrderReport、ParkingGarage、PayrollReport、PerfReview、PetShelter、PharmacySystem、PlaylistManager、PokerHands、PropertyManager、RankedChoice、RecipeBook、RecipeManager、RestaurantOrders、ShapeProcessor、ShipmentTracker、ShoppingCart、SocialNetwork、SortingShowcase、SpaceMission、SpellCheck、StatsApp、StockPortfolio、StudentGradeBook、Sudoku、SudokuSolver、TaskPlanner、TextAnalytics、TextAnalyzer、TicTacToe、TimesheetTracker、TodoManager、ToolDemo、TournamentStandings、TournamentTracker、VirtualMachine、VirtualShell、WeatherReport） | ≥ 5 |
| 4 | 機能網羅性 | 下記のチェックリストが大規模サンプル内で実証されている | 完了 | すべての項目 ✓ |
| 5 | 既知の使い勝手バグ | 実装済みだが到達不能/壊れた機能として未解決のもの | 0 | 0 |
| 6 | ドキュメントの対等性 | `docs/guide` と `docs/ja/guide` の数 + すべてのコードブロックがコンパイル可能 | 15 / 15 | 対等性 + すべてのブロックを検証 |
| 7 | 診断メッセージ | 英語と日本語の `E00xx` コード | 84 | よくあるエラーごとに専用コード |

**`SBT_OPTS` にプロジェクト既定を下回るヒープ値を設定しないでください。** `.jvmopts` は既定を
`-Xmx10g` に固定しています（`run/` サンプル群の増加に伴い 4g から引き上げ済み。CHANGELOG の
0.10.18 を参照）。これを下回るヒープを `SBT_OPTS` に設定すると——以前の悪い例だった
`-Xmx2g` に限らず、`-Xmx4G` も実際に踏まれています——スイートの途中で
`OutOfMemoryError` を起こします（#691 参照）。`SBT_OPTS` を他のフラグのために設定する
場合も、ヒープは `-Xmx10G` 以上を維持してください。

`-Duser.language=en` は重要です。エラーメッセージは二言語で JVM の既定ロケールから解決される
ため、メッセージ文字列を検査するテストは日本語ロケールでは通り、英語ロケールで走るリリース CI
で落ちます。**エラーコード**を検査してください。

cancelled の1件は `-Donion.dist.path` で切られている配布物 smoke テストです。

**実用的品質は、行1〜7がすべて合格したときに達成されます。** これにより、「実用的品質に到達する」という漠然とした目標を検証可能な状態に変換します。

## 行4 — 機能網羅性チェックリスト

機能は、大規模サンプル（`run/`）の少なくとも1つで動作したときに網羅されたとカウントされます。マイクロテストだけでは不十分です。

- [x] records（単純）とデータを持つ enum
- [x] 単純 enum
- [x] コンストラクタとメソッドを持つクラス
- [x] インターフェース + 多態ディスパッチ（ExprEval）
- [x] トップレベルの `def`（ブロック本体と式本体）
- [x] 再帰（末尾位置も含む）
- [x] コレクションパイプライン: map / filter / fold / reduce / sortedBy / groupBy / find / distinct / partition / zip / flatten
- [x] `select` / パターンマッチング
- [x] `if` / `else if` 式
- [x] `while`、範囲に対する `foreach`、Map に対する `foreach`（k, v）
- [x] null チェック付き nullable 型
- [x] val に格納されたクロージャ
- [x] 文字列補間 `#{}`
- [x] try / catch
- [x] 大規模サンプルで非自明に使われるジェネリクス（`StatsApp` — `SafeBox[T]`、`Pair[A,B]`、汎用 `countMatches`）
- [x] 大規模サンプルで使われる拡張メソッド（`StatsApp`、`ShapeProcessor`、`TextAnalyzer`、`TodoManager`）

## 行5 — 現在の未解決使い勝手バグ（追跡中）

未解決なし。

以前追跡していて解決済みのもの:

1. **プリミティブ型の拡張メソッド** — 修正済み。`extension Int { def double(): Int = self * 2 }` と `(5).double()` が動作します。プリミティブ受信側の拡張メソッドはボックス化されたクラス名で登録され、呼び出し時にターゲットをアンボックスしてから静的メソッドを呼び出します。
2. **クラスメソッドから呼ばれるトップレベル関数** — 修正済み。トップレベルの `val`/`var` と関数は合成トップレベルクラスの static メンバーとして出力され、クラスメソッド内の裸識別子/修飾なし呼び出しはこれらの static メンバーにフォールバックします。
3. **定数ナローイングがコンストラクタ引数位置に届かない。** `val b: Byte = 100` はナローイング
   されますが、`Short` 成分に対する `new R(..., -3)` は `E0021`（「constructor applicable for
   R(..., Int) is not found」）になり、`(-3 as Short)` が必要でした。コンストラクタでは修正済み
   （[#374](https://github.com/onion-lang/onion/issues/374)）。同じ抜け穴が通常のメソッド/関数の
   オーバーロード解決（`Short` 引数に対する `takesShort(-3)` が `E0005` になる）にもあることが
   同時に見つかり、あわせて修正しました — どちらも `ConstantNarrowing` ヘルパーを共有します。
