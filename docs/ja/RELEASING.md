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
   - その後、別のジョブで `org.onion-lang:onion` と `org.onion-lang:onion-llm` を
     Maven Central に公開（[Maven Central への公開](#publishing-to-maven-central)を参照。
     シークレットが揃うまでは notice を残すだけ）

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

## Maven Central への公開 {#publishing-to-maven-central}

リリースのたびに、2つのアーティファクトを Maven Central にも公開します:

| アーティファクト | 内容 |
|---|---|
| `org.onion-lang:onion` | コンパイラ、ランタイムライブラリ、ツールを通常のライブラリ jar（fat jar ではない）として。POM には依存（`scala3-library_3`、ASM、JLine、LSP4J、tomlj、coursier `interface` など）が並ぶ |
| `org.onion-lang:onion-llm` | [LLM バッテリー](batteries/llm.md)。`com.anthropic:anthropic-java` に依存し、`org.onion-lang:onion` には `provided` で依存する |

どちらにも `-sources.jar`、`-javadoc.jar`、`.asc` 署名、`.md5`/`.sha1` チェックサムが
付きます。`org.onion-lang` なのは Maven の groupId だけで、パッケージ名は `onion.*` の
ままです。

### 仕組み

- [リリースワークフロー](https://github.com/onion-lang/onion/blob/develop/.github/workflows/release.yml)の `publish-central` ジョブが、`release` ジョブの後に
  同じタグで動きます。署名鍵をインポートして `sbt centralStage sonaUpload` を実行します。
  `centralStage` はバージョンを検査してから両モジュールの `publishSigned` を
  `target/sona-staging` に対して実行し、`sonaUpload`（sbt 2 の組み込み）がその
  ディレクトリを zip にして、1つのデプロイメントとして Central Portal にアップロード
  します。署名は sbt 2 向けビルドの [sbt-pgp](https://github.com/sbt/sbt-pgp) 2.3.2 が
  `gpg` コマンドで行います。
- **既定は手動リリース。** `sonaUpload` はデプロイメントを検証済み・未公開の状態で
  残します。<https://central.sonatype.com/publishing/deployments> を開いて **Publish**
  （または **Drop**）を押してください。Central へのリリースは永続的で、削除も差し替えも
  できないので、最初の何回かは人の目を通します。それが何度か問題なく済んだら、
  リポジトリ変数 `CENTRAL_AUTO_RELEASE` を `true` にすると（Settings → Secrets and
  variables → Actions → Variables）、ジョブは待たずに公開する `sonaRelease` を
  実行するようになります。
- **シークレットがなければ**、ジョブは「Maven Central publishing skipped」という
  notice を出して成功するだけで、GitHub リリースには影響しません。4つのシークレットの
  一部だけが設定されているときは失敗するので、設定の途中で止まっていれば気づけます。
- **リリースバージョンだけ。** `centralStage`、`publish`、`publishSigned`、
  `sonaUpload`、`sonaRelease` は、素のリリースバージョン（`0.137.0`、`0.138.0-RC1`）
  以外を拒否します。タグのないコミットに sbt-dynver が付ける `+<距離>-<sha>`、
  dirty なツリーの日付サフィックス、`-SNAPSHOT` は、何もビルドする前に失敗します。
  `publishM2` と `publishLocal` は影響を受けません。
- ジョブはビルドキャッシュを使わずにタグのソースからビルドし、ステージした
  ディレクトリを確認用にワークフローの成果物（`maven-central-vX.Y.Z`）として残します。

### 初回だけの準備

1. **Central Portal にサインインする。** <https://central.sonatype.com> を開き、GitHub
   で `kmizu` としてサインインします。新しいアカウントは不要です。（以前の Scala
   リリースで使った旧 OSSRH/Sonatype アカウントがあれば、そちらを使っても構いません。）
2. **ネームスペースを追加する。** Publishing → Namespaces → **Add Namespace** で
   `org.onion-lang` を追加します。
3. **DNS で検証する。** Portal がネームスペースの検証キーを表示します。
   `onion-lang.org`（apex、`@`）にその値の TXT レコードを追加し、
   `dig +short TXT onion-lang.org` に現れるのを待ってから **Verify Namespace** を
   押します。検証が済んだらレコードは削除して構いません。
4. **ユーザートークンを生成する。** アカウントメニュー → View Account →
   **Generate User Token**。一度だけ表示されるユーザー名とパスワードが
   `CENTRAL_USERNAME` と `CENTRAL_PASSWORD` です（ログイン情報ではありません）。
5. **GPG 鍵を用意する。** 個人の鍵で構いません。プロジェクト専用の鍵は任意です。
   作る場合は `gpg --full-generate-key`（ed25519 か RSA 4096）で作り、
   `gpg --list-secret-keys --keyid-format long` で ID を確認します。Central が署名の
   検証に使う公開鍵を公開します:
   ```bash
   gpg --keyserver hkps://keys.openpgp.org --send-keys <KEYID>
   # keys.openpgp.org から確認リンクのメールが届くので、確認して鍵のユーザー ID を載せる
   ```
   CI 用に秘密鍵を base64（1行）でエクスポートします:
   ```bash
   gpg --armor --export-secret-keys <KEYID> | base64 | tr -d '\n' > PGP_SECRET.txt
   ```
6. **4つのリポジトリシークレットを追加する**（Settings → Secrets and variables →
   Actions）。`gh` なら値を1つずつ尋ねられます:
   ```bash
   gh secret set CENTRAL_USERNAME --repo onion-lang/onion
   gh secret set CENTRAL_PASSWORD --repo onion-lang/onion
   gh secret set PGP_SECRET --repo onion-lang/onion < PGP_SECRET.txt
   gh secret set PGP_PASSPHRASE --repo onion-lang/onion
   rm PGP_SECRET.txt
   ```
7. **最初のリリースを実行する。** いつもどおり（上の手順4）、タグ `vX.Y.Z` を push
   します。クライアントからのタグ push が HTTP 403 で拒否される環境では、
   `gh workflow run release.yml -f version=vX.Y.Z --ref develop` でワークフローを
   起動します。`publish-central` が成功したら
   <https://central.sonatype.com/publishing/deployments> を開き、デプロイメントに
   `onion` と `onion-llm`（jar、sources、javadoc、pom、それぞれ署名付き）があることを
   確認して **Publish** を押します。ファイルは30分ほどで `repo1.maven.org` に届きます。
   search.maven.org にはもう少しかかります。

### ローカルでのドライラン

同じステージングをローカルで実行でき、何もアップロードしません。リリースタグの上で
（または `set ThisBuild / version := "0.0.0-dryrun"` の後で）、使い捨ての鍵を一時的な
`GNUPGHOME` に置き、そのパスフレーズを `PGP_PASSPHRASE` に入れて:

```bash
sbt centralStage sonaBundle
```

`target/sona-staging/org/onion-lang/` に両モジュールができ、
`target/sona-bundle/bundle.zip` が `sonaUpload` の送るものそのものです。Windows では
sbt-pgp に Git の gpg を指定します: `set Global / PgpKeys.gpgCommand := "C:/Program Files/Git/usr/bin/gpg.exe"`。

### アーティファクトへの依存

`org.onion-lang:onion` には `_3` サフィックスがありません。`%%` ではなく `%` を
使います。Java や Scala から `onion-llm` を使うプログラムは `onion` も宣言します。
バッテリーはそれを `provided` で持っているからです。

```scala
// sbt
libraryDependencies ++= Seq(
  "org.onion-lang" % "onion"     % "<version>",
  "org.onion-lang" % "onion-llm" % "<version>"
)
```

```kotlin
// Gradle (Kotlin DSL)
dependencies {
    implementation("org.onion-lang:onion:<version>")
    implementation("org.onion-lang:onion-llm:<version>")
}
```

```xml
<!-- Maven -->
<dependency>
  <groupId>org.onion-lang</groupId>
  <artifactId>onion</artifactId>
  <version>X.Y.Z</version>
</dependency>
<dependency>
  <groupId>org.onion-lang</groupId>
  <artifactId>onion-llm</artifactId>
  <version>X.Y.Z</version>
</dependency>
```

Onion のスクリプトやプロジェクトはもともと Onion の上で動くので、バッテリーだけを
指定します:

```onion
//> using dep "org.onion-lang:onion-llm:<version>"
```

```toml
# onion.toml
[dependencies]
"org.onion-lang:onion-llm" = "<version>"
```

## ホットフィックスリリース

既にリリースされているバージョンに対するホットフィックスは、リリースタグからブランチを作成し、修正を適用してから新しいパッチタグ（例: `v0.2.1`）をプッシュします。
