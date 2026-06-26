# Onion のリリース

Onion は **git タグ** をリリースの起点としています。バージョンは [sbt-dynver](https://github.com/sbt/sbt-dynver) によって最新のタグから自動的に導出されるため、`build.sbt` の `version := ...` を手動で更新する必要はありません。

## リリースチェックリスト

1. **`develop` が正常であることを確認する。**
   ```bash
   sbt test
   ```

2. **次のバージョンを決める。**
   Onion は [Semantic Versioning](https://semver.org/) に従い、必要に応じてマイルストーンやRCプレリリースを行います。
   - パッチリリース: `v0.2.1`
   - マイナーリリース: `v0.3.0`
   - マイルストーン: `v0.3.0-M1`
   - リリース候補: `v0.3.0-RC1`

3. **`CHANGELOG.md` を更新する。**
   リリース日と、ユーザーに影響する変更、バグ修正、内部改善の概要を含む新しいセクションを追加します。

4. **タグを作成してプッシュする。**
   ```bash
   git checkout develop
   git pull
   git tag -a v0.2.0 -m "Release v0.2.0"
   git push origin v0.2.0
   ```

5. **CI に残りの作業を任せる。**
   [release workflow](https://github.com/onion-lang/onion/blob/main/.github/workflows/release.yml) は以下を実行します。
   - テストスイートの実行
   - fat jar（`sbt assembly`）と配布用ZIP（`sbt dist`）のビルド
   - タグがsbtから導出されたバージョンと一致することの確認
   - fat jarの動作確認
   - SHA-256チェックサムの生成
   - 成果物と自動生成ノート付きGitHubリリースの作成

6. **リリースを確認する。**
   - GitHubリリースページを確認
   - `onion-<version>.jar` をダウンロードして確認:
     ```bash
     java -cp onion-<version>.jar onion.tools.ScriptRunner run/Hello.on
     ```

## ローカルでの成果物確認

リリースを作成せずに同じ成果物をローカルでビルドするには:

```bash
sbt assembly dist
```

出力:
- `target/scala-3.3.7/onion-<version>.jar` (fat jar)
- `target/onion-dist-<version>.zip` (配布用アーカイブ)

## ホットフィックスリリース

既にリリースされているバージョンに対するホットフィックスは、リリースタグからブランチを作成し、修正を適用してから新しいパッチタグ（例: `v0.2.1`）をプッシュします。
