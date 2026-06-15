# 言語概要

OnionはJVM上で動く静的型付きオブジェクト指向言語です。Javaライブラリとシームレスに連携しながら、簡潔な構文で型安全なコードを書けます。

## 設計思想

1. **静的型安全** — コンパイル時にエラーを検出
2. **Javaとの相互運用** — 既存のJavaライブラリをそのまま利用
3. **簡潔な構文** — 定型コードを減らしつつ可読性を維持
4. **JVMのパフォーマンス** — 成熟したJVMエコシステムを活用

## 主な特徴

### 静的型付き

すべての変数と式にコンパイル時に型が決まります：

```onion
val name = "Alice"    // String と推論
val age: Int = 30
val scores: Int[] = new Int[10]
```

型の種類：
- プリミティブ型：`Int`、`Long`、`Double`、`Float`、`Boolean`、`Byte`、`Short`、`Char`
- 参照型：クラス・インターフェース
- 配列型：`Type[]`
- Nullable型：`Type?`

### オブジェクト指向

クラス、継承、インターフェースを完全サポートします：

```onion
class Animal {
  val name: String

  public:
    def this(n: String) {
      this.name = n
    }

    def speak: String = "Some sound"
}

class Dog : Animal {
  public:
    def this(n: String): (n) { }

    def speak: String = "Woof!"
}
```

### 関数型の要素

ラムダ式とクロージャも使えます：

```onion
val double = (x: Int) -> x * 2

def makeCounter(): () -> Int {
  var count: Int = 0
  return () -> {
    count = count + 1
    return count;
  };
}
```

### コンパイルモデル

```
ソースファイル (.on)
    ↓
[構文解析] JavaCC → 型なしAST
    ↓
[書き換え] 正規化
    ↓
[型検査] 型推論・検証 → 型付きAST
    ↓
[コード生成] ASM → .class ファイル
```

実行方法は3種類：
- `onionc` — `.class` ファイルにコンパイル
- `onion` — インメモリでコンパイルしてすぐ実行
- `Shell` — インタラクティブREPL

## Javaとの主な違い

| 機能 | Java | Onion |
|------|------|-------|
| フィールド宣言 | `Type field` | `val/var field: Type` |
| 変数宣言 | `Type variable` | `val/var variable[: Type] = value` |
| 静的アクセス | `Class.method()` | `Class::method()` |
| 型キャスト | `(Type) value` | `value as Type` |
| パターンマッチング | `switch` | `select` |
| リスト追加 | `list.add(x)` | `list << x` |

## 次のステップ

- [基本構文](basic-syntax.md) - 構文の基礎を学ぶ
- [クラスとオブジェクト](classes-and-objects.md) - オブジェクト指向プログラミング
- [Javaとの相互運用](java-interop.md) - Javaライブラリの活用
