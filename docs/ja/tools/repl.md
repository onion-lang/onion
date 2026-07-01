# REPL

OnionのREPLは `onion.tools.Repl` によって実装され、言語の主要な対話型シェルです。

## REPLの起動

以下のいずれかの方法で起動できます。

```bash
# 開発時
sbt repl

# ディストリビューションまたはローカルの bin/ から
onion repl
onion-repl
```

## 主な機能

- 継続プロンプト付きの複数行入力
- セッションスコープ内での `module`、`import`、`val`、`var`、`def`、クラス定義
- 評価済み式の永続的な `resN` バインディング
- JLine 3 による履歴管理とシンタックスハイライト
- 同一ソーススナップショットの再検査時に利用されるセッションコンパイルキャッシュ

## コマンド

```text
:help                 ヘルプを表示
:quit / :exit / :q    REPLを終了
:clear                ターミナルをクリア
:history              入力履歴を表示
:imports              現在のモジュール/インポートを表示
:type <expr>          式の推論型を表示
:ast <expr>           式の構文解析ASTを出力
:typed <expr>         式の型付きAST概要を出力
:bytecode <expr>      式の生成JVMバイトコードを出力
:load <file>          ファイルを現在のセッションに読み込む
:time [on|off]        コンパイル/評価時間の計測を切り替え
:classpath            有効なクラスパスを表示
:reset                セッションをリセット
:paste                ペーストモードに入る
```

## 例

### 結果と resN

```text
onion> 2 + 2
res0: Int = 4

onion> res0 * 10
res1: Int = 40
```

### 複数行定義

```text
onion> def factorial(n: Int): Int {
     |   if n <= 1 {
     |     return 1;
     |   } else {
     |     return n * factorial(n - 1);
     |   }
     | }
```

### 型とバイトコードの確認

```text
onion> :type Math::max(1, 2)
Math::max(1, 2): Int

onion> :bytecode 1 + 2
== <generated class> ==
...
```

### ファイルの読み込み

```text
onion> :load run/Hello.on
Hello
```

## 注意事項

- `onion repl` と `onion-repl` は同じ実装を起動します。
- `:time` は、コンパイラのパフォーマンス調査中にREPLのコンパイル/評価レイテンシを比較するのに便利です。
- `:bytecode` はASMトレースでクラス出力を表示するため、ヘルパークラスやクロージャクラスが主要な生成クラスと一緒に表示されることがあります。
