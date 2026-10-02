# LLM バッテリー（`onion-llm`）

`onion-llm` は Onion で最初の *バッテリー* です。`onion.jar` の中ではなく、その隣に
独立した Maven アーティファクトとして公開されるオプションのライブラリです。Anthropic
Messages API 経由で Claude を呼び出し、その答えを型付きのレコードとして読み戻します。
読み戻しには、JSON ファイルを読むのに使っているのと同じ `Shape` を使います:

```onion
//> using dep "org.onion_lang:onion-llm:<version>"
//> using repository "file:///C:/Users/you/.m2/repository"   // 当面はローカルインストール
import { onion.llm.Llm; onion.llm.LlmError }

record Action(owner: String, task: String) { shape doc = json }
record Summary(title: String, actions: List[Action], risk: Int?) { shape doc = json }

val claude = Llm::claude().effort("low").system("You summarize meetings.")
val r: Result[Summary, LlmError] = claude.ask(Summary::doc(), "Summarize: " + notes)
select r {
  case ok is Result.Ok:   println(ok.value().title())
  case err is Result.Err:
    select err.error() {
      case e is LlmError.Refusal:   println("declined: " + e.category())
      case e is LlmError.Invalid:   foreach d: Defect in e.defects() { println(d.describe()) }
      case e is LlmError.Truncated: println("output cut at max_tokens")
      case e is LlmError.Api:       println("api " + e.status() + ": " + e.message())
      case e is LlmError.Transport: println("network: " + e.message())
    }
}

val t: Result[String, LlmError] = claude.text("Say hi in Japanese")
```

答えの形を記述するのはレコードだけです。`Summary::doc().jsonSchema()` が構造化出力の
フォーマットとして送られるので、モデルはその形に制約されます。返答は
`Summary::doc().parse(...)` で読むので、それでも形に合わないものは例外ではなく、
`actions[1].owner` のようなパス付きの欠陥（defect）として返ってきます。

## インストール

このバッテリーはまだ公開リポジトリに公開されていません。Onion のチェックアウトから
ローカルの Maven リポジトリにインストールします:

```bash
sbt llm/publishM2
```

バージョンは Onion のビルドのバージョンです（`sbt "print llm/version"`）。リリースタグ
の上ではリリース番号（たとえば `0.135.0`）になります。作業ツリーからだと sbt-dynver
がコミットと日付のサフィックスを付けるので、ローカル用にインストールするときは
打ちやすいバージョンに固定してください:

```bash
sbt 'set llm / version := "0.135.0-local"' llm/publishM2
```

| | |
|---|---|
| 座標 | `org.onion_lang:onion-llm:<version>` |
| パッケージ | `onion.llm`（`Llm`、`Claude`、`LlmError`） |
| 依存 | `com.anthropic:anthropic-java` 2.68.0（公式 Java SDK） |
| Onion ランタイム | `provided`: スクリプトはもともと `onion.jar` 付きで動くので、バッテリーは2つ目のコピーを持ち込みません |

スクリプトでは `//> using dep` でバッテリーを、`//> using repository` でローカル
リポジトリを指定します（[スクリプトランナー](../tools/script-runner.md)を参照）:

```onion
//> using dep "org.onion_lang:onion-llm:0.135.0-local"
//> using repository "file:///C:/Users/you/.m2/repository"
```

プロジェクトでは `onion.toml` に書きます:

```toml
[dependencies]
"org.onion_lang:onion-llm" = "0.135.0-local"

[[repositories]]
url = "file:///C:/Users/you/.m2/repository"
```

`onion.jar` 自体は変わりません。バッテリーと SDK がクラスパスに載るのは、それを
求めたスクリプトとプロジェクトだけです。

## API

`Llm::claude()` は `Claude` を返します。これはクライアントの不変な記述です。ビルダーの
各ステップは新しい `Claude` を返し、レシーバーはそのまま残るので、ベースのクライアントを
共有して用途ごとに特化できます。組み立ては副作用なしで、API を呼ぶのは `ask` と
`text` だけです。

| メソッド | 意味 |
|---|---|
| `Llm::claude()` | 下記のデフォルト |
| `Llm::claude(model)` | モデルだけ変えたデフォルト |
| `.model(id)` | モデル ID。例: `"claude-sonnet-5-5"` |
| `.effort(level)` | `output_config.effort`: `"low"`、`"medium"`、`"high"`、`"xhigh"`、`"max"` |
| `.maxTokens(n)` | `max_tokens`。出力の上限 |
| `.system(text)` | システムプロンプト（`null` で外す） |
| `.fallbacks(on)` | サーバー側の拒否フォールバック。デフォルトはオン |
| `.baseUrl(url)` | 送信先を変える: ゲートウェイ、プロキシ、テスト用のローカルなフェイク |
| `.apiKey(key)` | 環境から解決せずにこのキーを使う |
| `.ask(shape, prompt)` | `Result[T, LlmError]`: `json` shape で読んだ答え |
| `.text(prompt)` | `Result[String, LlmError]`: 自由形式のテキストの答え |

