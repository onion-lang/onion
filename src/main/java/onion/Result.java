package onion;

/**
 * Result type for error handling without exceptions.
 * Represents either a successful value (Ok) or an error (Err).
 *
 * @param <T> the type of the success value
 * @param <E> the type of the error value
 */
public sealed interface Result<T, E> permits Result.Ok, Result.Err {

    /**
     * Represents a successful result.
     */
    record Ok<T, E>(T value) implements Result<T, E> {
        @Override
        public boolean isOk() { return true; }

        @Override
        public boolean isErr() { return false; }

        @Override
        public T get() { return value; }

        @Override
        public E getError() {
            throw new java.util.NoSuchElementException("Ok.getError()");
        }

        @Override
        public T getOrElse(T defaultValue) { return value; }

        @Override
        public <U> Result<U, E> map(Function1<T, U> f) {
            return new Ok<>(f.call(value));
        }

        @Override
        public <F> Result<T, F> mapError(Function1<E, F> f) {
            @SuppressWarnings("unchecked")
            Result<T, F> result = (Result<T, F>) this;
            return result;
        }

        @Override
        public <U> Result<U, E> flatMap(Function1<T, Result<U, E>> f) {
            return f.call(value);
        }

        @Override
        public T getOrThrow() {
            return value;
        }

        @Override
        public <X extends Throwable> T getOrThrow(Function1<E, X> exceptionMapper) {
            return value;
        }

        @Override
        public Option<T> toOption() {
            return Option.some(value);
        }

        @Override
        public Result<T, E> orElse(Result<T, E> alternative) {
            return this;
        }

        @Override
        public void forEach(Function1<T, ?> action) {
            action.call(value);
        }

        @Override
        public void forEachError(Function1<E, ?> action) {
            // do nothing
        }
    }

    /**
     * Represents an error result.
     */
    record Err<T, E>(E error) implements Result<T, E> {
        @Override
        public boolean isOk() { return false; }

        @Override
        public boolean isErr() { return true; }

        @Override
        public T get() {
            throw new java.util.NoSuchElementException("Err.get()");
        }

        @Override
        public E getError() { return error; }

        @Override
        public T getOrElse(T defaultValue) { return defaultValue; }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U, E> map(Function1<T, U> f) {
            return (Result<U, E>) this;
        }

        @Override
        public <F> Result<T, F> mapError(Function1<E, F> f) {
            return new Err<>(f.call(error));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U, E> flatMap(Function1<T, Result<U, E>> f) {
            return (Result<U, E>) this;
        }

        @Override
        public T getOrThrow() {
            if (error instanceof Throwable t) {
                throw new RuntimeException(t);
            }
            throw new RuntimeException(String.valueOf(error));
        }

        @Override
        public <X extends Throwable> T getOrThrow(Function1<E, X> exceptionMapper) throws X {
            throw exceptionMapper.call(error);
        }

        @Override
        public Option<T> toOption() {
            return Option.none();
        }

        @Override
        public Result<T, E> orElse(Result<T, E> alternative) {
            return alternative;
        }

        @Override
        public void forEach(Function1<T, ?> action) {
            // do nothing
        }

        @Override
        public void forEachError(Function1<E, ?> action) {
            action.call(error);
        }
    }

    // Static factory methods

    /**
     * Creates a successful result containing the given value.
     */
    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    /**
     * Creates an error result containing the given error.
     */
    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    /**
     * Creates a Result from a nullable value.
     * Returns Ok(value) if non-null, Err(errorIfNull) otherwise.
     */
    static <T, E> Result<T, E> ofNullable(T value, E errorIfNull) {
        return value != null ? new Ok<>(value) : new Err<>(errorIfNull);
    }

    /**
     * Wraps a potentially throwing operation in a Result.
     * Returns Ok if the operation succeeds, Err with the exception if it throws.
     */
    static <T> Result<T, Throwable> trying(Function0<T> operation) {
        try {
            return new Ok<>(operation.call());
        } catch (Throwable e) {
            return new Err<>(e);
        }
    }

    // Instance methods

    /**
     * Returns true if this is an Ok result.
     */
    boolean isOk();

    /**
     * Returns true if this is an Err result.
     */
    boolean isErr();

    /**
     * Gets the success value. Throws if this is an Err.
     */
    T get();

    /**
     * Gets the error value. Throws if this is an Ok.
     */
    E getError();

    /**
     * Gets the success value or returns the default if this is an Err.
     */
    T getOrElse(T defaultValue);

    /**
     * Transforms the success value using the given function.
     */
    <U> Result<U, E> map(Function1<T, U> f);

    /**
     * Transforms the error value using the given function.
     */
    <F> Result<T, F> mapError(Function1<E, F> f);

    /**
     * Transforms the success value using a function that returns a Result.
     */
    <U> Result<U, E> flatMap(Function1<T, Result<U, E>> f);

    /**
     * Gets the success value or throws the error (wrapped in RuntimeException if not Throwable).
     */
    T getOrThrow();

    /**
     * Gets the success value or throws a custom exception.
     */
    <X extends Throwable> T getOrThrow(Function1<E, X> exceptionMapper) throws X;

    /**
     * Converts this Result to an Option, discarding any error.
     */
    Option<T> toOption();

    /**
     * Returns this result if Ok, otherwise returns the alternative.
     */
    Result<T, E> orElse(Result<T, E> alternative);

    /**
     * Executes the action if this is an Ok result.
     */
    void forEach(Function1<T, ?> action);

    /**
     * Executes the action if this is an Err result.
     */
    void forEachError(Function1<E, ?> action);
}
