package onion;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Future type for asynchronous programming.
 * Represents a value that will be available at some point in the future.
 *
 * Wraps Java's CompletableFuture to provide a more functional API
 * that integrates with Onion's Function interfaces.
 *
 * @param <T> the type of the eventual value
 */
public final class Future<T> {
    private final CompletableFuture<T> delegate;

    private Future(CompletableFuture<T> delegate) {
        this.delegate = delegate;
    }

    // ========== Static Factory Methods ==========

    /**
     * Creates a Future that is already completed with the given value.
     */
    public static <T> Future<T> successful(T value) {
        return new Future<>(CompletableFuture.completedFuture(value));
    }

    /**
     * Creates a Future that is already failed with the given exception.
     */
    public static <T> Future<T> failed(Throwable error) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        cf.completeExceptionally(error);
        return new Future<>(cf);
    }

    /**
     * Creates a Future that runs the given operation asynchronously.
     * The operation is executed on a background thread.
     */
    public static <T> Future<T> async(Function0<T> operation) {
        return new Future<>(CompletableFuture.supplyAsync(() -> operation.call()));
    }

    /**
     * Creates a Future that runs the given operation asynchronously
     * and may throw an exception.
     */
    public static <T> Future<T> asyncThrowing(ThrowingSupplier<T> operation) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                cf.complete(operation.get());
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        return new Future<>(cf);
    }

    /**
     * Creates a Future from a Java CompletableFuture.
     */
    public static <T> Future<T> fromCompletableFuture(CompletableFuture<T> cf) {
        return new Future<>(cf);
    }

    // ========== Transformation Methods (Monadic) ==========

    /**
     * Transforms the value when it becomes available.
     * If this Future fails, the returned Future also fails with the same exception.
     */
    public <U> Future<U> map(Function1<T, U> f) {
        return new Future<>(delegate.thenApply(value -> f.call(value)));
    }

    /**
     * Transforms the value with a function that returns another Future.
     * Allows chaining async operations without nesting.
     */
    public <U> Future<U> flatMap(Function1<T, Future<U>> f) {
        return new Future<>(delegate.thenCompose(value -> f.call(value).delegate));
    }

    /**
     * Alias for flatMap, used by do notation desugaring.
     * Implements the monadic bind operation (>>= in Haskell).
     */
    public <U> Future<U> bind(Function1<T, Future<U>> f) {
        return flatMap(f);
    }

    /**
     * Filters the value using a predicate.
     * If the predicate returns false, the Future fails with a NoSuchElementException.
     */
    public Future<T> filter(Function1<T, Boolean> predicate) {
        return new Future<>(delegate.thenApply(value -> {
            if (predicate.call(value)) {
                return value;
            }
            throw new java.util.NoSuchElementException("Future.filter predicate is not satisfied");
        }));
    }

    // ========== Error Handling ==========

    /**
     * Recovers from a failure by providing an alternative value.
     * If this Future succeeds, the recovery function is not called.
     */
    public Future<T> recover(Function1<Throwable, T> handler) {
        return new Future<>(delegate.exceptionally(error -> {
            // Unwrap CompletionException to get the actual cause
            Throwable cause = error instanceof java.util.concurrent.CompletionException
                ? error.getCause()
                : error;
            return handler.call(cause);
        }));
    }

    /**
     * Recovers from a failure by providing an alternative Future.
     * If this Future succeeds, the recovery function is not called.
     */
    public Future<T> recoverWith(Function1<Throwable, Future<T>> handler) {
        return new Future<>(delegate.exceptionallyCompose(error -> {
            Throwable cause = error instanceof java.util.concurrent.CompletionException
                ? error.getCause()
                : error;
            return handler.call(cause).delegate;
        }));
    }

    /**
     * Transforms the error if this Future fails.
     * If this Future succeeds, the returned Future also succeeds.
     */
    public Future<T> mapError(Function1<Throwable, Throwable> f) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        delegate.whenComplete((value, error) -> {
            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException
                    ? error.getCause()
                    : error;
                cf.completeExceptionally(f.call(cause));
            } else {
                cf.complete(value);
            }
        });
        return new Future<>(cf);
    }

    // ========== Callbacks ==========

    /**
     * Registers a callback to be called when this Future completes successfully.
     * The callback is executed asynchronously.
     */
    public Future<T> onSuccess(Function1<T, ?> callback) {
        delegate.thenAccept(value -> callback.call(value));
        return this;
    }

    /**
     * Registers a callback to be called when this Future fails.
     * The callback is executed asynchronously.
     */
    public Future<T> onFailure(Function1<Throwable, ?> callback) {
        delegate.exceptionally(error -> {
            Throwable cause = error instanceof java.util.concurrent.CompletionException
                ? error.getCause()
                : error;
            callback.call(cause);
            return null;
        });
        return this;
    }

    /**
     * Registers callbacks for both success and failure cases.
     */
    public Future<T> onComplete(Function1<T, ?> onSuccess, Function1<Throwable, ?> onFailure) {
        delegate.whenComplete((value, error) -> {
            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException
                    ? error.getCause()
                    : error;
                onFailure.call(cause);
            } else {
                onSuccess.call(value);
            }
        });
        return this;
    }

    // ========== Blocking Operations ==========

    /**
     * Blocks and waits for the result.
     * Throws RuntimeException if the Future failed.
     */
    public T await() {
        try {
            return delegate.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Future.await() was interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Future.await() failed", e.getCause());
        }
    }

    /**
     * Blocks and waits for the result with a timeout.
     * Throws RuntimeException if the Future failed or timed out.
     *
     * @param timeoutMs the maximum time to wait in milliseconds
     */
    public T awaitTimeout(long timeoutMs) {
        try {
            return delegate.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Future.awaitTimeout() was interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Future.awaitTimeout() failed", e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException("Future.awaitTimeout() timed out after " + timeoutMs + "ms", e);
        }
    }

    /**
     * Gets the value or a default if the Future failed.
     * Blocks until the Future completes.
     */
    public T getOrElse(T defaultValue) {
        try {
            return delegate.get();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ========== Status Queries ==========

    /**
     * Returns true if this Future has completed (either successfully or with failure).
     */
    public boolean isCompleted() {
        return delegate.isDone();
    }

    /**
     * Returns true if this Future completed successfully.
     */
    public boolean isSuccess() {
        return delegate.isDone() && !delegate.isCompletedExceptionally();
    }

    /**
     * Returns true if this Future failed.
     */
    public boolean isFailure() {
        return delegate.isCompletedExceptionally();
    }

    // ========== Conversion Methods ==========

    /**
     * Converts this Future to an Option when completed.
     * Returns Some(value) if successful, None if failed.
     * Blocks until the Future completes.
     */
    public Option<T> toOption() {
        try {
            return Option.some(delegate.get());
        } catch (Exception e) {
            return Option.none();
        }
    }

    /**
     * Converts this Future to a Result when completed.
     * Returns Ok(value) if successful, Err(exception) if failed.
     * Blocks until the Future completes.
     */
    public Result<T, Throwable> toResult() {
        try {
            return Result.ok(delegate.get());
        } catch (ExecutionException e) {
            return Result.err(e.getCause());
        } catch (Exception e) {
            return Result.err(e);
        }
    }

    /**
     * Returns the underlying CompletableFuture for Java interop.
     */
    public CompletableFuture<T> underlying() {
        return delegate;
    }

    // ========== Combining Futures ==========

    /**
     * Combines this Future with another, producing a tuple-like result.
     * Both Futures run concurrently.
     */
    public <U> Future<Object[]> zip(Future<U> other) {
        return new Future<>(delegate.thenCombine(other.delegate, (a, b) -> new Object[]{a, b}));
    }

    /**
     * Races this Future against another, returning whichever completes first.
     */
    @SuppressWarnings("unchecked")
    public Future<T> race(Future<T> other) {
        return new Future<>((CompletableFuture<T>) CompletableFuture.anyOf(delegate, other.delegate));
    }

    // ========== Static Combinators ==========

    /**
     * Creates a Future that completes when all given Futures complete.
     * Returns an array of results in order.
     */
    @SafeVarargs
    public static <T> Future<Object[]> all(Future<T>... futures) {
        CompletableFuture<?>[] cfs = new CompletableFuture<?>[futures.length];
        for (int i = 0; i < futures.length; i++) {
            cfs[i] = futures[i].delegate;
        }
        return new Future<>(CompletableFuture.allOf(cfs).thenApply(v -> {
            Object[] results = new Object[futures.length];
            for (int i = 0; i < futures.length; i++) {
                results[i] = futures[i].delegate.join();
            }
            return results;
        }));
    }

    /**
     * Creates a Future that completes when the first of the given Futures completes.
     */
    @SafeVarargs
    public static <T> Future<T> first(Future<T>... futures) {
        CompletableFuture<?>[] cfs = new CompletableFuture<?>[futures.length];
        for (int i = 0; i < futures.length; i++) {
            cfs[i] = futures[i].delegate;
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<T> any = (CompletableFuture<T>) CompletableFuture.anyOf(cfs);
        return new Future<>(any);
    }

    /**
     * Creates a Future that completes after a delay.
     *
     * @param delayMs delay in milliseconds
     */
    public static Future<Void> delay(long delayMs) {
        return new Future<>(CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
    }

    @Override
    public String toString() {
        if (!delegate.isDone()) {
            return "Future(<pending>)";
        } else if (delegate.isCompletedExceptionally()) {
            return "Future(<failed>)";
        } else {
            try {
                return "Future(" + delegate.get() + ")";
            } catch (Exception e) {
                return "Future(<error>)";
            }
        }
    }

    /**
     * Functional interface for operations that may throw.
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }
}