ビルダーのステップに不正な値を渡すと（`effort("extreme")`、`maxTokens(0)`、相対
URL の `baseUrl`）、書いたその場所で `IllegalArgumentException` が投げられます。
JSON Schema を持たない shape を `ask` に渡した場合も同じです。JSON Schema を持つのは
`shape name = json` の shape だけで（`Shape.hasJsonSchema()`）、regex や config の
shape を渡すのはプログラミングエラーであって、レスポンスから気づくべきことでは
ありません。これは `Http::request` のビルダーと同じ方針です。

### デフォルト

| 設定 | デフォルト | 変え方 |
|---|---|---|
| モデル | `claude-opus-5-5` | `.model(...)` |
| effort | `"medium"` | `.effort(...)` |
| `max_tokens` | 16000 | `.maxTokens(...)` |
| 拒否フォールバック | **オン**: `fallbacks: "default"` と beta ヘッダー `server-side-fallback-2026-07-01` | `.fallbacks(false)` |
| 認証情報 | `ANTHROPIC_API_KEY`、または `ant auth login` のプロファイル | `.apiKey(...)` |
| リトライ | SDK のもの: 429、5xx、接続失敗をバックオフ付きで2回リトライ | — |

拒否フォールバックはデフォルトでオンです。安全性分類器がリクエストを断ったとき、
API は拒否を返す代わりに、その拒否カテゴリについて Anthropic が推奨するフォールバック
モデルでサーバー側で実行し直します。`LlmError.Refusal` が見えるのは、チェーンの全モデル
が断ったときだけです。`.fallbacks(false)` にすると `fallbacks` フィールドも beta
ヘッダーも送らないので、断られたリクエストはそのまま拒否として返ってきます。

バッテリーは `thinking` パラメータを一切送りません。Claude Opus 5.5 は常に思考し、
`thinking: {type: "disabled"}` もトークン予算の指定も 400 で拒否します。調整するのは
effort です。effort を下げれば思考も減ります。リクエストは非ストリーミングです。

認証情報は SDK の `fromEnv()` と同じ順で解決されます: `ANTHROPIC_API_KEY`（または
`ANTHROPIC_AUTH_TOKEN`）、なければ `ant auth login` のプロファイル。
`ANTHROPIC_BASE_URL` も尊重され、`.baseUrl(...)` がそれを上書きします。認証情報が
まったくない場合もリクエストは認証なしで送られ、`LlmError.Api` 401
`authentication_error` として返ってきます。

### レスポンスの読み方

各呼び出しは、レスポンスを決まった順で確認します:

1. `stop_reason: "refusal"` は `LlmError.Refusal` になります。API が返していれば
   `stop_details` のカテゴリと説明も付きます。拒否は HTTP 200 なので、コンテンツを
   読む前に確認します。
2. `stop_reason: "max_tokens"`（または `"model_context_window_exceeded"`）は
   `LlmError.Truncated` になり、途中までのテキストを保持します。
3. それ以外では、答えはレスポンスのテキストブロックのテキストです。thinking ブロックは
   読み飛ばします。`text` はそれをそのまま返します。`ask` は `shape.parse` で読み、
   `Outcome.Bad` はすべての欠陥と生テキストを持つ `LlmError.Invalid` になります。

## エラー

`LlmError` は、5つのレコードを入れ子に持つ sealed な Java インターフェースです。
Onion はパターンの中で入れ子の Java 型をドットで書けて、sealed な対象には網羅性を
検査するので、このページ冒頭の `select` に `else` は要りません。ケースを書き漏らすと
コンパイルエラー（E0042）になります。

| ケース | 要素 | いつ |
|---|---|---|
| `LlmError.Transport` | `message()`、`cause()` | HTTP レスポンスが得られなかった: 接続に失敗した・途切れた、レスポンスを読めなかった、設定された認証情報の取得元を解決できなかった |
| `LlmError.Api` | `status()`、`errorType()`、`message()`、`retryable()` | API がエラーステータスで答えた。`errorType()` は API のエラー種別（`"invalid_request_error"`、`"rate_limit_error"`、`"overloaded_error"` など）。`retryable()` は 429 と 5xx で true。SDK はそれらをすでにリトライ済み |
| `LlmError.Refusal` | `category()`、`explanation()` | `stop_reason: "refusal"`。どちらの要素も null のことがある |
| `LlmError.Truncated` | `stopReason()`、`partialText()` | 出力が `max_tokens` に達した: `.maxTokens(...)` を上げる |
| `LlmError.Invalid` | `defects()`、`rawText()` | 答えが shape として読めない |

