package com.billdesk.paymenthsm.client.internal.core;

import com.billdesk.paymenthsm.client.internal.exception.HSMException;
import com.billdesk.paymenthsm.client.internal.enums.ACS_BANK;
import com.billdesk.paymenthsm.client.internal.enums.Provider;

import java.util.concurrent.CompletableFuture;

public interface HSMService {
    CompletableFuture<String> generateVisaCAVV(ACS_BANK bank, String data) throws HSMException;
    CompletableFuture<String> generateMasterCAVV(ACS_BANK bank, String data) throws HSMException;
    CompletableFuture<String> generateHMAC(String keyName, String data) throws HSMException;
    Provider getProvider();
}
