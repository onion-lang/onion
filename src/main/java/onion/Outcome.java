package onion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of reading external data: either a value, or every reason it could not be
 * read.
 *
 * <p>This is the one failure channel for the boundary. Onion reports the same conceptual
 * failure — "this data did not match" — five different ways today: {@code null} on a
 * regex no-match, a second indistinguishable {@code null} on a conversion failure, a
 * silently dropped line, a {@code null} that discards the parser's position, and
 * {@code System.exit(1)}.
 *
 * <h2>Why this is not {@code Result<T, List<Defect>>}</h2>
 *
 * <p>Because of {@link #zip}. {@link Result} is monadic: {@code bind} short-circuits, so
 * the first bad field hides the rest. A record with three malformed fields should report
 * three defects in one pass, and that is a different composition law:
 *
 * <pre>
 *   Ok(f)   zip Ok(x)   = Ok(f(x))
 *   Bad(d1) zip Ok(_)   = Bad(d1)
 *   Ok(_)   zip Bad(d2) = Bad(d2)
 *   Bad(d1) zip Bad(d2) = Bad(d1 ++ d2)     &lt;- the reason this type exists
 * </pre>
 *
 * <p>{@link #bind} still short-circuits, because it must: the second computation may
 * depend on the first's value, so there is nothing to run it against. Both operations are
 * available; a shape derivation uses {@code zip}.
 *
 * @param <T> the type of the value read
 */
public sealed interface Outcome<T> permits Outcome.Ok, Outcome.Bad {

    /** A value that was read successfully. */
    record Ok<T>(T value) implements Outcome<T> {
        @Override public boolean isOk() { return true; }
        @Override public T get() { return value; }
        @Override public List<Defect> defects() { return Collections.emptyList(); }
    }

    /** Every reason the value could not be read. Never empty. */
    record Bad<T>(List<Defect> defects) implements Outcome<T> {
        public Bad {
            defects = defects == null ? Collections.emptyList() : List.copyOf(defects);
        }
        @Override public boolean isOk() { return false; }
        @Override
        public T get() {
            throw new java.util.NoSuchElementException("Outcome.Bad.get(): " + describe());
        }
    }

    // ---------------------------------------------------------------- factories

    /** Monadic unit, used by do-notation: {@code do[Outcome] { x <- o; ... }}. */
    static <T> Outcome<T> successful(T value) {
        return new Ok<>(value);
    }

    static <T> Outcome<T> ok(T value) {
        return new Ok<>(value);
    }

    static <T> Outcome<T> bad(Defect defect) {
        return new Bad<>(List.of(defect));
    }

    static <T> Outcome<T> bad(List<Defect> defects) {
        return new Bad<>(defects);
    }

    /** {@code value}, or a defect when it is null — the usual shape of a Java-interop read. */
    static <T> Outcome<T> ofNullable(T value, Defect ifNull) {
        return value == null ? bad(ifNull) : ok(value);
    }

    // ---------------------------------------------------------------- inspection

    boolean isOk();

    default boolean isBad() { return !isOk(); }

    /** The value. Throws if this is a Bad — use {@link #getOrElse} or {@link #fold}. */
    T get();

    /** Every defect, empty when this is an Ok. */
    List<Defect> defects();

    default T getOrElse(T defaultValue) {
        return isOk() ? get() : defaultValue;
    }

    default T orNull() {
        return isOk() ? get() : null;
    }

    // ---------------------------------------------------------------- combinators

    @SuppressWarnings("unchecked")
    default <U> Outcome<U> map(Function1<T, U> f) {
        return isOk() ? ok(f.call(get())) : (Outcome<U>) this;
    }

    @SuppressWarnings("unchecked")
    default <U> Outcome<U> flatMap(Function1<T, Outcome<U>> f) {
        return isOk() ? f.call(get()) : (Outcome<U>) this;
    }

    /** Monadic bind used by do-notation. Short-circuits; see {@link #zip} to accumulate. */
    default <U> Outcome<U> bind(Function1<T, Outcome<U>> f) {
        return flatMap(f);
    }

    /**
     * Combines two outcomes, <strong>accumulating</strong> defects from both.
     *
     * <p>This is the operation the type exists for. Unlike {@link #flatMap}, a failure on
     * the left does not hide a failure on the right, so a record with three bad fields
     * reports three defects rather than one, three times over.
     */
    default <U, R> Outcome<R> zip(Outcome<U> other, Function2<T, U, R> f) {
        if (isOk() && other.isOk()) return ok(f.call(get(), other.get()));
        List<Defect> combined = new ArrayList<>(defects());
        combined.addAll(other.defects());
        return bad(combined);
    }

    default Outcome<T> orElse(Outcome<T> other) {
        return isOk() ? this : other;
    }

    default <R> R fold(Function1<List<Defect>, R> ifBad, Function1<T, R> ifOk) {
        return isOk() ? ifOk.call(get()) : ifBad.call(defects());
    }

    /** Prefixes every defect's path, for reporting a nested read under its field. */
    default Outcome<T> under(String prefix) {
        if (isOk()) return this;
        List<Defect> nested = new ArrayList<>(defects().size());
        for (Defect d : defects()) nested.add(d.under(prefix));
        return bad(nested);
    }

    /** Moves every defect to {@code line} of an enclosing text. */
    default Outcome<T> onLine(int line) {
        if (isOk()) return this;
        List<Defect> moved = new ArrayList<>(defects().size());
        for (Defect d : defects()) moved.add(d.onLine(line));
        return bad(moved);
    }

    /** Discards the defects. Named so that discarding them is a deliberate act. */
    default Option<T> toOption() {
        return isOk() ? Option.some(get()) : Option.none();
    }

    default Result<T, List<Defect>> toResult() {
        return isOk() ? Result.ok(get()) : Result.err(defects());
    }

    /** One line per defect, or the value's own rendering when there is nothing wrong. */
    default String describe() {
        if (isOk()) return String.valueOf(get());
        StringBuilder sb = new StringBuilder();
        for (Defect d : defects()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(d.describe());
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- collections

    /**
     * All values, if every outcome succeeded; otherwise every defect from all of them.
     *
     * <p>All-or-nothing, and accumulating: use it when a partial result is meaningless.
     * When it is not — a log file where the good lines are still worth having — use
     * {@link #values} and {@link #defects(List)} instead, which keep both.
     */
    static <T> Outcome<List<T>> all(List<Outcome<T>> outcomes) {
        List<T> values = new ArrayList<>(outcomes.size());
        List<Defect> problems = new ArrayList<>();
        for (Outcome<T> o : outcomes) {
            if (o.isOk()) values.add(o.get()); else problems.addAll(o.defects());
        }
        return problems.isEmpty() ? ok(values) : bad(problems);
    }

    /** The values that were read, ignoring the ones that were not. */
    static <T> List<T> values(List<Outcome<T>> outcomes) {
        List<T> values = new ArrayList<>(outcomes.size());
        for (Outcome<T> o : outcomes) if (o.isOk()) values.add(o.get());
        return values;
    }

    /**
     * Every defect across the outcomes.
     *
     * <p>Paired with {@link #values}, this is what lets a per-line read report 995 rows
     * <em>and</em> the 5 lines it could not read — rather than dropping them, which is
     * what happens today.
     */
    static <T> List<Defect> defects(List<Outcome<T>> outcomes) {
        List<Defect> problems = new ArrayList<>();
        for (Outcome<T> o : outcomes) problems.addAll(o.defects());
        return problems;
    }
}
