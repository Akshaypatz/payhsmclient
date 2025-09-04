package com.billdesk.paymenthsm.client;

import com.billdesk.paymenthsm.client.internal.config.HSMConfig;
import com.billdesk.paymenthsm.client.internal.core.HSMService;
import com.billdesk.paymenthsm.client.internal.enums.ACS_BANK;
import com.billdesk.paymenthsm.client.internal.exception.HSMException;
import com.billdesk.paymenthsm.client.internal.loadbalancer.LoadBalancer;
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
    private final boolean enabled;

    public HSMClient(HSMConfig config,LoadBalancer loadBalancer, HSMService hsmService) {

        if (!config.isEnabled()) {
            log.info("HSM Client is disabled");
            this.enabled = false;
            this.hsmService = null;
            this.loadBalancer = null;
            return;
        }

        this.loadBalancer = loadBalancer;
        this.hsmService = hsmService;
        this.enabled = true;
    }

    public CompletableFuture<String> generateVisaCAVV(ACS_BANK bank, String data) throws HSMException {
        checkIfEnabled();
        return hsmService.generateVisaCAVV(bank, data);
    }

    public CompletableFuture<String> generateMasterCAVV(ACS_BANK bank, String data) throws HSMException {
        checkIfEnabled();
        return hsmService.generateMasterCAVV(bank, data);
    }

    public CompletableFuture<String> generateHMAC(String keyName, String data) throws HSMException {
        checkIfEnabled();
        return hsmService.generateHMAC(keyName, data);
    }

    private void checkIfEnabled() throws HSMException {
        if (!enabled) {
            throw new HSMException("HSM Client is disabled. Check your configuration.");
        }
    }

    @PreDestroy
    public void shutdown() {
        if (enabled) {
            log.info("Shutting down HSMClient gracefully.");
            loadBalancer.shutdown();
        } else {
            log.info("HSM Client was disabled, no shutdown required.");
        }
    }
}
