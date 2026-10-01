package onion.tools.project

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Text a command printed, with each platform line separator folded to "\n".
 *
 * The commands end lines with `println`, so on Windows the captured text has CRLF where
 * the specs spell out "\n". On Linux and macOS the separator already is "\n", so this
 * changes nothing there and the specs keep checking the exact line boundaries.
 */
private[project] object Captured:
  def text(bytes: ByteArrayOutputStream): String =
    bytes.toString(UTF_8).replace(System.lineSeparator, "\n")
