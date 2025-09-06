package com.billdesk.paymenthsm.client.internal.core;

import com.billdesk.paymenthsm.client.internal.config.HSMConfig;
import com.billdesk.paymenthsm.client.internal.enums.ACS_BANK;
import com.billdesk.paymenthsm.client.internal.exception.HSMException;
import com.billdesk.paymenthsm.client.internal.exception.HSMKeyNotFoundException;
import com.billdesk.paymenthsm.client.internal.loadbalancer.LoadBalancer;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class AbstractHSMService implements HSMService {
    public static final String VISA = "VISA";
    public static final String CAVV_GENERATION_KEYNAME_SUFFIX = "_CAVV_GEN";
    public static final String MASTERCARD = "MASTERCARD";
    private final HSMConfig config;
    private final LoadBalancer loadBalancer;
    private final CommandBuilder commandBuilder;
    private final Map<String, String> keyBlocks;

    public AbstractHSMService(HSMConfig config, LoadBalancer loadBalancer, CommandBuilder commandBuilder, Map<String, String> keyBlocks) {
        this.config = config;
        this.loadBalancer = loadBalancer;
        this.commandBuilder = commandBuilder;
        this.keyBlocks = keyBlocks;
        if (config.isEnabled()) {
            log.info("Initializing {} HSM", getProvider().name());
        }

    }

    @Override
    public CompletableFuture<String> generateVisaCAVV(ACS_BANK bank, String data) {
        try {
            String keyBlock = getKeyBlock(buildCAVVKeyName(bank, VISA));
            String command = commandBuilder.buildVisaCAVVCommand(keyBlock, data);
            String correlationId = generateCorrelationId();
            return loadBalancer.executeCommand(command, correlationId);
        } catch (HSMKeyNotFoundException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private String buildCAVVKeyName(ACS_BANK bank, String scheme) {
        return bank.name() + "_" + scheme.toUpperCase() + CAVV_GENERATION_KEYNAME_SUFFIX;
    }

    @Override
    public CompletableFuture<String> generateMasterCAVV(ACS_BANK bank, String data) {
        try {
            String keyBlock = getKeyBlock(buildCAVVKeyName(bank, MASTERCARD));
            String command = commandBuilder.buildMasterCAVVCommand(keyBlock, data);
            String correlationId = generateCorrelationId();
            return loadBalancer.executeCommand(command, correlationId);
        } catch (HSMKeyNotFoundException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<String> generateHMAC(String keyName, String data) {
        try {
            String keyBlock = getKeyBlock(keyName);
            String command = commandBuilder.buildHMACCommand(keyBlock, data);
            String correlationId = generateCorrelationId();
            return loadBalancer.executeCommand(command, correlationId);
        } catch (HSMKeyNotFoundException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private String getKeyBlock(String keyName) {
        String keyBlock = keyBlocks.get(keyName);
        if (keyBlock == null) {
            throw new HSMKeyNotFoundException("Key not found: " + keyName);
        }
        return keyBlock;
    }

    private String generateCorrelationId() {
        return "BD_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
