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
[効果リファレンス](../../reference/effects.md)）。効果を宣言する場所はただ一つ ——
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
`--count=5`）、`Boolean` のデフォルトはスイッチになります。コマンドラインで省略された
デフォルトは元の式として言語内で評価されます — 文字列を経由した往復はしません。

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

引数は本物の実行とまったく同じに解析されます —— 不正な値は実行が失敗するのと同じように
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

