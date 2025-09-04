package com.billdesk.paymenthsm.client.internal.core;

import com.billdesk.paymenthsm.client.internal.exception.*;
import com.billdesk.paymenthsm.client.internal.model.HSMNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Slf4j
public class ResponseDispatcher {
    private final ConcurrentMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutExecutor;
    private static final long defaultHSMTimeout = 100L;
    private final HSMNode hsmNode;

    public ResponseDispatcher(HSMNode hsmNode) {
        this.hsmNode = hsmNode;
        this.timeoutExecutor = Executors.newScheduledThreadPool(2);
    }

    public void registerRequest(String correlationId, CompletableFuture<String> future) {
        registerRequest(correlationId, future, defaultHSMTimeout);
    }

    public void registerRequest(String correlationId, CompletableFuture<String> future, long timeoutMs) {
        pendingRequests.put(correlationId, future);

        timeoutExecutor.schedule(() -> {
            CompletableFuture<String> removedFuture = pendingRequests.remove(correlationId);
            if (removedFuture != null && !removedFuture.isDone()) {
                log.error("Waited too long for the hsm to responsd.");
                removedFuture.completeExceptionally(new HSMRequestTimeoutException("HSM request response timeout for id : " + correlationId));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void completeResponse(String correlationId, String response) {
        CompletableFuture<String> future = pendingRequests.remove(correlationId);
        log.info("Marking correlation id : {} as done", correlationId);
        if (future != null && !future.isDone()) {
            future.complete(response);
        }
    }

    public void completeWithError(String correlationId, Exception error) {
        CompletableFuture<String> future = pendingRequests.remove(correlationId);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(error);
        }
    }

    public void completeHSMCommandSendFailureWithError(String command, String correlationId, Exception e) {
        CompletableFuture<String> future = pendingRequests.remove(correlationId);
        HSMException wrappedException;
        if (e instanceof HSMConnectionException || e instanceof HSMIOException) {
            wrappedException = (HSMException) e;
        } else {
            wrappedException = new HSMException("Unexpected error sending command to HSM", e);
        }
        log.error("Failed to send command to HSM : {}", command, wrappedException);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(wrappedException);
        }
    }

    public void shutdown() {
        log.warn("Shutting down a response dispatcher for socket to {}:{}", hsmNode.getIp(), hsmNode.getPort());
        String shutdownMessage = String.format("HSMClient socket connection closing to %s:%s", hsmNode.getIp(), hsmNode.getPort());
        completeAllWithError(new HSMConnectionException(shutdownMessage));
        timeoutExecutor.shutdown();
        try {
            if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            timeoutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void completeAllWithError(HSMConnectionException exception) {
        log.error("Completing all pending requests with error: {}", exception.getMessage());
        pendingRequests.forEach((correlationId, future) -> {
            if (!future.isDone()) {
                future.completeExceptionally(exception);
                log.warn("Completed request {} with global error", correlationId);
            }
        });
        pendingRequests.clear();
    }
}