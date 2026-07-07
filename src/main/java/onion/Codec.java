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

    /** Decodes a standard Base64 string back to text (UTF-8). */
    public static String base64Decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
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

    /** Decodes a hex string (even length, case-insensitive) back to text (UTF-8). */
    public static String hexDecode(String hex) {
        if (hex == null || hex.isEmpty()) return "";
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i + 1 < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            bytes[i / 2] = (byte) ((hi << 4) + lo);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** URL/percent-encodes {@code text} (application/x-www-form-urlencoded, UTF-8). */
    public static String urlEncode(String text) {
        if (text == null) return "";
        return java.net.URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    /** Decodes a URL/percent-encoded string (UTF-8). */
    public static String urlDecode(String text) {
        if (text == null) return "";
        return java.net.URLDecoder.decode(text, StandardCharsets.UTF_8);
    }
}
