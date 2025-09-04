package com.billdesk.paymenthsm.client.internal.provider.utimaco;

import com.billdesk.paymenthsm.client.internal.config.HSMConfig;
import com.billdesk.paymenthsm.client.internal.core.AbstractHSMService;
import com.billdesk.paymenthsm.client.internal.core.CommandBuilder;
import com.billdesk.paymenthsm.client.internal.enums.Provider;
import com.billdesk.paymenthsm.client.internal.loadbalancer.LoadBalancer;
import org.springframework.stereotype.Service;
import java.util.Map;


@Service
public class UtimacoHSMService extends AbstractHSMService {

    public UtimacoHSMService(HSMConfig config, LoadBalancer loadBalancer, CommandBuilder commandBuilder, Map<String, String> keyBlocks) {
        super(config, loadBalancer, commandBuilder, keyBlocks);
    }

    @Override
    public Provider getProvider() {
        return Provider.UTIMACO;
    }
}
