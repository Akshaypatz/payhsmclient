package com.billdesk.paymenthsm.client.internal.provider.utimaco;

import com.billdesk.paymenthsm.client.internal.core.AbstractHSMService;
import com.billdesk.paymenthsm.client.internal.core.CommandBuilder;
import com.billdesk.paymenthsm.client.internal.config.HSMConfig;
import com.billdesk.paymenthsm.client.internal.enums.Provider;
import com.billdesk.paymenthsm.client.internal.loadbalancer.LoadBalancer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class UtimacoHSMService extends AbstractHSMService {

    public UtimacoHSMService(HSMConfig config, LoadBalancer loadBalancer, CommandBuilder commandBuilder, Map<String, String> keyBlocks) {
        super(config, loadBalancer, commandBuilder, keyBlocks);
        // log key here to validate file or bean keys were passed.
        // log.info(keyBlocks.toString());
    }

    @Override
    public Provider getProvider() {
        return Provider.UTIMACO;
    }
}
