package com.billdesk.paymenthsm.client.internal.provider.utimaco;

import com.billdesk.paymenthsm.client.internal.core.CommandBuilder;
import com.billdesk.paymenthsm.client.internal.exception.HSMException;
import com.billdesk.paymenthsm.client.internal.exception.HSMProtocolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class UtimacoCommandBuilder implements CommandBuilder {

    private static final Pattern RESPONSE_PATTERN = Pattern.compile("<([A-Z0-9]+)#([^#]+)#.*?(?:\\^([^#]+)#)?>");
    private static final Pattern CONTEXT_TAG_PATTERN = Pattern.compile("#\\^([^#]+)#>$");

    @Override
    public String buildVisaCAVVCommand(String keyBlock, String data) {
        return String.format("<5D#3#%s##%s#>", keyBlock, data);
    }

    @Override
    public String buildMasterCAVVCommand(String keyBlock, String data) {
        return String.format("<5D#3#%s##%s#>", keyBlock, data);
    }

    @Override
    public String buildHMACCommand(String keyBlock, String data) {
        return String.format("<39B#%s##2#%s#>", keyBlock, data);
    }

    @Override
    public String parseResponse(String rawResponse) throws HSMException {
        Matcher matcher = RESPONSE_PATTERN.matcher(rawResponse.trim());
        if (matcher.find()) {
            String commandCode = matcher.group(1);
            String firstField = matcher.group(2);
            String correlationId = matcher.group(3);
            log.info("Parsing for correlation id -> {}", correlationId);

            return switch (commandCode) {
                case "6D" -> firstField; // CAVV
                case "49B" -> firstField; // HMAC
                case "00" -> firstField; // ECHO? PING
                default -> firstField;
            };
        }
        throw new HSMProtocolException("Failed to parse Utimaco response");
    }

    @Override
    public String extractContextTag(String rawResponse) {
        Matcher matcher = CONTEXT_TAG_PATTERN.matcher(rawResponse.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    @Override
    public String embedContextTag(String command, String contextTag) {
        return command.replaceFirst("#>", "#^" + contextTag + "#>");
    }

    @Override
    public String buildHSMPingCommand() {
        return "<00#>";
    }
}