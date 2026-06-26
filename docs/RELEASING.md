# Releasing Onion

Onion uses **git tags** to drive releases. The version is derived automatically
from the latest tag by [sbt-dynver](https://github.com/sbt/sbt-dynver), so there
is no manual `version := ...` line to update in `build.sbt`.

## Release checklist

1. **Make sure `develop` is green.**
   ```bash
   sbt test
   ```

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

4. **Create and push a tag.**
   ```bash
   git checkout develop
   git pull
   git tag -a v0.2.0 -m "Release v0.2.0"
   git push origin v0.2.0
   ```

5. **Let CI do the rest.**
   The [release workflow](https://github.com/onion-lang/onion/blob/main/.github/workflows/release.yml) will:
   - run the test suite,
   - build the fat jar (`sbt assembly`) and the distribution zip (`sbt dist`),
   - verify the tag matches the sbt-derived version,
   - smoke-test the fat jar,
   - generate SHA-256 checksums,
   - create a GitHub Release with the artifacts and auto-generated notes.

6. **Verify the release.**
   - Check the GitHub Release page.
   - Download `onion-<version>.jar` and confirm:
     ```bash
     java -cp onion-<version>.jar onion.tools.ScriptRunner run/Hello.on
     ```

## Local artifact inspection

To build the same artifacts locally without creating a release:

```bash
sbt assembly dist
```

Outputs:
- `target/scala-3.3.7/onion-<version>.jar` (fat jar)
- `target/onion-dist-<version>.zip` (distribution archive)

## Hotfix releases

For a hotfix against an already-released version, branch from the release tag,
apply the fix, and push a new patch tag (e.g. `v0.2.1`).
