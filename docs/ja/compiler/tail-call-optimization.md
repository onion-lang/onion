# 末尾呼び出し最適化

## 現在の状態

**検出: ✅ 実装済み**
**変換: ✅ 実装済み**

Onionコンパイラは、コンパイル時に末尾再帰メソッドを検出し、`while(true)` ループへと書き換えます。これにより、末尾位置での自己再帰呼び出しがJVMのコールスタックを消費しなくなり、深い再帰（例: 10,000回以上の呼び出し）による `StackOverflowError` を防ぎます。

このフェーズが書き換えるのは**直接の自己再帰**のみです。複数のメソッド間で成立する相互再帰は、パイプライン上でこの直後に実行される `MutualRecursionOptimization` が別途扱います。

## 仕組み

### 検出フェーズ

コンパイラは各メソッドを分析して末尾再帰呼び出しを特定します。

1. **末尾位置の分析**: 最後の文（または制御フローの分岐内の文）が自己呼び出しかどうかを確認
2. **再帰的探索**: `StatementBlock` と `IfStatement` ノードを再帰的に検索して末尾呼び出しを見つける
3. **メソッド一致の確認**: 呼び出し対象が現在のメソッドと一致することを確認（同じ名前、クラス、パラメータ型）
4. **ディスパッチ安全性**: オーバーライドされ得ないメソッド（private・static・final）のみが対象です。public なオーバーライド可能メソッド内の自己呼び出しは、実行時にサブクラスの override へ動的ディスパッチされる可能性があるため、本体をループへ書き換えると挙動が黙って変わってしまいます。そのため対象から除外されます。
5. **相互再帰の除外**: `@TailRecursive` アノテーションが付いたメソッド（相互再帰の参加者を示す）はここではスキップされ、`MutualRecursionOptimization` に委ねられます。

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
  return factorial(n - 1, n * acc)  // ✅ 検出され、変換される
}
```

### 変換フェーズ

末尾再帰かつ対象条件を満たすと確認されたメソッドは、以下のように書き換えられます。

1. **ループ変数の割り当て**: 各パラメータに対応するループ変数を用意する
2. **パラメータの書き換え**: メソッド本体内のパラメータ参照をすべて対応するループ変数の参照に書き換える
3. **ループの構築**: （書き換え後の）本体を `while(true)` ループで囲む
4. **末尾呼び出しの置換**: 各末尾呼び出しをループ変数への代入に置き換え、そのままループが継続する（`continue` キーワードは不要 — `while(true)` の本体末尾まで到達すると自然に再度ループへ入る）

```onion
// 変換前
def factorial(n: Int, acc: Int): Int {
  if (n <= 1) return acc
  return factorial(n - 1, n * acc)
}

// 変換後（概念的には）
def factorial(n: Int, acc: Int): Int {
  while (true) {
    if (n <= 1) return acc
    val n_next = n - 1
    val acc_next = n * acc
    n = n_next
    acc = acc_next
    // ループ継続
  }
}
```

## 最適化の様子を確認する

`--verbose` フラグを付けてコンパイルすると、どのメソッドが変換され、どのメソッドがなぜスキップされたかを追跡できます。

```bash
sbt 'runScript --verbose your_program.on'
```

出力例:
```
[TCO] Method YourClass.factorial: hasTailCall=true
[TCO] Optimizing tail-recursive method: YourClass.factorial
[TCO] Skipping overridable method: YourClass.someOverridableMethod
[TCO] Skipping @TailRecursive annotated method: YourClass.mutuallyRecursiveMethod
```

## 実装の詳細

### ファイルの場所

- ソース: `src/main/scala/onion/compiler/optimization/TailCallOptimization.scala`
- パイプライン統合: `Typing` と `AsmCodeGeneration` の間、`MutualRecursionOptimization` の直前で実行される

### コンパイラパイプライン

```
Parsing → Rewriting → Typing → [TailCallOptimization] → MutualRecursionOptimization → AsmCodeGeneration
```

## テスト

- スペック: `src/test/scala/onion/compiler/tools/TailCallOptimizationSpec.scala`
- `src/test/run/` 内のサンプルプログラム: `tail_recursion_factorial.on`、`tail_recursion_simple.on`、`tail_recursion_direct.on`、`tail_recursion_private.on`、`tail_recursion_public.on`、`tail_recursion_test.on`

## 参考

- [Tail Call Optimization (Wikipedia)](https://en.wikipedia.org/wiki/Tail_call)
- 関連実装: Scala の `@tailrec` アノテーション
- 同様の最適化を持つ関数型言語: Haskell、Scheme、OCaml
