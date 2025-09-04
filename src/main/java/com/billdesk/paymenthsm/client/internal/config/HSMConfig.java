package com.billdesk.paymenthsm.client.internal.config;

import com.billdesk.paymenthsm.client.internal.enums.Provider;
import com.billdesk.paymenthsm.client.internal.loadbalancer.LoadBalancingType;
import com.billdesk.paymenthsm.client.internal.model.HSMNode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;


@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hsm.client")
public class HSMConfig {
    private Provider provider;
    private LoadBalancingType loadBalancingType;
    private List<HSMNode> nodes;
    private int maxConnections;
    private int idleConnections;

    //TODO: add validations for properties and add deafult values.

    // Getters and setters
    public void validate() {
        if (provider == null) throw new IllegalArgumentException("Provider must be specified");
        if (nodes == null || nodes.isEmpty()) throw new IllegalArgumentException("HSM nodes must be configured");
//        if (keyBlocks == null || keyBlocks.isEmpty()) throw new IllegalArgumentException("Key blocks must be provided");
    }
}

