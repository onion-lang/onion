# パーサーリファクタリング: 文法から AST 構築の分離

## 概要

このリファクタリングは、Builder パターンを導入して、Onion コンパイラでの解析の関心事と AST 構築を分離します。この分離にはいくつかの利点があります。

1. **テスト容易性**: AST 構築はパースなしで独立してテストできる
2. **柔軟性**: 異なる目的のために異なる AST ビルダーを使える
3. **保守性**: 文法の変更が AST 構築の変更を必要とせず、その逆も同様
4. **拡張性**: パーサーを修正せずに新しい動作を追加できる

## アーキテクチャ

### リファクタリング前

元のパーサー（`JJOnionParser.jj`）は、文法ルール内で直接 AST ノードを構築していました。

```java
AST.ClassDeclaration class_decl(int mset) : {
  // ... 変数宣言 ...
}{
  t1="class" t2=<ID> /* ... パース ... */ {
    return new AST.ClassDeclaration(  // 直接 AST 構築
      p(t1), mset, t2.image, ty1, toList(ty2s), sec3, toList(sec2s)
    );
  }
}
```

### リファクタリング後

リファクタリングされたパーサーは `ASTBuilder` インターフェースを使用します。

```java
AST.ClassDeclaration class_decl(int mset) : {
  // ... 変数宣言 ...
}{
  t1="class" t2=<ID> /* ... パース ... */ {
    return builder.createClassDeclaration(  // ビルダーに委譲
      p(t1), mset, t2.image, ty1, toList(ty2s), sec3, toList(sec2s)
    );
  }
}
```

## コンポーネント

### 1. ASTBuilder トレイト（`ASTBuilder.scala`）

AST 構築のインターフェースを定義します。

```scala
trait ASTBuilder {
  def createCompilationUnit(...): AST.CompilationUnit
  def createClassDeclaration(...): AST.ClassDeclaration
  def createMethodDeclaration(...): AST.MethodDeclaration
  // ... その他の AST ノード作成メソッド
}
```

### 2. DefaultASTBuilder（`ASTBuilder.scala`）

単純に AST ノードを構築するデフォルト実装を提供します。

```scala
class DefaultASTBuilder extends ASTBuilder {
  def createClassDeclaration(...) = {
    AST.ClassDeclaration(location, modifiers, name, ...)
  }
}
```

### 3. ASTBuilderAdapter（`ASTBuilderAdapter.java`）

JavaCC とのシームレスな統合のための Java アダプター。

```java
public class ASTBuilderAdapter {
  private final ASTBuilder builder;
  
  // Java-Scala 相互運用の複雑性を処理
  public AST.ClassDeclaration createClassDeclaration(...) {
    return builder.createClassDeclaration(...);
  }
}
```

### 4. JJOnionParserRefactored（`JJOnionParserRefactored.jj`）

直接の AST 構築の代わりに Builder パターンを使用するように修正された JavaCC 文法。

## ユースケース

### 1. カスタム分析

```scala
class AnalyzingASTBuilder extends DefaultASTBuilder {
  var methodCount = 0
  
  override def createMethodDeclaration(...) = {
    methodCount += 1
    super.createMethodDeclaration(...)
  }
}
```

### 2. 検証

```scala
class ValidatingASTBuilder extends DefaultASTBuilder {
  override def createMethodDeclaration(...) = {
    if (args.length > 10) {
      throw new IllegalArgumentException("Too many parameters")
    }
    super.createMethodDeclaration(...)
  }
}
```

### 3. 変換

```scala
class TransformingASTBuilder extends DefaultASTBuilder {
  override def createMethodDeclaration(...) = {
    val modifiedBody = addLogging(body)
    super.createMethodDeclaration(..., modifiedBody)
  }
}
```

### 4. デバッグ

```scala
class LoggingASTBuilder extends DefaultASTBuilder {
  override def createClassDeclaration(...) = {
    println(s"Creating class: $name at $location")
    super.createClassDeclaration(...)
  }
}
```

## 移行戦略

1. **フェーズ1**: ビルダー基盤の作成（完了）
   - ASTBuilder トレイト
   - DefaultASTBuilder 実装
   - Java 相互運用のための ASTBuilderAdapter

2. **フェーズ2**: パーサーの段階的なリファクタリング
   - 単純な構文（リテラル、識別子）から始める
   - 複雑な構文（クラス、メソッド）へ移行
   - 後方互換性を維持

3. **フェーズ3**: 既存コードの更新
   - Parsing.scala を新しいパーサーに対応
   - テストをリファクタリングされたコンポーネントで更新

4. **フェーズ4**: 新機能の活用
   - 検証ビルダーの追加
   - 変換ビルダーの実装
   - 異なるコンパイルモード用の特殊ビルダーの作成

## 実現された利点

1. **関心事の分離**: 文法ルールは構文に集中し、ビルダーは意味に集中
2. **テスト容易性**: AST 構築ロジックをパースなしでユニットテスト可能
3. **拡張性**: カスタムビルダー経由で新しいコンパイル機能を追加可能
4. **保守性**: AST 構造の変更が文法の修正を必要としない
5. **デバッグ**: パーサーに触れずにロギング/トレースを追加可能

## 今後の強化

1. **ビルダー合成**: 複数のビルダーを連鎖して複雑な変換を行う
2. **コンテキスト認識ビルド**: コンパイルコンテキストを保持するビルダー
3. **エラー回復**: より良いエラーメッセージのための部分 AST を構築できるビルダー
4. **最適化**: パース中に早期最適化を実行するビルダー
