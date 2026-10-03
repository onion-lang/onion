# Releasing Onion

Onion uses **git tags** to drive releases. The version is derived automatically
from the latest tag by [sbt-dynver](https://github.com/sbt/sbt-dynver), so there
is no manual `version := ...` line to update in `build.sbt`.

## Release checklist

1. **Make sure `develop` is green — in both locales.**
   ```bash
   sbt shutdown && sbt -Duser.language=en testFull
   sbt shutdown && sbt -Duser.language=ja testFull
   ```
   Both parts matter. Diagnostics are bilingual, and release CI runs in English while
   local development is usually `ja_JP`, so a test asserting on message text can pass
   in one locale and fail in the other. Under sbt 2, `test` delegates to `testQuick`
   and reports `No tests to run` on an unchanged tree, and `-D` is only picked up by a
   *freshly started* server — so without `shutdown` and `testFull` this step can look
   green having run nothing.

2. **Decide the next version.**
   Onion follows [Semantic Versioning](https://semver.org/) with milestone and
   RC pre-releases when needed:
   - Patch release: `v0.2.1`
   - Minor release: `v0.3.0`
   - Milestone: `v0.3.0-M1`
   - Release candidate: `v0.3.0-RC1`

3. **Update `CHANGELOG.md`.**
   Add a new section for the release with the date and a summary of user-facing
   changes, bug fixes, and internal improvements.

4. **Start the release.**

   Preferred — trigger the release workflow's `workflow_dispatch` event and let
   it create the tag itself. Pushing a `v*` tag from a client is rejected by the
   repository's tag protection (HTTP 403), which is what left releases stuck for
   months (issue #334), so the workflow creates the tag itself with the Actions
   token instead of relying on a client-side push:

   ```bash
   gh workflow run release.yml -f version=v0.2.0 --ref develop
   gh run watch "$(gh run list --workflow=release.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
   ```

   Without the `gh` CLI (e.g. a session with only the GitHub API/MCP tools), the
   same `workflow_dispatch` event is `POST
   /repos/{owner}/{repo}/actions/workflows/release.yml/dispatches` with
   `{"ref": "develop", "inputs": {"version": "v0.2.0"}}` — a GitHub MCP server
   typically exposes this as an "run workflow" action taking `workflow_id:
   "release.yml"`, `ref: "develop"`, `inputs: {version: "v0.2.0"}`.

   The tag-push path still works for anyone whose credentials are allowed to
   create `v*` refs:

   ```bash
   git checkout develop
   git pull
   git tag -a v0.2.0 -m "Release v0.2.0"
   git push origin v0.2.0
   ```

   Do **not** commit a `## [X.Y.Z]` CHANGELOG heading before the tag exists: if
   the release then fails, the heading has to be reverted, which is exactly the
   release-and-revert loop #334 describes. Confirm the release first, then
   finalize the heading.

5. **Let CI do the rest.**
   The [release workflow](https://github.com/onion-lang/onion/blob/main/.github/workflows/release.yml) will:
   - run the test suite,
   - build the fat jar (`sbt assembly`) and the distribution zip (`sbt dist`),
   - verify the tag matches the sbt-derived version,
   - smoke-test the fat jar,
   - generate SHA-256 checksums,
   - create a GitHub Release with the artifacts and auto-generated notes.
   - then, in a separate job, publish `org.onion-lang:onion` and `org.onion-lang:onion-llm`
     to Maven Central (see [Publishing to Maven Central](#publishing-to-maven-central);
     until its secrets exist this job only leaves a notice).

6. **Verify the release.**
   - Check the GitHub Release page.
   - Download `onion-<version>.jar` and confirm:
     ```bash
     java -cp onion-<version>.jar onion.tools.ScriptRunner run/Hello.on
     ```

## Local artifact inspection

To build the same artifacts locally without creating a release:

```bash
sbt "assembly; dist"
```

Outputs (sbt 2's default layout nests build products under `target/out/<platform>/<scalaVersion>/<project>/`):
- `target/out/jvm/scala-3.3.7/onion/onion-<version>.jar` (fat jar)
- `target/out/jvm/scala-3.3.7/onion/onion-dist-<version>.zip` (distribution archive)

## Publishing to Maven Central

Every release also publishes two artifacts to Maven Central:

| Artifact | What it is |
|---|---|
| `org.onion-lang:onion` | The compiler, runtime library and tools as a normal library jar (not the fat jar), with a POM listing its dependencies: `scala3-library_3`, ASM, JLine, LSP4J, tomlj, coursier `interface`, ... |
| `org.onion-lang:onion-llm` | The [LLM battery](batteries/llm.md). Depends on `com.anthropic:anthropic-java`, and on `org.onion-lang:onion` as `provided` |

Each comes with a `-sources.jar`, a `-javadoc.jar`, `.asc` signatures and `.md5`/`.sha1`
checksums. Only the Maven groupId is `org.onion-lang`; the packages stay `onion.*`.

### How it works

- The `publish-central` job of the [release workflow](https://github.com/onion-lang/onion/blob/develop/.github/workflows/release.yml) runs after the
  `release` job, on the same tag. It imports the signing key and runs
  `sbt centralStage sonaUpload`: `centralStage` checks the version and then runs
  `publishSigned` for both modules into `target/sona-staging`, and `sonaUpload` (built
  into sbt 2) zips that directory and uploads it to the Central Portal as one deployment.
  Signatures come from [sbt-pgp](https://github.com/sbt/sbt-pgp) 2.3.2, the sbt 2 build,
  which drives the `gpg` binary.
- **Manual release by default.** `sonaUpload` leaves the deployment validated but not
  published. Open <https://central.sonatype.com/publishing/deployments> and press
  **Publish** (or **Drop**). A release on Central is permanent: it can never be deleted or
  replaced, so the first releases get a human look. When that has gone well a few times,
  set the repository variable `CENTRAL_AUTO_RELEASE` to `true` (Settings → Secrets and
  variables → Actions → Variables) and the job runs `sonaRelease` instead, which
  publishes without waiting.
- **Without the secrets** the job only prints the notice "Maven Central publishing
  skipped" and succeeds; the GitHub Release is not affected. If only some of the four
  secrets are set it fails, so a half-finished setup does not go unnoticed.
- **Only release versions.** `centralStage`, `publish`, `publishSigned`, `sonaUpload` and
  `sonaRelease` refuse any version that is not a plain release version (`0.137.0`,
  `0.138.0-RC1`): sbt-dynver's `+<distance>-<sha>` versions of an untagged commit, the
  date suffix of a dirty tree and `-SNAPSHOT` all fail before anything is built.
  `publishM2` and `publishLocal` are not affected.
- The job builds from the tag's sources with no build cache, and keeps the staged
  directory as a workflow artifact (`maven-central-vX.Y.Z`) for inspection.

### One-time setup

1. **Sign in to the Central Portal.** Open <https://central.sonatype.com> and sign in
   with GitHub, as `kmizu`. No new account is needed. (A legacy OSSRH/Sonatype account
   from earlier Scala releases, if you have one, can be used instead.)
2. **Add the namespace.** Publishing → Namespaces → **Add Namespace**: `org.onion-lang`.
3. **Verify it with DNS.** The Portal shows a verification key for the namespace. Add a
   TXT record on `onion-lang.org` (the apex, `@`) whose value is that key, wait until
   `dig +short TXT onion-lang.org` shows it, and press **Verify Namespace**. The record
   can be removed once the namespace is verified.
4. **Generate a user token.** Account menu → View Account → **Generate User Token**. The
   username and password it shows once are `CENTRAL_USERNAME` and `CENTRAL_PASSWORD`
   (not your login).
5. **Prepare a GPG key.** Your personal key is fine; a dedicated project key is optional.
   To create one: `gpg --full-generate-key` (ed25519 or RSA 4096), then find its id with
   `gpg --list-secret-keys --keyid-format long`. Publish the public key, which Central
   uses to check the signatures:
   ```bash
   gpg --keyserver hkps://keys.openpgp.org --send-keys <KEYID>
   # keys.openpgp.org then mails you a link; confirm it so the key's user id is listed
   ```
   Export the secret key, base64 on one line, for CI:
   ```bash
   gpg --armor --export-secret-keys <KEYID> | base64 | tr -d '\n' > PGP_SECRET.txt
   ```
6. **Add the four repository secrets** (Settings → Secrets and variables → Actions), or
   with `gh`, which prompts for each value:
   ```bash
   gh secret set CENTRAL_USERNAME --repo onion-lang/onion
   gh secret set CENTRAL_PASSWORD --repo onion-lang/onion
   gh secret set PGP_SECRET --repo onion-lang/onion < PGP_SECRET.txt
   gh secret set PGP_PASSPHRASE --repo onion-lang/onion
   rm PGP_SECRET.txt
   ```
7. **Run the first release** the usual way (step 4 above): push the tag `vX.Y.Z`, or,
   where a client tag push is rejected with HTTP 403, start the workflow with
   `gh workflow run release.yml -f version=vX.Y.Z --ref develop`. When
   `publish-central` is green, open <https://central.sonatype.com/publishing/deployments>,
   check that the deployment holds `onion` and `onion-llm` (jar, sources, javadoc, pom,
   each signed) and press **Publish**. The files reach `repo1.maven.org` within about
   half an hour; search.maven.org takes longer.

### Local dry run

The same staging runs locally and uploads nothing. On a release tag (or after
`set ThisBuild / version := "0.0.0-dryrun"`), with a throwaway key in a temporary
`GNUPGHOME` and its passphrase in `PGP_PASSPHRASE`:

```bash
sbt centralStage sonaBundle
```

`target/sona-staging/org/onion-lang/` then holds both modules and
`target/sona-bundle/bundle.zip` is exactly what `sonaUpload` would send. On Windows,
point sbt-pgp at Git's gpg: `set Global / PgpKeys.gpgCommand := "C:/Program Files/Git/usr/bin/gpg.exe"`.

### Depending on the artifacts

`org.onion-lang:onion` has no `_3` suffix: use `%`, not `%%`. A program that uses
`onion-llm` from Java or Scala also declares `onion`, because the battery has it as
`provided`.

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

An Onion script or project already runs on Onion, so it names only the battery:

```onion
//> using dep "org.onion-lang:onion-llm:<version>"
```

```toml
# onion.toml
[dependencies]
"org.onion-lang:onion-llm" = "<version>"
```

## Hotfix releases

For a hotfix against an already-released version, branch from the release tag,
apply the fix, and push a new patch tag (e.g. `v0.2.1`).
