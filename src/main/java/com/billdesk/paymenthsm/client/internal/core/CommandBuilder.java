package com.billdesk.paymenthsm.client.internal.core;

import com.billdesk.paymenthsm.client.internal.exception.HSMException;

public interface CommandBuilder {
    String buildVisaCAVVCommand(String keyBlock, String data);
    String buildMasterCAVVCommand(String keyBlock, String data);
    String buildHMACCommand(String keyBlock, String data);
    String parseResponse(String rawResponse);
    String extractContextTag(String rawResponse);
    String embedContextTag(String command, String contextTag);
    String buildHSMPingCommand();
    String getHSMResponseEndTag();

}
