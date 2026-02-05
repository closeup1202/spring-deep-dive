package com.exam.gracefulshutdown;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Wrapper class that supports graceful shutdown of ExecutorService.
 * <p>
 * Waits for running tasks to complete during application shutdown,
 * and forces shutdown if timeout is exceeded.
 */
@Slf4j
public class GracefulExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    @Getter
    private final long terminationTimeoutSeconds;

    private volatile boolean isShutdown = false;

    public GracefulExecutorService(ExecutorService delegate, long terminationTimeoutSeconds) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate ExecutorService must not be null");
        }
        if (terminationTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Termination timeout must be positive, but was: " + terminationTimeoutSeconds);
        }
        this.delegate = delegate;
        this.terminationTimeoutSeconds = terminationTimeoutSeconds;
    }

    public GracefulExecutorService(ExecutorService delegate) {
        this(delegate, 30);
    }

    @Override
    @PreDestroy
    public void shutdown() {
        if (isShutdown) {
            log.debug("ExecutorService already shutdown");
            return;
        }

        isShutdown = true;
        log.info("Initiating graceful shutdown of ExecutorService (timeout: {}s)", terminationTimeoutSeconds);

        // 1. Stop accepting new tasks
        delegate.shutdown();

        try {
            // 2. Wait for running tasks to complete
            if (!delegate.awaitTermination(terminationTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("ExecutorService did not terminate within {}s, forcing shutdown", terminationTimeoutSeconds);

                // 3. Timeout exceeded - force shutdown
                List<Runnable> remainingTasks = delegate.shutdownNow();

                if (!remainingTasks.isEmpty()) {
                    log.warn("Forced shutdown cancelled {} pending tasks", remainingTasks.size());
                }

                // 4. Brief wait after forced shutdown
                if (!delegate.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("ExecutorService did not terminate even after shutdownNow()");
                }
            } else {
                log.info("ExecutorService shutdown gracefully");
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for ExecutorService shutdown", e);

            // Interrupt occurred - immediate forced shutdown
            List<Runnable> remainingTasks = delegate.shutdownNow();
            log.warn("Shutdown interrupted, cancelled {} pending tasks", remainingTasks.size());

            // Restore interrupt status
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        isShutdown = true;
        log.warn("Forcing immediate shutdown of ExecutorService");
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }
}
