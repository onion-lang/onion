# tool と capability

`tool` は境界を持つ関数です。プログラムの外の世界に対して何をしてよいかを最初に宣言し、
コンパイラがそれを守らせます。

```onion
tool ingest(src: String, dst: String): Int
  requires { read(src), write(dst) }
{
  val data = Files::readText(src)
  Files::writeText(dst, data)
  return 0
}
```

`requires` 節は *capability* の一覧です。本体が行ってよい効果を、必要なら作用先のパラメータに
結びつけて並べます。本体が —— どれだけ間接的な呼び出し経由でも —— 宣言していない効果を
行うなら、そのプログラムはコンパイルできません。

## 取り決め：推論はどこでも、宣言は境界だけ

普通の関数に効果の注釈は一切書きません。コンパイラが本体を見て、呼び出しをまたいで合流
させながら、各関数が何をしうるかを推論します（語彙と解析の詳細は
[効果リファレンス](../reference/effects.md)）。効果を宣言する場所はただ一つ ——
`tool` の境界 —— で、検査は宣言と推論を突き合わせます：

```onion
def helper(msg: String): void {
  IO::println(msg)          // 推論: console — 注釈は不要
}

tool speak(msg: String): Int
  requires { console }      // 宣言は境界で一度だけ
{
  helper(msg)               // OK: helper の推論結果 console は節に含まれる
  return 0
}
```

効果多相はなく、普通のシグネチャに効果変数もなく、ハンドラもありません。この抑制は意図的
です。目的はプログラムの縁での検査可能な約束であって、言語全体の効果システムではありません。

## 違反はこう見える

`read` だけを宣言した tool が書き込みをするとコンパイルに失敗し、診断は効果・呼び出し先・
それを持ち込んだ呼び出し箇所を名指しします：

```
ingest.on:5:8: [E0077] tool `sneaky` performs `write` here (calling
onion.Files::writeText) but does not declare it. Add `write` to its
`requires { ... }` clause.
  5 |   Files::writeText(dst, data)
    |        ~~
```

検査は逆方向にも正直です。本体が行使できない capability は飾りではなくエラーです ——
`net` を掲げてネットワークに触れない tool は過大申告で、`E0078` になります。語彙にない
名前や、tool に存在しないパラメータを指す capability は `E0079` です。

## `unknown` は「認める」もので、「ないことにする」ものではない

効果表が保証できない Java メソッドの効果は *unknown* です —— JDK と classpath 上の全 jar を
覆う表は永遠に作れません。unknown を禁止にすれば相互運用が死に、無害と見なせば保証が嘘に
なります。だから `unknown` は他の効果と同じように伝播し、境界で明示的に認める必要が
あります：

```onion
import { java.util.Random; }

tool roll(): Int
  requires { unknown }      // 「保証できないコードを呼ぶ」と声に出して言う
{
  return new Random().nextInt()
}
```

節がなければ呼び出し箇所に `unknown` を名指しする `E0077` が出ます。節があれば、この tool の
契約は正直です ——「これは解析が抑えられない何かをする」。

## tool の実行時コスト：ゼロ

効果は型検査中に検査され、そこで消去されます。`tool` は等価な関数と正確に同じバイトコードに
コンパイルされます —— コンパイラのテストスイートが両者を逆アセンブルして命令単位で比較し、
これを固定しています。境界はコンパイル時の約束であって実行時サンドボックスではありません。
呼び出しコストはゼロで、実行時に何も止めません。保証するのはより狭く、より有用なこと ——
*コンパイルが通った tool は、宣言より多くのことを黙って行えない* —— です。

## 知っておくべき2つの細部

クロージャの効果は、それを最終的に*呼び出す*関数ではなく*生成する*関数に課金されます。
tool が印字するラムダを作るなら、そのラムダが他所でしか呼ばれなくても tool には `console` が
要ります —— 生成箇所こそ検査器が常に見える場所だからです。

そして `tool` / `requires` はソフトキーワードです。`tool name(` だけが宣言を開き、
`requires {` だけが節を開くので、既存コードの識別子としては今までどおり使えます。

## 宣言からコマンドラインへ

トップレベルに tool を宣言していて、自前の `main` もトップレベル文もないスクリプトは、
それ自体がコマンドラインプログラムです。コンパイラがすべてを宣言から導出します：

```bash
$ onion ingest.on --contract
[{"tool":"ingest",
  "params":[{"name":"src","type":"String","role":"positional"},
            {"name":"dst","type":"String","role":"positional"},
            {"name":"count","type":"Int","role":"flag","default":"3"},
            {"name":"loud","type":"Boolean","role":"switch","default":"false"}],
  "returns":"Int",
  "capabilities":["read(src)","write(dst)","console"]}]

$ onion ingest.on --help
usage: ingest.on <src> <dst> [--count <Int>] [--loud]
  <src>                   String
  <dst>                   String
  --count <int>           Int (default: 3)
  --loud                  Boolean (default: false)
  requires: read(src), write(dst), console
```

