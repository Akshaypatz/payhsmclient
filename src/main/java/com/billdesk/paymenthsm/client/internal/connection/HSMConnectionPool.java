package com.billdesk.paymenthsm.client.internal.connection;

import com.billdesk.paymenthsm.client.internal.config.HSMConfig;
import com.billdesk.paymenthsm.client.internal.core.CommandBuilder;
import com.billdesk.paymenthsm.client.internal.exception.*;
import com.billdesk.paymenthsm.client.internal.model.HSMNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class HSMConnectionPool {

    @Getter
    private final HSMNode node;
    private final HSMConfig config;
    @Getter
    private final GenericObjectPool<AsyncSocketConnection> internalConnectionPool;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    public void printPoolStats() {
        int active = internalConnectionPool.getNumActive();
        int idle = internalConnectionPool.getNumIdle();
        int total = active + idle;
        int maxTotal = internalConnectionPool.getMaxTotal();

        log.info("HSM Pool Status {}:{} -> Active:{}, Idle:{}, Total:{}/{}",
                node.getIp(), node.getPort(), active, idle, total, maxTotal);
    }

    public HSMConnectionPool(HSMNode node, HSMConfig config,
                             CommandBuilder commandBuilder) {
        this.node = node;
        this.config = config;
        this.internalConnectionPool = new GenericObjectPool<>(
                new AsyncSocketFactory(node, config, commandBuilder)
        );
        internalConnectionPool.setMaxTotal(config.getMaxConnections());
        internalConnectionPool.setMinIdle(config.getIdleConnections());
        internalConnectionPool.setTestOnBorrow(true);
        internalConnectionPool.setTestOnReturn(false);
        internalConnectionPool.setTestWhileIdle(false);
        try {
            // FYI prepares the idle connections also at startup else lazy loading is done
            internalConnectionPool.preparePool();
        } catch (Exception e) {
            log.error("Error occurred while preparing pool for {}:{}", node.getIp(), node.getPort());
        }
    }


    public void warmupPoolAndMarkHealthyNodesWithPing() {
        int connections = Math.max(1, config.getIdleConnections());

        int successfulPings = 0;
        int testedConnections = 0;
        List<AsyncSocketConnection> borrowedConnections = new ArrayList<>();

        try {
            for (int i = 0; i < connections; i++) {
                AsyncSocketConnection socket = null;
                try {
                    socket = internalConnectionPool.borrowObject();
                    borrowedConnections.add(socket);
                    testedConnections++;

                    if (!socket.isConnected()) {
                        log.warn("Warmup: socket {} to HSM {}:{} is not connected",
                                i + 1, node.getIp(), node.getPort());
                        invalidateConnectionSafely(socket);
                        borrowedConnections.remove(socket);
                        continue;
                    }

                    CompletableFuture<String> pingFuture = socket.pingHsm();
                    String response = pingFuture.get(1, TimeUnit.SECONDS);

                    if (response != null && !response.trim().isEmpty()) {
                        successfulPings++;
                        log.info("Warmup validated connection {} for {}:{}",
                                i + 1, node.getIp(), node.getPort());
                    } else {
                        log.warn("Warmup failed - empty response from connection {} for HSM {}:{}",
                                i + 1, node.getIp(), node.getPort());
                        invalidateConnectionSafely(socket);
                        borrowedConnections.remove(socket);
                    }

                } catch (TimeoutException e) {
                    log.warn("Warmup timeout for connection {} to HSM {}:{}",
                            i + 1, node.getIp(), node.getPort());
                    invalidateConnectionSafely(socket);
                    borrowedConnections.remove(socket);
                } catch (Exception e) {
                    log.error("Warmup failed for connection {} to HSM {}:{}",
                            i + 1, node.getIp(), node.getPort(), e);
                    invalidateConnectionSafely(socket);
                    borrowedConnections.remove(socket);
                }
            }

            if (successfulPings > 0) {
                markHealthy();
                log.info("HSM {}:{} warmup passed - {}/{} connections healthy",
                        node.getIp(), node.getPort(), successfulPings, testedConnections);
            } else {
                markUnhealthy();
                log.warn("HSM {}:{} warmup failed - 0/{}/{} connections healthy",
                        node.getIp(), node.getPort(), testedConnections, connections);
            }

        } finally {
            for (AsyncSocketConnection asyncSocketConnection : borrowedConnections) {
                returnConnectionSafely(asyncSocketConnection);
            }
        }
    }


    public void warmupPoolAndMarkHealthyNodes() throws Exception {
        int connections = Math.max(1, config.getIdleConnections());

        for (int i = 0; i < connections; i++) {
            AsyncSocketConnection socket = null;
            try {
                socket = internalConnectionPool.borrowObject();
                if (!socket.isConnected()) {
                    internalConnectionPool.invalidateObject(socket);
                    throw new HSMConnectionException("Socket failed to connect to HSM" + node.getIp());
                }
                else {
                    log.info("Validated connection {} for {}:{}", i + 1, node.getIp(), node.getPort());
                }
            } finally {
                if (socket != null && socket.isConnected()) {
                    internalConnectionPool.returnObject(socket);
                }
            }
        }
        markHealthy();
    }

    public CompletableFuture<String> executeCommand(String command, String correlationId) {
        if (!isHealthy()) {
            CompletableFuture<String> failed = new CompletableFuture<>();
            String errorMsg = String.format("HSM node si %s:%d is unhealthy", node.getIp(), node.getPort());
            failed.completeExceptionally(new HSMUnhealthyNodeException(errorMsg));
            return failed;
        }
        AsyncSocketConnection socket = null;
        try {
            log.info("Attempting to borrow socket from pool for correlation id {}  {}:{}", correlationId, node.getIp(), node.getPort());
            socket = internalConnectionPool.borrowObject(Duration.ofMillis(1000));
            log.info("Successfully borrowed socket from pool for correlation id {} {}:{}", correlationId, node.getIp(), node.getPort());
            final AsyncSocketConnection finalSocket = socket;
            return socket.sendCommandToHSM(command, correlationId)
                    .whenComplete((hsmResult, ex) -> {
                        try {
                            if (ex != null) {

                                Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                                boolean connectionIssue = cause instanceof HSMConnectionException
                                        || cause instanceof HSMSocketShutdownException
                                        || cause instanceof HSMIOException;

                                if (connectionIssue) {
                                    log.warn("Command failed due to connection issue, invalidating socker for {}:{}", node.getIp(), node.getPort());
                                    invalidateConnectionSafely(finalSocket);
                                } else {
                                    returnConnectionSafely(finalSocket);
                                }
                            } else {
                                log.info("Returning connection ALL GOOD HERE");
                                returnConnectionSafely(finalSocket);
                            }
                        } catch (Exception poolError) {
                            log.error("Unwarranted exception occurred to socket to {}:{}", node.getIp(), node.getPort(), poolError);
                            // Last resort can be to try to invalidate
                            invalidateConnectionSafely(finalSocket);
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to borrow connection from pool for {}:{}", node.getIp(), node.getPort(), e);
            //safety mesaure
            invalidateConnectionSafely(socket);
            if (e instanceof IllegalStateException || (internalConnectionPool.getNumActive() == 0)) {
                markUnhealthy();
            }
            CompletableFuture<String> failed = new CompletableFuture<>();
            String errorMsg = String.format("Failed to execute command on HSM %s:%s", node.getIp(), node.getPort());
            failed.completeExceptionally(new HSMConnectionException(errorMsg, e));
            return failed;
        }
    }

    public boolean isHealthy() {
        return healthy.get();
    }

    public void markUnhealthy() {
        healthy.set(false);
    }

    public void markHealthy() {
        healthy.set(true);
    }

    public void shutdown() {
        internalConnectionPool.close();
    }

    public boolean performHealthCheckOnAllSockets() {

        int totalConnections = internalConnectionPool.getNumActive() + internalConnectionPool.getNumIdle();

        if (isHealthy() && totalConnections == 0) {
            log.info(String.valueOf(isHealthy()));
            log.warn("No connections in pool to HSM {}:{}", node.getIp(), node.getPort());
            return false;
        }

        int successfulPings = 0;
        int testedConnections = 0;
        List<AsyncSocketConnection> borrowedConnections = new ArrayList<>();
        try {
            for (int i = 0; i < internalConnectionPool.getMinIdle(); i++) {
                AsyncSocketConnection socket = null;
                try {
                    socket = internalConnectionPool.borrowObject();
                    borrowedConnections.add(socket);
                    testedConnections++;

                    CompletableFuture<String> pingFuture = socket.pingHsm();
                    // TODO: can get exception also here in below ping since timeout on hsm end is 100ms?
                    String response = pingFuture.get(1, TimeUnit.SECONDS);

                    if (response != null && !response.trim().isEmpty()) {
                        successfulPings++;
                        log.debug("Health check passed for connection {} in pool {}:{}",
                                i + 1, node.getIp(), node.getPort());
                    } else {
                        log.warn("Health check failed - empty response from connection {} in pool {}:{}",
                                i + 1, node.getIp(), node.getPort());
                        invalidateConnectionSafely(socket);
                        borrowedConnections.remove(socket);
                    }

                } catch (TimeoutException e) {
                    log.warn("Health check timeout for connection {} in pool {}:{}",
                            i + 1, node.getIp(), node.getPort());
                    invalidateConnectionSafely(socket);
                    borrowedConnections.remove(socket);
                } catch (Exception e) {
                    log.debug("Health check Failed for connection {} in pool to HSM {}:{}", i + 1, node.getIp(), node.getPort());
                    invalidateConnectionSafely(socket);
                    borrowedConnections.remove(socket);
                }
            }

//            double successRate = (double) successfulPings / testedConnections;
//            if (successRate >= 0.3) {
//                markHealthy();
//                log.info("HSM {}:{} health check passed - {}/{} connections healthy",
//                        node.getIp(), node.getPort(), successfulPings, testedConnections);
//                return true;
//            } else {
//                markUnhealthy();
//                log.warn("HSM {}:{} health check failed - only {}/{} connections healthy",
//                        node.getIp(), node.getPort(), successfulPings, testedConnections);
//                return false;
//            }

            return successfulPings > 0;
        } catch (Exception poolError) {
            log.error("Unexpected error during health check for pool {}:{}",
                    node.getIp(), node.getPort(), poolError);
            markUnhealthy();
            return false;
        } finally {
            for (AsyncSocketConnection asyncSocketConnection : borrowedConnections) {
                returnConnectionSafely(asyncSocketConnection);
            }
        }
    }

    private void invalidateConnectionSafely(AsyncSocketConnection socket) {
        if (socket == null) return;
        try {
            internalConnectionPool.invalidateObject(socket);
            log.debug("Invalidated connection for {}:{}", node.getIp(), node.getPort());
        } catch (Exception e) {
            log.warn("Failed to invalidate connection for {}:{}: {}",
                    node.getIp(), node.getPort(), e.getMessage());
            try {
                socket.close();
            } catch (Exception closeError) {
                log.debug("Failed to close socket after invalidation error", closeError);
            }
        }
    }

    private void returnConnectionSafely(AsyncSocketConnection socket) {
        if (socket == null) return;
        try {
            if (socket.isConnected()) {
                internalConnectionPool.returnObject(socket);
            } else {
                invalidateConnectionSafely(socket);
            }
        } catch (Exception e) {
            log.warn("Failed to return connection to pool for {}:{}: {}",
                    node.getIp(), node.getPort(), e.getMessage());
            invalidateConnectionSafely(socket);
        }
    }

}
