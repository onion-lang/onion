# シンタックスハイライト

Onion は自分の字句面を 3 箇所に書いています。正本はそのうち 1 つだけです。

| 位置づけ | ファイル | 利用者 |
|---|---|---|
| **正本** | `grammar/JJOnionParser.jj` | コンパイラ |
| TextMate 文法 | `vscode-onion/syntaxes/onion.tmLanguage.json` | VS Code とその他の TextMate 系 |
| Pygments lexer | `tools/pygments-onion/` | このドキュメントサイト |

## ハイライタが 2 つある理由

docs サイトは MkDocs で生成しており、ハイライトは Pygments が担当します。Pygments は
TextMate 文法を読めず、Material for MkDocs にも「静的生成のまま TextMate 文法を使う」
公式の経路がありません。そのため同じ字句面を、2 つの言語で、2 つの利用者向けに書いています。

これはドリフトの温床であり、実際に起きていました。Pygments lexer を用意する前、TextMate
文法のカバレッジはおよそ 70% まで腐っていました。`trait`、`instance`、そしてソフトキーワード
（`tool`、`requires`、`shape`、`law`、`example`、`from`、`derive`）が全滅、加えてスキーム付き
生文字列（`re"…"`、`file"…"`、`http"…"`）、`|>` パイプライン演算子、バッククォート識別子も
未対応でした。**言語の一番特徴的な部分だけが色の付いていない状態**で、しかも何も落ちませんでした。

## ドリフトガード

`src/test/scala/onion/compiler/tools/SyntaxHighlightingDriftSpec.scala` が、キーワード集合を
パーサから機械的に導出して、両方のハイライタをそれに従わせます。

- **ハードキーワード**は `TOKEN` ブロックの `<K_NAME: "kw">` から
- **ソフトキーワード**は文法が使う 2 系統のセマンティック先読み、
  `getToken(n).image.equals("x")` と `la("x")` / `la2("x")` ヘルパから。
  両方を走査する必要があります——`in` は `la("in")` の形でしか現れません。

検査は両方向です。順方向はパーサに足してハイライタに足し忘れたキーワードを、逆方向は
パーサから消したのにハイライタに残った腐った項目を捕まえます。さらに、抽出そのものが
言語の差別化要素を今も見つけられているかも検査するので、文法ファイルを再編しても
ガードが素通りすることはありません。

## キーワードを足すとき

1. `grammar/JJOnionParser.jj` に足す。
2. `sbt 'testOnly *SyntaxHighlightingDriftSpec'` を走らせる。キーワード名つきで落ちる。
3. `vscode-onion/syntaxes/onion.tmLanguage.json` と
   `tools/pygments-onion/onion_pygments/lexer.py` に足す。

**ソフト**キーワードの場合は、パーサの先読みが受理する唯一の形にルールを固定してください。
ソフトキーワードはそれ以外の位置では普通の識別子なので、`val shape = 1` は無色のままで
なければいけません。既存のルールは `\btool\b(?=\s+[a-zA-Z_]\w*\s*\()` のような先読みで
これを実現しています。

キーワードのルールは、両ハイライタで意図的にスキームリテラルより先に置いています。
`JJOnionParser.jj` の字句解析器はスキームトークンになりかけたキーワード接頭辞を押し戻すので、
`return"x"` は `return` の呼び出しではなく「キーワード＋文字列」になります。キーワードを
先に照合することでこの挙動を再現しています。

## ローカルでの docs ビルド

```bash
pip install mkdocs-material mkdocs-glightbox ./tools/pygments-onion
mkdocs serve
```

`./tools/pygments-onion` のインストールは省略できません。入れないと Pygments に `onion`
lexer が存在せず、すべての ` ```onion ` フェンスが素のテキストとして描画されます。しかも
**mkdocs は何も警告しません**——ページが見た目だけおかしくなります。

## フェンスの検査

キーワード以外の字句形式が抜けていても、キーワードの穴としては現れません。Pygments の
`Error` トークンとして現れ、それは無装飾のテキストとして描画されるだけでビルドは成功します。
そこでフェンスを直接 lex します。

```bash
python tools/pygments-onion/check_docs.py docs README.md
```

これは `.github/workflows/docs.yml` のデプロイ前と、プルリクエストで実行されます。
シェバンの抜けはこれで見つかりました——5 ページ 7 ブロックが、`#!/usr/bin/env onion` の
`#` に対して静かにエラートークンを出していました。

## GitHub について

` ```onion ` フェンスは **github.com では色が付きません**。そしてこれはこのリポジトリ側からは
直せません。GitHub はフェンスの情報文字列を
[github-linguist](https://github.com/github-linguist/linguist) の言語リストと照合しており、
そこに言語を追加するには、その拡張子のファイルが複数のリポジトリにまたがって 2000 件以上
インデックスされている必要があります。Onion はまるで届きません。`.gitattributes` の
`linguist-language` も助けになりません——あれは*ファイル*の分類を変えるもので、Markdown 内の
フェンスのハイライトには効かないからです。

`.gitattributes` で得られるのは `.on` ファイル自体の色で、これは `run/` 配下のサンプル
プログラムにとって意味があります。表示上は Scala に写像し、リポジトリの言語統計からは外しています。

```
*.on linguist-language=Scala linguist-detectable=false
```

共通する構文——`def`、`val`、`var`、`class`、`extends`、`case`、`enum`、文字列、コメント、数値、
そして Scala に文字列補間があるおかげで `re"…"` まで——は正しく色が付きます。`conforms`、
`foreach`、`select`、`tool` といった Onion 固有のキーワードは付きません。これが他言語の文法を
借りることの正直な代償であり、フェンスのタグを付け替えずに `onion` のまま残している理由でもあります。
`DocExamplesCompileSpec` と readiness の抽出器はどちらもこのタグで Onion コードを見つけているので、
付け替えるとコンパイル検査が黙って止まります。