どのケースにも、1行で説明する `describe()` があります。

API エラーの種別は `type()` ではなく `errorType()` です。`type` は Onion の予約語
なので、`e.type()` は構文解析できません。

エラーは値です。`ask` と `text` はエラーを `Err` に入れて返し、例外は投げません。
例外になるのは上で述べたプログラミングエラー（`IllegalArgumentException`）と、
スレッドへの割り込みだけです。

## 効果と `--plan`

バッテリーの jar は `META-INF/onion/effect-table.txt` に効果テーブルを同梱しています:

```
onion.llm.Llm#*=pure
onion.llm.Claude#*=pure
onion.llm.Claude#ask=net:api.anthropic.com,env:ANTHROPIC_API_KEY
onion.llm.Claude#text=net:api.anthropic.com,env:ANTHROPIC_API_KEY
onion.llm.LlmError#*=pure
```

クライアントの組み立ては `pure` です。`ask` と `text` は `net`（`api.anthropic.com`
宛て）と `env`（`ANTHROPIC_API_KEY` を読む）です。エラーの値は `pure` です。

!!! note "ライブラリ効果テーブルに依存します"
    コンパイラがクラスパス上の jar から効果テーブルを読むこと、そして上の
    `effect:operand` 構文は、*ライブラリ効果テーブル* の変更（ブランチ
    `feat/library-effect-tables`）がマージされてからです。それまではコンパイラはこの
    テーブルを見ないので、バッテリーの呼び出しは `unknown` です（[効果リファレンス](../reference/effects.md)
    を参照）。バッテリーを使う `tool` はそれを明示する必要があります:

    ```onion
    tool summarize(notes: String, out: String): Int
      requires { read(notes), write(out), console, unknown }
    { ... }
    ```

    テーブルが読まれるようになれば、同じ tool は `requires { read(notes), write(out),
    net, env, console }` と宣言でき、`--plan` は API を呼ばずに、ファイルの
    オペランドと並べて `net api.anthropic.com` と `env ANTHROPIC_API_KEY` を表示します。

## API キーなしでテストする

クライアントを、Messages API のように答えるローカルサーバーに向けます。`baseUrl` と
`apiKey` はそのためにあります。ネットワークに出てはいけないテストでは、
`onion.Server` を起動して用意したレスポンスを返させ、終わったら止めます:

```onion
import { onion.llm.Llm; onion.llm.Claude; onion.llm.LlmError }

def summarizeAgainst(fixture: String): Result[Summary, LlmError] {
  val body = Files::readText("tests/fixtures/" + fixture)
  val server = Server::start("127.0.0.1", 0).handleAll { req ->
    Server::status(200, body).withHeader("Content-Type", "application/json")
  }
  try {
    val claude: Claude = Llm::claude().baseUrl("http://127.0.0.1:" + server.port()).apiKey("test-key")
    return claude.ask(Summary::doc(), "notes")
  } finally {
    server.stop()   // JDK の HTTP ディスパッチャスレッドはデーモンではない
  }
}
```

用意するレスポンスは Messages API の形にします: thinking ブロックの後にテキスト
ブロックが続く `content` と、`stop_reason`。拒否なら `content` は空で、`stop_reason`
は `"refusal"`、`stop_details` に `category` と `explanation` を入れます。

## バッテリーのビルド

バッテリーは sbt のサブプロジェクト `llm`（`batteries/llm`）です。ルートプロジェクトは
これを集約（aggregate）も依存もしないので、`sbt test`、`sbt assembly`、`sbt dist` は
変わりません。名前を指定して扱います:

```bash
sbt llm/test          # Java API と Onion スクリプトのテスト（ローカルのフェイク API 相手）
sbt llm/package       # target/out/jvm/u/onion-llm/onion-llm-<version>.jar
sbt llm/publishM2     # ~/.m2/repository にインストール
```

テストがネットワークに出ることはありません。クライアントをローカルの
`com.sun.net.httpserver.HttpServer` に向け、それが用意した Messages API の JSON を
返して各リクエストを記録するので、送った内容を検査できます: モデル、
`output_config.effort`、`output_config.format` としての shape の JSON Schema、
`fallbacks: "default"`、beta ヘッダー。
