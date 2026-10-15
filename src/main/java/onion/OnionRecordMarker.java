package onion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applied by the compiler to every Onion {@code record} class, carrying its component names
 * in declaration order. Lets runtime code that only sees a plain {@code Object} (such as
 * {@link Json#stringify}) recognize a record value and read its components by name instead
 * of guessing from unspecified reflection field order. An Onion record is a plain class
 * (private final fields plus public accessor methods), not a {@code java.lang.Record}, so
 * this annotation is how the two meanings of "record" are told apart at runtime.
 *
 * <p>An annotation rather than a marker interface on purpose: adding every record to an
 * extra interface's subtype set made it a second candidate when the compiler computes two
 * records' common ancestor (e.g. an ADT enum's `case` records, each already implementing
 * their own sealed interface), and an ambiguous choice between two candidate interfaces
 * silently falls back to {@code Object} instead of the sealed interface both actually want.
 * An annotation carries the same information without becoming part of the type hierarchy.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface OnionRecordMarker {
    /** Component names, in declaration order; each has a matching public no-arg accessor. */
    String[] value();
}