契約が唯一のソースです。`--contract` はそれをそのまま出力し（エージェントが読むのは
これ）、`--help`・フラグ解析・型付き変換・エラーメッセージはすべて実行時にそこから
導出され、あなたの tool への型付き呼び出しはコンパイル時に同じ宣言から導出されます。
必須パラメータは位置引数、デフォルト付きパラメータは `--name` フラグ（`--count 5` /
`--count=5`）、`Boolean` のデフォルトはスイッチになります。camelCase のパラメータは、
名前そのままのフラグに加えて kebab-case のフラグでも指定できます —— `maxRows` は
`--max-rows` でも `--maxRows` でもよく、`--help` には kebab-case のほうが表示されます。
同じフラグになってしまう2つのパラメータ（`parseURL` と `parseUrl`）はコンパイルエラー（`E0093`）です。
コマンドラインで省略されたデフォルトは元の式として言語内で評価されます — 文字列を経由した
往復はしません。

`--help` やフラグ処理の背後にあるパースと型変換は、生成コードが `onion.Cli` という
より低レベルのランタイムモジュールを呼び出して行っています。このモジュールは直接
使うことも可能です — stdlib リファレンスの [`Cli` モジュール](../reference/stdlib.md#cli-モジュール)
を参照してください。

リテラルでないデフォルト（`= retries() + 1` など）には契約が引用できる値がないため、
そのエントリは `"default"` キーの代わりに `"defaultComputed":true` を持ちます。
`--help` はそれを `(default: computed at call time)` と説明し、`--plan` はそのような
パラメータに紐づく未指定のオペランドについて、値をでっち上げたり黙って省いたりせず
「計算されるデフォルト」だと報告します — `--plan` が他の場所で守っている正直さの
ルールと同じです。

裸の `--` はオプションの終わりを示します。それ以降はどう綴られていてもすべて値なので、
`--` で始まる引数も tool に渡せます。3つのモードフラグ（`--help`、`--contract`、
`--plan`）は排他で、2つ渡すと黙ってどちらかが選ばれるのではなくエラーになります。
tool 名は重複できません（`E0082`）。コマンドラインが持つ選択手段は名前だけだからです。

失敗は `System.exit` ではなく終了コードです。位置引数の不足、宣言型として解析できない
値、未知のオプションは、引数名と期待される型を名指しするメッセージを出して `main` から
`1` を返します。スクリプトが複数の tool を宣言していれば、最初の引数がサブコマンド式に
名前で選択し、契約には tool ごとのエントリが並びます。

すべての `tool` のすべてのパラメータは、CLI に変換可能な型 —
`String`・`Int`・`Long`・`Double`・`Float`・`Boolean`・`Short`・`Byte` —
のいずれかである必要があります。任意の型をコマンドライン文字列から一般的な方法で
パースする手段は無いためです。それ以外の型（たとえば record）のパラメータを持つ
`tool` はコンパイルエラーになり、問題の tool とパラメータを名指しします。呼び出されない
まま黙ってコンパイルが通ってしまうことはありません。

`--help` の capability 行と契約の `capabilities` フィールドはドキュメントではありません —
上の節で検査された宣言そのものです。契約が「してよい」と言うことは、コンパイラが
「それを超えられない」と証明したことです。

## `--plan`：実行せずに「実行したら何が起きるか」

検査済み capability の見返りがこれです。コンパイラは本体が何をしうるかを知っているので、
CLI は*この呼び出し*が何をするかを —— 宣言された効果集合を実際の引数値で具体化して ——
報告し、何も実行せずに終了できます：

```bash
$ onion ingest.on access.log /backup/access.log --plan
plan: `ingest` would
  read    src = access.log
  write   dst = /backup/access.log
  console
(nothing was executed)
```

capability はエフェクトを*パラメータ*に結びつけるので、`write derived from dst = …` は
「書き込みは `dst` を通る」という意味です。「実行がちょうどそのパスに触る」ではありません
（本体はそこから `dst + ".1"` を作れます）。引数は本物の実行とまったく同じに解析されます —— 不正な値は実行が失敗するのと同じように
プランも失敗させます。デフォルトに任せたオペランドは契約のデフォルトを表示します。
正直さの規則は両方向に厳格です。ambient な効果（`console`、`clock`、`env`、`rand`）は
名前だけで表示されます。解析がパラメータに結びつけられなかったオペランドは、推測される
ことなく `(operand not statically known)` と報告されます。そして `unknown` を運ぶ本体は
それを声に出して言います：

```
  unknown  — calls code the analysis cannot characterize; this plan is a lower bound
```

特徴づけられなかったものを黙って省くプランは、プランがないより悪い。これが、この
ドライランを飾りではなく*信頼に足るもの*にしています。シグネチャから導出した CLI は
コモディティですが、検査済み効果集合から導出したプランはそうではありません ——
本体が何をするかを知っている必要があるからです。

### ソースに書かれたオペランド

裸の capability（`net`、`exec`、`env`、`read`、`write`）はパラメータに結びついて
いませんが、そのオペランドはソースにリテラルとして書かれていることがよくあります ——
API のホスト、tool が呼び出すコマンド、読む環境変数。`--plan` はそれを表示します：

```bash
$ onion digest.on out/digest.md gh --plan
plan: `digest` would
  write   derived from out = out/digest.md
  exec    gh … list --repo onion-lang/onion --state all --search … --json number,…
  net     $SLACK_WEBHOOK_URL
  net     api.github.com
  env     SLACK_WEBHOOK_URL
  env     GITHUB_TOKEN
  clock
  console
(nothing was executed; operands are the arguments the effects are
 derived from, not necessarily the exact paths or hosts touched)
```

コンパイラは、tool から到達できるすべての呼び出し（本体、本体が生成するクロージャ、
本体が推移的に呼ぶプログラム内のメソッド）から次を読み取ります：

| 効果 | 表示されるオペランド | 読み取り元 |
|------|----------------------|------------|
| `net` | ホスト（とポート） | `Http::get`/`post`/`put`/`delete`/`postJson`/`getResponse`/`postResponse`/`request`（ビルダーのステップをたどって `send()` まで）の URL、`http"…"` リソース、`Net::connect` |
| `exec` | コマンド（リテラルでない引数は `…`） | `Proc::capture`/`run`/`exec` のコマンドの語、`captureIn`/`runIn`/`execIn` ではディレクトリより後の語 |
| `env` | 変数名 | `System::getenv("NAME")`、`Config::getEnv("NAME", …)` |
| `read`/`write` | パス | `Files` の各操作のパス引数、`file"…"` リソース |

オペランドが「既知」とみなされるのは、文字列リテラル、リテラルで*始まる*連結や補間
（`"https://api.github.com/search?q=" + q` —— このような接頭辞から読むのは URL のホスト
だけで、リテラルがホストの先まで続いている場合に限ります）、そのどちらかで一度だけ代入
されるローカル `val`、そして `$NAME` と表示される `System::getenv("NAME")` です（環境変数に
入れた Webhook URL は `net $SLACK_WEBHOOK_URL` と表示されます）。URL のユーザー情報
（`user:password@`）は決して表示しません。オペランドは1行に1つで、効果名を繰り返し、呼び出しが見つかった順 —— tool 自身の本体が
先、そのあと本体が呼ぶメソッド —— に並びます。

`exec` の行にはコマンド全体が並びます。リテラルの引数はそのまま、完全なリテラルでない
引数（パラメータ、ループ変数、`"updated:>=" + since` など）はそれぞれ `…` になり、
連続する `…` は1つにまとめます。たとえば
`Proc::capture("gh", kind, "list", "--repo", "onion-lang/onion")` は
`exec    gh … list --repo onion-lang/onion` と表示され、上の `digest.on` の行もこうして
できています。72 文字を超えるコマンドは切り詰めて `…` で終わり、空白や引用符を含む
引数はシングルクォートで囲んで表示します。2つの呼び出し箇所から到達する同じコマンドは
1度だけ並びます。

この一覧は**下界**です。それ以外 —— パラメータ、`var`、実行時に計算される値、ヘルパーの
パラメータに渡されたリテラル（引数を呼び出し先まで追うことはしません）—— は推測しません。
ある効果の呼び出し箇所に解析が読めなかったオペランドがあれば、既知のオペランドの後に同じ
効果の `(operand not statically known)` 行が続くので、部分的な一覧が完全な一覧に見える
ことはありません。パラメータに結びついた capability（`write(out)`）では `derived from`
行は変わらず、本体の他の場所にある同じ効果のリテラルオペランドがその下に並びます。

同じ事実は `--contract` にも tool ごとに1つの追加キーとして現れます。何か分かったときだけ
存在し、既存のキーはすべて値も位置も変わりません：

```json
"staticOperands":{"net":{"known":["$SLACK_WEBHOOK_URL","api.github.com"],"unresolved":false}}
```

`unresolved` は、その効果の呼び出し箇所のうち少なくとも1つで、解析がオペランドを決定
できなかったときに `true` になります。`exec` のエントリには `commands` も入ります。
呼び出し箇所ごとの引数列全体で、リテラルでない引数は `null` です。`known` はこれまで
どおりコマンド名の一覧です：

```json
"exec":{"known":["gh"],"unresolved":false,"commands":[["gh",null,"list","--repo","onion-lang/onion","--state","all","--search",null,"--json","number,title,author,url","--limit","100"]]}
```

