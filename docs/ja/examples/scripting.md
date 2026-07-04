# スクリプティングとCLIの例

Onionはコマンドラインスクリプトや小さな自動化タスクにも適しています。このページでは、引数解析、プロセス実行、ファイル入出力の実用的なパターンを紹介します。

## コマンドライン引数の解析

`Args` モジュールを使ってフラグ、オプション、位置引数を解析します。

```onion
val parsed = Args::parse(args)

val name: String = parsed.option("name", "World")
val count: Int = parsed.intOption("count", 1)
val verbose: Boolean = parsed.flag("verbose")
val rest: List[String] = parsed.positional()

if verbose {
  println("name=" + name + " count=" + count)
}

for var i: Int = 0; i < count; i = i + 1 {
  println("Hello, " + name + "!")
}
```

実行:

```bash
onion greet.on --name Alice --count 3 --verbose
```

## ファイルの読み書き

`file"..."` リテラル、または動的パスの場合は `file(...)` 関数を使います。

```onion
val content: String = file("input.txt").text()
println("Read " + content.length() + " characters")

file("output.txt").write("Hello from Onion\n")
```

CSVファイルもサポートされています。

```onion
val rows: List[Map[String, String]] = file("data.csv").csvRows()
foreach row: Object in rows {
  val m = row as Map
  println("name=" + m.get("name") + " age=" + m.get("age"))
}
```

## シェルコマンドの実行

`Proc` モジュールを使うと、外部プログラムを実行して出力を取得するのが簡単です。

```onion
val result = Proc::capture("git", "status")
if result.succeeded() {
  println(result.stdout())
} else {
  println("failed: " + result.stderr())
}
```

シェル経由でパイプラインを実行することもできます。

```onion
val sorted = Proc::capture("sh", "-c", "cat words.txt | sort | uniq -c")
println(sorted.stdout())
```

## スクリプトをすっきりさせる static インポート

特定の static メソッドだけをインポートして、クラス名を繰り返さずに済ませられます：

```onion
import { java.lang.Math::max; java.lang.Math::min; }

println(max(10, 20))
println(min(10, 20))
```

クラス全体の static メンバーをインポートすることもできます：

```onion
import { java.lang.Math }

println(max(10, 20))
println(Math::PI)
```

## 完全な例: CLI + 設定ファイル

**`ConfigApp.on`** は引数解析とYAML設定ファイルを組み合わせます。

```onion
record ServerConfig(host: String, port: Int, debug: Boolean) derive!(Yaml)

def defaultConfig(): ServerConfig {
  return new ServerConfig("localhost", 8080, false)
}

val parsed = Args::parse(args)
val configPath: String = parsed.option("config", "")
val portOverride: Int = parsed.intOption("port", -1)
val debugFlag: Boolean = parsed.flag("debug")

val base: ServerConfig =
  if configPath.length() > 0 {
    val loaded = ServerConfig::fromYaml(file(configPath).text())
    if loaded != null { loaded } else { defaultConfig() }
  } else {
    defaultConfig()
  }

val port = if portOverride >= 0 { portOverride } else { base.port() }
val debug = if debugFlag { true } else { base.debug() }

println("host=" + base.host())
println("port=" + port)
println("debug=" + debug)
```

実行:

```bash
onion ConfigApp.on --config server.yaml --port 9000 --debug
```

## プロセスパイプラインの例

**`ShellPipeline.on`** は `wc`、`sort`、`head` をパイプラインとして実行します。

```onion
val inputPath = "words.txt"

val countResult = Proc::capture("wc", "-l", inputPath)
println("wc exit=" + countResult.status() + " out=" + countResult.stdout().trim())

val pipelineResult = Proc::capture("sh", "-c", "sort " + inputPath + " | head -n 3")
println(pipelineResult.stdout())
```

## 拡張メソッドを使った単位変換

**`UnitConverter.on`** は `Double` に単位変換メソッドを追加する拡張メソッドを使います。

```onion
extension Double {
  def celsiusToFahrenheit(): Double {
    return self * 9.0 / 5.0 + 32.0
  }
  def kilometersToMiles(): Double {
    return self * 0.621371
  }
  def rounded(decimals: Int): Double {
    val factor = Math::pow(10.0, decimals as Double)
    return (Math::round(self * factor) as Double) / factor
  }
}

val celsius = 25.0
println(celsius + "C = " + celsius.celsiusToFahrenheit().rounded(2) + "F")
```

## 次のステップ

- [JSONとHTTPの例](json-http.md) - ネットワークとデータ形式のスクリプト
- [エラーハンドリングの例](error-handling.md) - 入力の検証と失敗の処理
- [ツール: スクリプトランナー](../tools/script-runner.md) - スクリプトを直接実行
