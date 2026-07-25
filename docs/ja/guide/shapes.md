# Shape

**shape** は、テキストと型付き値の対応を記述します。一度宣言すれば、パース・書き戻し・
失敗の伝え方が、そのひとつの記述から出てきます。

## 何を解決するのか

ログ行をレコードに読むこと自体は前からできました。

```onion
record Access(ip: String, method: String, path: String, status: Int)
  from re"(\S+) (\w+) (\S+) (\d+)"

val rows = Access::parseAll(logText)
```

1000行のうち5行が壊れている状態でこれを走らせると、995行が返ってきます。残り5行は
**消えます**——数えられず、報告されず、「最初から995行しかなかったファイル」と区別が
つきません。壊れた行に `Access::parse` を呼ぶと `null` が返りますが、これはそもそも
アクセスログでない行に対する戻り値と同じです。

## shape を宣言する

```onion
record Access(ip: String, method: String, path: String, status: Int)
  shape common = re"(\S+) (\w+) (\S+) (\d+)"
```

`shape name = ...` は境界に名前を与えるので、1つのレコードが必要なだけ持てます。

```onion
record Access(ip: String, method: String, path: String, status: Int)
  shape common = re"(\S+) (\w+) (\S+) (\d+)"
  shape tabbed = re"(\S+)\t(\w+)\t(\S+)\t(\d+)"
  shape doc    = json
```

`shape` はソフトキーワードなので、通常の識別子としても使えます。

## 読む

`parse` は `Outcome` を返します。値か、値でない理由**すべて**です。

```onion
val o = Access::common().parse(line)
if o.isOk() { println(o.get().path()) }
else        { println(o.describe()) }
```

defect は「どこから来たか」と「何を期待したか」を知っています。

```onion
Access::common().parse("10.0.0.1 GET")
// 行全体が一致しない:
//   expected "match of /(\S+) (\w+) (\S+) (\d+)/", found "10.0.0.1 GET"

Access::common().parse("10.0.0.1 GET /x abc")
//   status: expected Int, found abc      <- 不一致ではなく、壊れたフィールド
```

この区別こそ `from re"..."` が表現できないものです。どちらにも `null` を返します。

## 複数行を読む

```onion
val each = Access::common().eachLine(logText)

val rows    = Outcome::values(each)     // 読めた995行
val defects = Outcome::defects(each)    // 読めなかった5行、行番号つき

foreach d: Defect in defects {
  println("line " + d.origin().line() + ": " + d.expected())
}
```

部分的な結果に意味がない場合は `lines()` を使ってください。all-or-nothing で、壊れた行の
defect をすべてまとめて報告します。

## 書き戻す

shape はパースと書き戻しの両方をこなし、両者は一致します。

```onion
val s = Access::common()
s.parse(s.print(row)).get() == row     // true
```

ただし、すべてのパターンが書き戻せるわけではありません。`\s+` を区切りに使うと一意な
書き戻し方がない（空白は何個？）ので、その shape は read-only になり、そう答えます。

```onion
record Pt(x: Int, y: Int)
  shape loose = re"(-?\d+)\s+(-?\d+)"

Pt::loose().canPrint()     // false
```

「メソッドが黙って存在しない」ではなく、**問い合わせられる**という点が違います。

## 文書を読む

パターンの代わりにフォーマット名を書くと、成分名をキーとして構造化文書を読みます。

```onion
record Person(name: String, age: Int)
  shape doc = json

Person::doc().parse("{\"age\": 30}")
//   name: expected String, found absent
```

サポートしているのは `json` と `yaml` です。未知の名前はコンパイルエラー（E0076）です。

## ファイルと URL

```onion
val one  = file"person.json".read(Person::doc())
val many = file"access.log".eachLine(Access::common())
val api  = http"https://example.com/p".read(Person::doc())
```

すべての defect がパスまたは URL を持つので、失敗が*どの*リソースのものか分かります。
読めないファイルも例外ではなく defect です。存在しないかもしれないものを読むのは、
境界では普通のことだからです。

メソッド名が `as` ではなく `read` なのは、`as` がキャスト用のキーワードだからです。

## ビルド時に shape を検査する

`law` はコンパイル時に実行されるので、往復の性質を機械検査できます。

```onion
record Pt(x: Int, y: Int)
  shape text = re"(-?\d+),(-?\d+)"
  law roundtrip(p: Pt) { Pt::text().parse(Pt::text().print(p)).get() == p }
```

引数の型からサンプルを生成できない law はエラー（E0074）になります。読み飛ばしません。
実行されない検査が「通った検査」に見えてはいけないからです。

## `Shapes` を直接使うとき

`onion.Shapes::regex` と `::json` は同じ構築を通常の API として公開しています。自分で
宣言していない型に対する shape はこちらで書けます。

## 関連項目

- [スクリプティング](scripting.md) — scheme リテラル、`|>`、auto-CLI
- [言語仕様](../reference/specification.md#records)
- `run/LogReport.on` — 全体を1つのプログラムにしたもの
