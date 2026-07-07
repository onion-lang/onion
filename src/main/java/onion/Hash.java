package onion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Cryptographic and checksum hash digests for Onion programs. Each method hashes
 * the UTF-8 bytes of a string and returns the digest as a lowercase hex string.
 * Call as {@code Hash::sha256("text")}.
 *
 * <p>MD5 and SHA-1 are provided for checksums and interop with legacy systems;
 * prefer SHA-256 or SHA-512 for security-sensitive uses.
 */
public final class Hash {
    private Hash() {
    }

    /** MD5 digest as a 32-char hex string (checksums/interop; not collision-safe). */
    public static String md5(String text) {
        return hexDigest("MD5", text);
    }

    /** SHA-1 digest as a 40-char hex string (interop; not collision-safe). */
    public static String sha1(String text) {
        return hexDigest("SHA-1", text);
    }

    /** SHA-256 digest as a 64-char hex string. */
    public static String sha256(String text) {
        return hexDigest("SHA-256", text);
    }

    /** SHA-512 digest as a 128-char hex string. */
    public static String sha512(String text) {
        return hexDigest("SHA-512", text);
    }

    private static String hexDigest(String algorithm, String text) {
        String input = text == null ? "" : text;
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Every JRE ships MD5/SHA-1/SHA-256/SHA-512, so this cannot happen for
            // the algorithms above; surface it rather than swallowing it.
            throw new RuntimeException("Unsupported hash algorithm: " + algorithm, e);
        }
    }
}
