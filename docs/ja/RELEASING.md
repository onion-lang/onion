# Onion のリリース

Onion は **git タグ** をリリースの起点としています。バージョンは [sbt-dynver](https://github.com/sbt/sbt-dynver) によって最新のタグから自動的に導出されるため、`build.sbt` の `version := ...` を手動で更新する必要はありません。

## リリースチェックリスト

1. **`develop` が両ロケールで green であることを確認する。**
   ```bash
   sbt shutdown && sbt -Duser.language=en testFull
   sbt shutdown && sbt -Duser.language=ja testFull
   ```
   どちらも必要です。診断は二言語で、リリース CI は英語ロケール、ローカル開発は多くの場合
   `ja_JP` なので、メッセージ文字列を検査するテストは片方でだけ通ることがあります。
   sbt 2 では `test` が `testQuick` に委譲され、変更のないツリーでは `No tests to run` を
   返します。また `-D` は**新規起動した**サーバにしか反映されません。そのため `shutdown` と
   `testFull` なしでは、何も実行していないのに green に見えることがあります。

2. **次のバージョンを決める。**
   Onion は [Semantic Versioning](https://semver.org/) に従い、必要に応じてマイルストーンやRCプレリリースを行います。
   - パッチリリース: `v0.2.1`
   - マイナーリリース: `v0.3.0`
   - マイルストーン: `v0.3.0-M1`
   - リリース候補: `v0.3.0-RC1`

3. **`CHANGELOG.md` を更新する。**
   リリース日と、ユーザーに影響する変更、バグ修正、内部改善の概要を含む新しいセクションを追加します。

4. **リリースを開始する。**

   推奨: リリースワークフローの `workflow_dispatch` イベントを起動し、タグは
   ワークフロー自身に作らせる。クライアントから `v*` タグをプッシュすると、
   リポジトリのタグ保護によって HTTP 403 で拒否され、これが何ヶ月もリリースを
   止めていた原因でした（issue #334）。そのためクライアント側のプッシュに頼らず、
   ワークフロー自身が Actions トークンでタグを作成します:

   ```bash
   gh workflow run release.yml -f version=v0.2.0 --ref develop
   gh run watch "$(gh run list --workflow=release.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
   ```

   `gh` CLI が無い場合（GitHub API/MCP ツールしか無いセッションなど）は、同じ
   `workflow_dispatch` イベントを `POST
   /repos/{owner}/{repo}/actions/workflows/release.yml/dispatches` に
   `{"ref": "develop", "inputs": {"version": "v0.2.0"}}` を渡して呼び出せます。
   GitHub MCP サーバでは通常 `workflow_id: "release.yml"`、`ref: "develop"`、
   `inputs: {version: "v0.2.0"}` を取る「ワークフロー実行」アクションとして
   公開されています。

   `v*` refをプッシュできる権限を持つ認証情報であれば、タグをプッシュする方法も
   引き続き使えます:

   ```bash
   git checkout develop
   git pull
   git tag -a v0.2.0 -m "Release v0.2.0"
   git push origin v0.2.0
   ```

   タグが存在する前に `## [X.Y.Z]` の CHANGELOG 見出しをコミットしては
   **いけません**。リリースが失敗した場合に見出しを元に戻す必要が生じ、これは
   まさに #334 が説明しているリリース・差し戻しループです。まずリリースを
   確定させてから、見出しを確定してください。

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
sbt "assembly; dist"
```

出力 (sbt 2 の既定レイアウトではビルド成果物が `target/out/<platform>/<scalaVersion>/<project>/` 以下に配置される):
- `target/out/jvm/scala-3.3.7/onion/onion-<version>.jar` (fat jar)
- `target/out/jvm/scala-3.3.7/onion/onion-dist-<version>.zip` (配布用アーカイブ)

## ホットフィックスリリース

既にリリースされているバージョンに対するホットフィックスは、リリースタグからブランチを作成し、修正を適用してから新しいパッチタグ（例: `v0.2.1`）をプッシュします。
