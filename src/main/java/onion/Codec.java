package onion;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Text encoding and decoding for Onion programs: Base64, hex, and URL/percent
 * encoding. All methods operate on the UTF-8 bytes of a string and return a
 * string. Call as {@code Codec::base64Encode("text")}.
 */
public final class Codec {
    private Codec() {
    }

    /** Encodes the UTF-8 bytes of {@code text} as a standard Base64 string. */
    public static String base64Encode(String text) {
        if (text == null) return "";
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes a standard Base64 string back to text (UTF-8). Whitespace is skipped, so
     *  text read from a file or wrapped at 76 columns decodes as is.
     *  @throws IllegalArgumentException on a non-Base64 character, or a bad length or padding */
    public static String base64Decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(encoded.length());
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') continue;
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '+' || c == '/' || c == '=';
            if (!ok) {
                throw new IllegalArgumentException("invalid Base64 character '" + c + "' at index " + i);
            }
            sb.append(c);
        }
        try {
            return new String(Base64.getDecoder().decode(sb.toString()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "invalid Base64 length or padding: " + sb.length() + " character(s) after removing whitespace", e);
        }
    }

    /** Encodes the UTF-8 bytes of {@code text} as a lowercase hex string. */
    public static String hexEncode(String text) {
        if (text == null) return "";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** Decodes a hex string (even length, case-insensitive) back to text (UTF-8).
     *  @throws IllegalArgumentException on odd length or a non-hex character */
    public static String hexDecode(String hex) {
        if (hex == null || hex.isEmpty()) return "";
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("hex string has odd length: " + len);
        }
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i + 1 < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("invalid hex digit near index " + i + ": \"" + hex.substring(i, i + 2) + "\"");
            }
            bytes[i / 2] = (byte) ((hi << 4) + lo);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** URL/percent-encodes {@code text} (application/x-www-form-urlencoded, UTF-8). */
    public static String urlEncode(String text) {
        if (text == null) return "";
        return java.net.URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    /** Decodes a URL/percent-encoded string (UTF-8).
     *  @throws IllegalArgumentException on a % not followed by two hex digits */
    public static String urlDecode(String text) {
        if (text == null) return "";
        for (int i = text.indexOf('%'); i >= 0; i = text.indexOf('%', i + 3)) {
            if (i + 2 >= text.length()
                || !isAsciiHex(text.charAt(i + 1))
                || !isAsciiHex(text.charAt(i + 2))) {
                throw new IllegalArgumentException("invalid % escape at index " + i + " in \"" + text
                    + "\": a % must be followed by two hex digits");
            }
        }
        return java.net.URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private static boolean isAsciiHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
