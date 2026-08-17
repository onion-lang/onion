package onion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Threads, and the pieces needed to use them safely.
 *
 * {@link Future} could already run one thing off the current thread, but there was no way
 * to bound how many run at once, to share a counter between them, to hold a lock, or to
 * hand work from one to another.
 *
 * Usage:
 *   val pool = Concurrent::pool(4)
 *   val results = pool.mapAll(urls) { url => Http::get(url) }
 *   pool.close()
 *
 *   val hits = Concurrent::counter()
 *   hits.increment()
 *
 *   val chan = Concurrent::channel(16)
 *   chan.send("work")
 *   val item = chan.receive()
 *
 * Virtual threads are deliberately absent: they need Java 21 and Onion targets 17.
 */
public final class Concurrent {
    private Concurrent() {} // Prevent instantiation

    /** How many threads the machine can actually run at once. */
    public static int cpus() {
        return Runtime.getRuntime().availableProcessors();
    }

    /** A pool of the given size. Close it, or its threads outlive the work. */
    public static Pool pool(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException(
                "Concurrent: a pool needs at least one thread, got " + threads);
        }
        return new Pool(threads);
    }

    /** A pool sized to the machine. */
    public static Pool pool() {
        return pool(cpus());
    }

    public static Counter counter() {
        return new Counter(0L);
    }

    public static Counter counter(long initial) {
        return new Counter(initial);
    }

    public static Lock lock() {
        return new Lock();
    }

    /** A bounded queue. Bounded on purpose: an unbounded one hides a producer outrunning its consumer until memory runs out. */
    public static Channel channel(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException(
                "Concurrent: a channel needs capacity of at least one, got " + capacity);
        }
        return new Channel(capacity);
    }

    // ========== Pool ==========

    /** A fixed set of worker threads. */
    public static final class Pool {
        private final ExecutorService executor;
        private final int size;

        Pool(int size) {
            this.size = size;
            // Daemon threads: a pool someone forgot to close must not keep the JVM alive
            // after `main` returns. `close()` is still the right thing to call.
            this.executor = Executors.newFixedThreadPool(size, runnable -> {
                Thread thread = new Thread(runnable, "onion-pool");
                thread.setDaemon(true);
                return thread;
            });
        }

        public int size() { return size; }

        /** Runs the operation on a worker and hands back a {@link Future} to await. */
        public <T> Future<T> submit(Function0<T> operation) {
            if (operation == null) {
                throw new IllegalArgumentException("Concurrent: operation must not be null");
            }
            java.util.concurrent.CompletableFuture<T> completable =
                java.util.concurrent.CompletableFuture.supplyAsync(operation::call, executor);
            return Future.fromCompletableFuture(completable);
        }

        /**
         * Applies the function to every element with at most {@link #size} running at once,
         * and returns the results <em>in the input's order</em> — not in the order they
         * finished, which would make the output depend on timing.
         *
         * Any failure is rethrown once every task has settled, so a single bad element
         * cannot leave workers running behind a caller that has already given up.
         */
        public List mapAll(List items, Function1 function) {
            if (items == null) throw new IllegalArgumentException("Concurrent: items must not be null");
            if (function == null) throw new IllegalArgumentException("Concurrent: function must not be null");

            List<java.util.concurrent.CompletableFuture<Object>> pending = new ArrayList<>();
            for (Object item : items) {
                pending.add(java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> function.call(item), executor));
            }
            List<Object> results = new ArrayList<>(pending.size());
            RuntimeException failure = null;
            for (java.util.concurrent.CompletableFuture<Object> task : pending) {
                try {
                    results.add(task.join());
                } catch (RuntimeException e) {
                    results.add(null);
                    // Keep the first failure but keep draining, so nothing is left running.
                    if (failure == null) failure = e;
                }
            }
            if (failure != null) {
                throw new RuntimeException(
                    "Concurrent: a parallel task failed (" + rootMessage(failure) + ")", failure);
            }
            return results;
        }

        /** Stops accepting work and interrupts what is running. Idempotent. */
        public void close() {
            executor.shutdownNow();
        }

        /**
         * Stops accepting work and waits for what is running.
         *
         * @return true when everything finished within the timeout.
         */
        public boolean awaitClose(long timeoutMillis) {
            executor.shutdown();
            try {
                return executor.awaitTermination(Math.max(0, timeoutMillis), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private static String rootMessage(Throwable error) {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            String message = cause.getMessage();
            return message == null ? cause.getClass().getSimpleName() : message;
        }
    }

    // ========== Counter ==========

    /** A number several threads can update without losing increments. */
    public static final class Counter {
        private final AtomicLong value;

        Counter(long initial) {
            this.value = new AtomicLong(initial);
        }

        public long get() { return value.get(); }
        public long increment() { return value.incrementAndGet(); }
        public long decrement() { return value.decrementAndGet(); }
        public long add(long delta) { return value.addAndGet(delta); }
        public void set(long next) { value.set(next); }

        /** Sets the value only if it still equals {@code expected}. */
        public boolean compareAndSet(long expected, long next) {
            return value.compareAndSet(expected, next);
        }

        @Override
        public String toString() { return "Concurrent.Counter(" + value.get() + ")"; }
    }

    // ========== Lock ==========

    /** A mutual-exclusion lock. */
    public static final class Lock {
        private final ReentrantLock lock = new ReentrantLock();

        /**
         * Runs the body while holding the lock, releasing it even if the body throws.
         *
         * Prefer this to {@link #acquire}/{@link #release}: a body that throws between a
         * manual pair leaks the lock and every other thread waits forever.
         */
        public <T> T withLock(Function0<T> body) {
            if (body == null) throw new IllegalArgumentException("Concurrent: body must not be null");
            lock.lock();
            try {
                return body.call();
            } finally {
                lock.unlock();
            }
        }

        public void acquire() { lock.lock(); }

        public void release() { lock.unlock(); }

        /** Takes the lock only if it is free. Returns whether it was taken. */
        public boolean tryAcquire() { return lock.tryLock(); }

        public boolean isHeld() { return lock.isLocked(); }
    }

    // ========== Channel ==========

    /** A bounded hand-off queue between threads. */
    public static final class Channel {
        private final BlockingQueue<Object> queue;
        private volatile boolean closed;

        Channel(int capacity) {
            this.queue = new ArrayBlockingQueue<>(capacity);
        }

        /** Blocks while the channel is full. */
        public void send(Object item) {
            if (item == null) {
                // A null would be indistinguishable from "nothing arrived" on the way out.
                throw new IllegalArgumentException("Concurrent: cannot send null on a channel");
            }
            requireOpen();
            try {
                queue.put(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Concurrent: interrupted while sending", e);
            }
        }

        /** Returns false rather than blocking when the channel is full. */
        public boolean trySend(Object item) {
            if (item == null) {
                throw new IllegalArgumentException("Concurrent: cannot send null on a channel");
            }
            requireOpen();
            return queue.offer(item);
        }

        /** Blocks until something arrives. */
        public Object receive() {
            try {
                return queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Concurrent: interrupted while receiving", e);
            }
        }

        /** Waits up to the timeout, then returns null. */
        public Object receiveTimeout(long timeoutMillis) {
            try {
                return queue.poll(Math.max(0, timeoutMillis), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Concurrent: interrupted while receiving", e);
            }
        }

        public int size() { return queue.size(); }

        public boolean isEmpty() { return queue.isEmpty(); }

        /** Refuses further sends. What is already queued can still be received. */
        public void close() { closed = true; }

        public boolean isClosed() { return closed; }

        /** Everything queued right now, leaving the channel empty. */
        public List drain() {
            List<Object> out = new ArrayList<>();
            queue.drainTo(out);
            return out;
        }

        private void requireOpen() {
            if (closed) throw new IllegalStateException("Concurrent: the channel is closed");
        }
    }
}
