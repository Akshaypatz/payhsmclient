package com.billdesk.paymenthsm.client.internal.config;

import com.billdesk.paymenthsm.client.HSMClient;
import com.billdesk.paymenthsm.client.internal.enums.Provider;
import com.billdesk.paymenthsm.client.internal.loadbalancer.LoadBalancingType;
import com.billdesk.paymenthsm.client.internal.model.HSMNode;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;


@Getter
@Setter
@Validated
@Slf4j
@ConfigurationProperties(prefix = "hsm.client")
public class HSMConfig {
    @NotNull(message = "Provider must be specified")
    private Provider provider;
    @NotNull(message = "Load balancing type must be specified")
    private LoadBalancingType loadBalancingType;
    @Valid
    private List<HSMNode> hsmNodes;
    @Min(value = 1, message = "Max connections must be at least 1")
    private int maxConnections = 1;
    @Min(value = 1, message = "Idle connections cannot be negative")
    private int idleConnections = 1;
    private String vip;
    private Map<String, String> keyBlocks;
    private boolean enabled = true;

    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }

        if (provider == null) {
            throw new IllegalArgumentException("Provider must be specified");
        }

        if (loadBalancingType == null) {
            throw new IllegalArgumentException("Load balancing type must be specified");
        }

        // Network-level load balancing validation
        if (loadBalancingType == LoadBalancingType.NETWORK_LEVEL) {
            if (vip == null || vip.trim().isEmpty()) {
                throw new IllegalArgumentException("Vip is mandatory for NETWORK_LEVEL load balancing");
            }
            this.hsmNodes = List.of(parseVipAsNode(vip));
            if (hsmNodes != null && !hsmNodes.isEmpty()) {
                log.warn("HSM Nodes configuration will be ignored for NETWORK_LEVEL load balancing");
            }
        }

        else if (loadBalancingType == LoadBalancingType.CLIENT_SIDE_ROUND_ROBIN || loadBalancingType == LoadBalancingType.CLIENT_SIDE_FAILOVER) {
            if (hsmNodes == null || hsmNodes.isEmpty()) {
                throw new IllegalArgumentException("HSM nodes must be configured for CLIENT_SIDE load balancing");
            }
            for (HSMNode node : hsmNodes) {
                if (node.getIp() == null || node.getIp().trim().isEmpty()) {
                    throw new IllegalArgumentException("Node IP is required for all nodes");
                }
                if (node.getPort() <= 0 || node.getPort() > 65535) {
                    throw new IllegalArgumentException("Invalid port number for node: " + node.getIp());
                }
            }
        }

        if (idleConnections > maxConnections) {
            throw new IllegalArgumentException("Idle connections cannot exceed max connections");
        }

        log.info(String.valueOf(provider));
        hsmNodes.forEach(hsmNode -> log.info(String.valueOf(hsmNode.getPort())));
        keyBlocks.forEach((k,v)-> log.info("{} :  {}",k,v));


    }

    private HSMNode parseVipAsNode(String vip) {
        String[] parts = vip.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("VIP must be in the format host:port (e.g., 10.0.0.1:1500)");
        }
        String host = parts[0].trim();
        int port;
        try {
            port = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port in VIP: " + vip, e);
        }
        HSMNode node = new HSMNode();
        node.setIp(host);
        node.setPort(port);
        return node;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

