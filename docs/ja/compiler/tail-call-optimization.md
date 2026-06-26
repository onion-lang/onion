# 末尾呼び出し最適化

## 現在の状態（2026-01-26）

**検出: ✅ 実装済み**
**変換: 🚧 計画中**

Onionコンパイラは、コンパイル時に末尾再帰メソッドを特定する末尾呼び出し検出システムを含んでいます。

## 仕組み

### 検出フェーズ

コンパイラはメソッドを分析して末尾再帰呼び出しを特定します。

1. **末尾位置の分析**: 最後の文（または制御フローの分岐内の文）が自己呼び出しかどうかを確認
2. **再帰的探索**: `StatementBlock` と `IfStatement` ノードを再帰的に検索して末尾呼び出しを見つける
3. **メソッド一致の確認**: 呼び出し対象が現在のメソッドと一致することを確認（同じ名前、クラス、パラメータ型）

### サポートされるパターン

検出器は以下の末尾再帰を認識します。

- 直接末尾呼び出し: `return method(args)`
- 条件分岐: if文の `then` と `else` の両方の分岐
- ネストしたブロック: 文ブロックを再帰的に検索

### 例

```onion
def factorial(n: Int, acc: Int): Int {
  if (n <= 1) {
    return acc
  }
  return factorial(n - 1, n * acc)  // ✅ 末尾呼び出しとして検出
}
```

## 検出された末尾呼び出しの表示

`--verbose` フラグを付けてコンパイルすると、どのメソッドが末尾再帰であるかを確認できます。

```bash
sbt 'runScript --verbose your_program.on'
```

出力:
```
[TCO] Detected tail-recursive method: YourClass.factorial
[TCO] Note: Tail call optimization is not yet fully implemented
```

## 実装の詳細

### ファイルの場所

- ソース: `src/main/scala/onion/compiler/optimization/TailCallOptimization.scala`
- パイプライン統合: `Typing` フェーズと `CodeGeneration` フェーズの間に追加

### コンパイラパイプライン

```
Parsing → Rewriting → Typing → [TailCallOptimization] → MutualRecursionOptimization → TypedAstCodeGeneration
```

## 今後の作業

### 計画中の変換

変換フェーズ（末尾再帰をループに変換）には以下が必要です。

1. **ローカル変数の割り当て**: `LocalFrame` を使った一時変数の適切な割り当て
2. **パラメータの書き換え**: メソッド本体全体でパラメータ参照を一時変数参照に変換
3. **ループの構築**: メソッド本体を `while(true)` ループで囲む
4. **末尾呼び出しの置換**: 末尾呼び出しを変数の代入 + continue に置き換える

### 変換の例（計画中）

```onion
// 元のコード
def factorial(n: Int, acc: Int): Int {
  if (n <= 1) {
    return acc
  }
  return factorial(n - 1, n * acc)
}
```

概念的には以下のように変換されます。

```onion
def factorial(n: Int, acc: Int): Int {
  var n_temp: Int = n
  var acc_temp: Int = acc
  while (true) {
    if (n_temp <= 1) {
      return acc_temp
    }
    val n_next = n_temp - 1
    val acc_next = n_temp * acc_temp
    n_temp = n_next
    acc_temp = acc_next
    // ループ継続
  }
}
```

## テスト

テストファイルは `src/test/run/` にあります。
- `tail_recursion_factorial.on` - 末尾再帰による階乗
- `tail_recursion_simple.on` - シンプルなカウントダウンの例
- `tail_recursion_direct.on` - 直接の無限再帰（テスト用）

## 貢献

変換フェーズの実装に貢献したい場合:

1. `TypedAST` ノード構造（特に `LocalFrame`、`RefLocal`、`SetLocal`）を理解する
2. `src/main/scala/onion/compiler/backend/asm/LocalVarContext.scala` の `LocalVarContext` を学習する
3. パラメータ参照を置き換える再帰的な文書き換えを実装する
4. さまざまな末尾再帰パターンの包括的なテストを追加する

## 参考

- [Tail Call Optimization (Wikipedia)](https://en.wikipedia.org/wiki/Tail_call)
- 関連実装: Scala の `@tailrec` アノテーション
- 同様の最適化を持つ関数型言語: Haskell、Scheme、OCaml
