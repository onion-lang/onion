package onion.compiler

import java.util.{Locale, ResourceBundle}

/**
 * Loads the diagnostic message bundles pinned to one language, for tests that need to
 * reason about a specific translation rather than about whatever the machine is set to.
 *
 * `ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)` does **not** reliably give
 * you English. There is no `errorMessage_en.properties` — the base
 * `errorMessage.properties` *is* the English one — and when the requested locale has no
 * bundle of its own the JDK falls back to the **default locale** before it falls back to
 * the base file. So on a `ja_JP` machine that call hands back the Japanese bundle, and a
 * test asserting "the Japanese text differs from the English text" ends up comparing
 * Japanese with Japanese and failing.
 *
 * That is not hypothetical: `ErrorMessageJapaneseTranslationSpec` and
 * `ErrorCountMessageSpec` both failed exactly this way the first time the suite was
 * genuinely run under `-Duser.language=ja`. `getNoFallbackControl` removes the
 * default-locale step, so English means English wherever the suite runs.
 *
 * Production code deliberately does the opposite: `onion.compiler.toolbox.Message` uses
 * the ambient default locale, because a user's diagnostics should follow their machine.
 */
object MessageBundles {

  private val NoFallback =
    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)

  /** The English messages: the base `errorMessage.properties`. */
  def english: ResourceBundle =
    ResourceBundle.getBundle("errorMessage", Locale.ROOT, NoFallback)

  /**
   * The Japanese messages: `errorMessage_ja.properties`, with the English bundle as its
   * parent — so a key missing from the Japanese file still resolves, to English, which is
   * exactly what a translation-coverage test wants to be able to see.
   */
  def japanese: ResourceBundle =
    ResourceBundle.getBundle("errorMessage", Locale.JAPANESE, NoFallback)

  /** Formats `key` from `bundle`, in the fixed root locale so numbers do not vary. */
  def format(bundle: ResourceBundle, key: String, args: Any*): String = {
    val formatter = new java.text.MessageFormat(bundle.getString(key), Locale.ROOT)
    formatter.format(args.map(_.asInstanceOf[AnyRef]).toArray)
  }
}
