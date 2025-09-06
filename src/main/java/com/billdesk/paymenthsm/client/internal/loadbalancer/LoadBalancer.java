package com.billdesk.paymenthsm.client.internal.loadbalancer;

import com.billdesk.paymenthsm.client.internal.config.HSMConfig;
import com.billdesk.paymenthsm.client.internal.connection.HSMConnectionPool;
import com.billdesk.paymenthsm.client.internal.core.CommandBuilder;
import com.billdesk.paymenthsm.client.internal.exception.HSMConnectionException;
import com.billdesk.paymenthsm.client.internal.exception.HSMNoHealthyNodeException;
import com.billdesk.paymenthsm.client.internal.exception.TriedAllHSMNodesException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class LoadBalancer {
    private final List<HSMConnectionPool> nodePools;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final LoadBalancingType loadBalancingType;
    private final ScheduledExecutorService healthChecker = Executors.newSingleThreadScheduledExecutor();

    public LoadBalancer(HSMConfig config,
                        CommandBuilder commandBuilder) {
        if (!config.isEnabled()) {
            nodePools = new ArrayList<>();
            loadBalancingType = LoadBalancingType.CLIENT_SIDE_FAILOVER;
            return;
        }
        this.loadBalancingType = config.getLoadBalancingType();
        this.nodePools = config.getHsmNodes().stream()
                .map(node -> new HSMConnectionPool(node, config, commandBuilder))
                .collect(Collectors.toList());

        // Validate and warm up pools at startup
        nodePools.forEach(pool -> {
            try {
                pool.warmupPoolAndMarkHealthyNodes();
                pool.printPoolStats();
            } catch (Exception e) {
                pool.markUnhealthy();
            }
        });

        boolean anyHealthy = nodePools.stream().anyMatch(HSMConnectionPool::isHealthy);
        if (!anyHealthy) {
            throw new HSMNoHealthyNodeException("No HSM nodes available at startup!");
        }

        // kept it 30 seconds because socket timeout we have kept 45.
        healthChecker.scheduleAtFixedRate(this::runHealthCheckOnAllSockets, 30, 30, TimeUnit.SECONDS);
    }

    public CompletableFuture<String> executeCommand(String command, String correlationId) {
        return tryExecute(command, correlationId, 0, 0L);
    }

    private CompletableFuture<String> tryExecute(String command, String correlationId, int tries, long attemptedMask) {
        if (tries >= nodePools.size()) {
            CompletableFuture<String> failed = new CompletableFuture<>();
            failed.completeExceptionally(new TriedAllHSMNodesException("Tried all HSM nodes but still failing after tries " + tries + " HSMs. Last command tried " + command));
            return failed;
        }

        HSMConnectionPool pool = getNextHealthyPool(attemptedMask);
        if (pool == null) {
            CompletableFuture<String> failed = new CompletableFuture<>();
            failed.completeExceptionally(new HSMNoHealthyNodeException("No healthy HSM nodes available out of " + nodePools.size() + " nodes!"));
            return failed;
        }

        int poolIndex = nodePools.indexOf(pool);
        long newMask = attemptedMask | (1L << poolIndex);
        log.info("Executing command on node {}:{} as attempt {} with correlation {}", pool.getNode().getIp(), pool.getNode().getPort(), tries, correlationId);

        return pool.executeCommand(command, correlationId)
                .exceptionallyCompose(ex -> {
                    log.error("Command to HSM Failed on node {}:{}", pool.getNode().getIp(), pool.getNode().getPort(), ex);
                    if (ex instanceof HSMConnectionException) {
                        log.error("Connection error encountered to HSM {}:{}. Marking it unhealthy! ", pool.getNode().getIp(), pool.getNode().getPort());
                        pool.markUnhealthy();
                    }
                    log.info("Retrying command on pool {}:{} -> {} with correlation: {}", pool.getNode().getIp(), pool.getNode().getPort(), command, correlationId);
                    return tryExecute(command, correlationId, tries + 1, newMask);
                });
    }

    private HSMConnectionPool getNextHealthyPool(long attemptedMask) {
        log.info("Using {} load balancing!", loadBalancingType);
        if (loadBalancingType == LoadBalancingType.NETWORK_LEVEL || loadBalancingType == LoadBalancingType.CLIENT_SIDE_FAILOVER) {
            return nodePools.stream().filter(HSMConnectionPool::isHealthy).filter(pool -> {
                int idx = nodePools.indexOf(pool);
                return ((attemptedMask >> idx) & 1L) == 0;
            }).findFirst().orElse(null);
        }
        int size = nodePools.size();
        for (int i = 0; i < size; i++) {
            int index = currentIndex.getAndIncrement() % size;
            // think of resetting currentindex to 0 else out of bound.
            //TODO: akshay revisit this
            if (currentIndex.get() >= Integer.MAX_VALUE - 100000) {
                currentIndex.set(0);
                index = 0;
            }
            if (((attemptedMask >> index) & 1L) != 0) {
                continue;
            }
            HSMConnectionPool candidate = nodePools.get(index);
            if (candidate.isHealthy()) {
                return candidate;
            }
        }
        return null;
    }


    //TODO: can decide to keep running on strategy chosen also - keep these many alive - 1/3 can also be healthy and 2/4 can also be ( 50 % )
    private void runHealthCheckOnAllSockets() {

        log.info("Initiating health check on all sockets");
        for (HSMConnectionPool hsmConnectionPool : nodePools) {
            try {
                boolean isHealthyNow = hsmConnectionPool.performHealthCheckOnAllSockets();
                if (isHealthyNow && !hsmConnectionPool.isHealthy()) {
                    hsmConnectionPool.markHealthy();
                    log.info("HSM {}:{} recovered and marked healthy", hsmConnectionPool.getNode().getIp(), hsmConnectionPool.getNode().getPort());
                } else if (!isHealthyNow && hsmConnectionPool.isHealthy()) {
                    hsmConnectionPool.markUnhealthy();
                    log.info("HSM {}:{} seems down so marked unhealthy", hsmConnectionPool.getNode().getIp(), hsmConnectionPool.getNode().getPort());
                }
                hsmConnectionPool.printPoolStats();
            } catch (Exception e) {
                log.error("Health check failed for HSM {}:{}", hsmConnectionPool.getNode().getIp(), hsmConnectionPool.getNode().getPort());
                hsmConnectionPool.markUnhealthy();
            }
        }

        long healthyNodes = nodePools.stream().filter(HSMConnectionPool::isHealthy).count();
        log.info("Health Check complete -> {}/{} HSMs healthy", healthyNodes, nodePools.size());
    }

//    private void runHealthCheck() {
//        for (HSMConnectionPool pool : nodePools) {
//            AsyncSocketConnection testSocket = null;
//            try {
//                testSocket = pool.getInternalConnectionPool().borrowObject();
//                CompletableFuture<String> ping = testSocket.pingHsm();
//                String resp = ping.get(1, TimeUnit.SECONDS);
//                pool.printPoolStats();
//                pool.markHealthy();
//                log.info("HSM {}:{} is up", pool.getNode().getIp(), pool.getNode().getPort());
//            } catch (Exception e) {
//                pool.markUnhealthy();
//                log.warn("HSM {}:{} ping failed", pool.getNode().getIp(), pool.getNode().getPort());
//                try {
//                    pool.getInternalConnectionPool().clear();
//                } catch (Exception ignored) {
//                }
//            } finally {
//                if (testSocket != null && testSocket.isConnected()) {
//                    try {
//                        pool.getInternalConnectionPool().returnObject(testSocket);
//                    } catch (Exception ignored) {
//                    }
//                }
//            }
//        }
//    }

    public void shutdown() {
        healthChecker.shutdown();
        nodePools.forEach(HSMConnectionPool::shutdown);
    }
}
