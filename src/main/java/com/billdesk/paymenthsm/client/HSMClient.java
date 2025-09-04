package com.billdesk.paymenthsm.client;

import com.billdesk.paymenthsm.client.internal.config.HSMConfig;
import com.billdesk.paymenthsm.client.internal.core.CommandBuilder;
import com.billdesk.paymenthsm.client.internal.core.HSMService;
import com.billdesk.paymenthsm.client.internal.core.ResponseDispatcher;
import com.billdesk.paymenthsm.client.internal.enums.ACS_BANK;
import com.billdesk.paymenthsm.client.internal.exception.HSMException;
import com.billdesk.paymenthsm.client.internal.loadbalancer.LoadBalancer;
import com.billdesk.paymenthsm.client.internal.provider.utimaco.UtimacoCommandBuilder;
import com.billdesk.paymenthsm.client.internal.provider.utimaco.UtimacoHSMService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class HSMClient {
    private final HSMService hsmService;
    private final LoadBalancer loadBalancer;

    public HSMClient(HSMConfig config, Map<String,String> keyBlocks) {
        config.validate();
        if (keyBlocks == null || keyBlocks.isEmpty()) {
            throw new IllegalArgumentException("HSM Key blocks must be provided at application startup");
        }
        CommandBuilder commandBuilder = createCommandBuilder(config);
        this.loadBalancer = new LoadBalancer(config, commandBuilder);
        this.hsmService = createHSMService(config, loadBalancer, commandBuilder,keyBlocks);
    }

    private CommandBuilder createCommandBuilder(HSMConfig config) {
        switch (config.getProvider()) {
            case UTIMACO:
                return new UtimacoCommandBuilder();
            default:
                throw new IllegalArgumentException("Unsupported provider: " + config.getProvider());
        }
    }

    private HSMService createHSMService(HSMConfig config, LoadBalancer loadBalancer, CommandBuilder commandBuilder, Map<String, String> keyBlocks) {
        switch (config.getProvider()) {
            case UTIMACO:
                return new UtimacoHSMService(config, loadBalancer, commandBuilder,keyBlocks);
            default:
                throw new IllegalArgumentException("Unsupported provider: " + config.getProvider());
        }
    }

    public CompletableFuture<String> generateVisaCAVV(ACS_BANK bank, String data) throws HSMException {
        return hsmService.generateVisaCAVV(bank, data);
    }

    public CompletableFuture<String> generateMasterCAVV(ACS_BANK bank, String data) throws HSMException {
        return hsmService.generateMasterCAVV(bank, data);
    }

    public CompletableFuture<String> generateHMAC(String keyName, String data) throws HSMException {
        return hsmService.generateHMAC(keyName, data);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down HSMClient gracefully.");
        loadBalancer.shutdown();
    }
}
