package com.billdesk.paymenthsm.client.internal.core;

import com.billdesk.paymenthsm.client.HSMClient;
import com.billdesk.paymenthsm.client.internal.config.HSMConfig;
import com.billdesk.paymenthsm.client.internal.loadbalancer.LoadBalancer;
import com.billdesk.paymenthsm.client.internal.provider.utimaco.UtimacoCommandBuilder;
import com.billdesk.paymenthsm.client.internal.provider.utimaco.UtimacoHSMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@AutoConfiguration
@ConditionalOnClass(HSMClient.class)
@EnableConfigurationProperties(HSMConfig.class)
@Slf4j
//@ConditionalOnProperty(prefix = "hsm.client", name = "enabled", havingValue = "true", matchIfMissing = false)
public class HSMAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CommandBuilder commandBuilder(HSMConfig config) {
        switch (config.getProvider()) {
            case UTIMACO:
                return new UtimacoCommandBuilder();
            default:
                throw new IllegalArgumentException("Unsupported provider: " + config.getProvider());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadBalancer loadBalancer(HSMConfig config, CommandBuilder commandBuilder) {
        return new LoadBalancer(config, commandBuilder);
    }

    @Bean
    @ConditionalOnMissingBean
    public HSMService hsmService(HSMConfig config, LoadBalancer loadBalancer,
                                 CommandBuilder commandBuilder, Map<String, String> hsmKeyBlocks) {

        if (!config.isEnabled()) {
            // default for not enabled initialization
            return new UtimacoHSMService(config, loadBalancer, commandBuilder, null);
        }

        Map<String, String> finalKeyBlocks = resolveKeyBlocks(hsmKeyBlocks, config.getKeyBlocks());
        switch (config.getProvider()) {
            case UTIMACO:
                return new UtimacoHSMService(config, loadBalancer, commandBuilder, finalKeyBlocks);
            default:
                throw new IllegalArgumentException("Unsupported provider: " + config.getProvider());
        }
    }

    public Map<String, String> resolveKeyBlocks(Map<String, String> beanKeyBlocks,
                                                Map<String, String> configKeyBlocks) {
        if (beanKeyBlocks != null && !beanKeyBlocks.isEmpty()) {
            log.info("Using key blocks provided via bean");
            return beanKeyBlocks;
        }

        if (configKeyBlocks != null && !configKeyBlocks.isEmpty()) {
            log.info("Using key blocks provided via configuration");
            return configKeyBlocks;
        }


        throw new IllegalArgumentException("HSM Key blocks must be provided");
    }


    @Bean
    @ConditionalOnMissingBean
    public HSMClient hsmClient(HSMConfig config, LoadBalancer loadBalancer, HSMService hsmService) {
        return new HSMClient(config, loadBalancer, hsmService);
    }
}