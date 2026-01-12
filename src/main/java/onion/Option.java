package onion;

/**
 * Option type for safe null handling.
 * Represents an optional value that may or may not be present.
 *
 * @param <T> the type of the contained value
 */
public sealed interface Option<T> permits Option.Some, Option.None {

    /**
     * Represents a present value.
     */
    record Some<T>(T value) implements Option<T> {
        @Override
        public boolean isDefined() { return true; }

        @Override
        public boolean isEmpty() { return false; }

        @Override
        public T get() { return value; }

        @Override
        public T getOrElse(T defaultValue) { return value; }

        @Override
        public <U> Option<U> map(Function1<T, U> f) {
            return new Some<>(f.call(value));
        }

        @Override
        public <U> Option<U> flatMap(Function1<T, Option<U>> f) {
            return f.call(value);
        }

        @Override
        public Option<T> filter(Function1<T, Boolean> predicate) {
            return predicate.call(value) ? this : none();
        }

        @Override
        public void forEach(Function1<T, ?> action) {
            action.call(value);
        }

        @Override
        public T orElseThrow() {
            return value;
        }

        @Override
        public <E extends Throwable> T orElseThrow(Function0<E> exceptionSupplier) {
            return value;
        }
    }

    /**
     * Represents an absent value.
     */
    record None<T>() implements Option<T> {
        @Override
        public boolean isDefined() { return false; }

        @Override
        public boolean isEmpty() { return true; }

        @Override
        public T get() {
            throw new java.util.NoSuchElementException("None.get()");
        }

        @Override
        public T getOrElse(T defaultValue) { return defaultValue; }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Option<U> map(Function1<T, U> f) {
            return (Option<U>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Option<U> flatMap(Function1<T, Option<U>> f) {
            return (Option<U>) this;
        }

        @Override
        public Option<T> filter(Function1<T, Boolean> predicate) {
            return this;
        }

        @Override
        public void forEach(Function1<T, ?> action) {
            // do nothing
        }

        @Override
        public T orElseThrow() {
            throw new java.util.NoSuchElementException("None.orElseThrow()");
        }

        @Override
        public <E extends Throwable> T orElseThrow(Function0<E> exceptionSupplier) throws E {
            throw exceptionSupplier.call();
        }
    }

    // Static factory methods

    /**
     * Creates an Option from a nullable value.
     * Returns Some(value) if value is non-null, None otherwise.
     */
    static <T> Option<T> of(T value) {
        return value != null ? new Some<>(value) : none();
    }

    /**
     * Creates a Some containing the given non-null value.
     */
    static <T> Option<T> some(T value) {
        return new Some<>(value);
    }

    /**
     * Returns the singleton None instance.
     */
    @SuppressWarnings("unchecked")
    static <T> Option<T> none() {
        return (Option<T>) NoneHolder.INSTANCE;
    }

    // Instance methods

    /**
     * Returns true if the option contains a value.
     */
    boolean isDefined();

    /**
     * Returns true if the option is empty.
     */
    boolean isEmpty();

    /**
     * Gets the contained value. Throws if empty.
     */
    T get();

    /**
     * Gets the contained value or returns the default if empty.
     */
    T getOrElse(T defaultValue);

    /**
     * Transforms the contained value using the given function.
     */
    <U> Option<U> map(Function1<T, U> f);

    /**
     * Transforms the contained value using a function that returns an Option.
     */
    <U> Option<U> flatMap(Function1<T, Option<U>> f);

    /**
     * Returns this option if the predicate is satisfied, None otherwise.
     */
    Option<T> filter(Function1<T, Boolean> predicate);

    /**
     * Executes the action if a value is present.
     */
    void forEach(Function1<T, ?> action);

    /**
     * Gets the contained value or throws NoSuchElementException if empty.
     */
    T orElseThrow();

    /**
     * Gets the contained value or throws a custom exception if empty.
     */
    <E extends Throwable> T orElseThrow(Function0<E> exceptionSupplier) throws E;

    // Holder for singleton None instance
    class NoneHolder {
        static final None<?> INSTANCE = new None<>();
    }
}
