package onion.tools.project

import java.util.Locale

/**
 * The one rule for what a declared dependency version may look like, shared by
 * `onion.toml`'s `[dependencies]` ([[ProjectManifest]]) and a script's `//> using dep`
 * directives (`onion.tools.ScriptDirectives`), so the two can never disagree about it.
 *
 * A declared version must be *exact*: it names one artifact, not a rule for picking one.
 * Coursier would happily resolve each of the following, but the version it picked could then
 * move underneath a manifest or a script whose text never changed:
 *
 *  - Maven version ranges: `[1.0,2.0)`, `(,1.0]`, `[1.0,)`, `[1.5]` (any `[`, `]`, `(`, `)`
 *    or `,`, which also covers Ivy's `]1.0,2.0[` spelling);
 *  - Ivy/Gradle dynamic revisions: `1.+`, `1.2.+`, `+`;
 *  - moving aliases: `LATEST`, `RELEASE`, `latest.release`, `latest.integration`,
 *    `latest.milestone` (anything starting with `latest`, in any case);
 *  - anything empty or containing whitespace, which is not a version at all.
 *
 * Deliberately *not* rejected: a `+` inside SemVer build metadata (`1.0.0+build.5`, which
 * does not end in `+`), and `-SNAPSHOT` versions, which are exact coordinates even though
 * a repository may republish them.
 */
object DependencyVersion:

  def isExact(version: String): Boolean =
    val lower = version.toLowerCase(Locale.ROOT)
    version.nonEmpty &&
      !version.exists(c => Character.isWhitespace(c) || "[](),".indexOf(c) >= 0) &&
      !version.endsWith("+") &&
      !lower.startsWith("latest") &&
      lower != "release"
