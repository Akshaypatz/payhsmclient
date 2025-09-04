package com.billdesk.paymenthsm.client.internal.connection;

import com.billdesk.paymenthsm.client.internal.config.HSMConfig;
import com.billdesk.paymenthsm.client.internal.core.CommandBuilder;
import com.billdesk.paymenthsm.client.internal.core.ResponseDispatcher;
import com.billdesk.paymenthsm.client.internal.exception.*;
import com.billdesk.paymenthsm.client.internal.model.HSMNode;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class AsyncSocketConnection {
    private final Socket socket;
    private final PrintWriter writer;
    private final ResponseDispatcher responseDispatcher;
    private final CommandBuilder commandBuilder;
    private volatile boolean running = true;
    private final Thread listenerThread;
    private static final Long PING_HSM_TIMEOUT = 100L;

    public AsyncSocketConnection(Socket socket, ResponseDispatcher responseDispatcher,
                                 CommandBuilder commandBuilder, HSMConfig config, HSMNode hsmNode) throws IOException {
        try {
            this.socket = socket;
            this.responseDispatcher = responseDispatcher;
            this.commandBuilder = commandBuilder;
            this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            //TODO: kill this daemon thread later when socket closes??
            this.listenerThread = new Thread(this::listenForResponses);
            this.listenerThread.setName("HSM-Listener-" + socket.getPort());
            this.listenerThread.setDaemon(true);
            this.listenerThread.start();
        } catch (IOException e) {
            log.warn("Exception occured while connecting to socket listener to HSM {}:{}", hsmNode.getIp(), hsmNode.getPort(),e);
            throw e;
        }
        catch (Exception e) {
            log.error("Unexpected error creating AsyncSocketConnection for HSM {}:{}",
                    hsmNode.getIp(), hsmNode.getPort(), e);
            throw new IOException("Failed to create AsyncSocketConnection", e);
        }
    }

    private void listenForResponses() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder responseBuilder = new StringBuilder();
            char[] buffer = new char[1024];
            int bytesRead;

            while (running && (bytesRead = reader.read(buffer)) != -1) {
                responseBuilder.append(buffer, 0, bytesRead);
                String data = responseBuilder.toString();
                log.info("Data received from HSM : {}", data);

                //TODO: this seems specific to utimaco. check if can be made generic
                if (data.trim().endsWith(">")) {
                    processHsmResponse(data.trim());
                    responseBuilder.setLength(0);
                }
            }
        } catch (SocketTimeoutException e) {
            // expected case but nothing can be done
            log.error("Socked timed out {}:{} !", socket.getInetAddress().getHostAddress(), socket.getPort(), e);
            //can continue here to avoid socker timeout.
        } catch (IOException e) {
            log.error("Closing listener for socket {}:{}", socket.getInetAddress().getHostAddress(), socket.getPort(), e);
            if (running) {
                responseDispatcher.completeAllWithError(new HSMConnectionException("Socket connection closed"));
            }
        }
    }

    private void processHsmResponse(String fullResponse) {
        try {
            String contextTag = commandBuilder.extractContextTag(fullResponse);
            log.info("Context Tag : {}", contextTag);
            String responseData = commandBuilder.parseResponse(fullResponse);
            log.info("Important Value from hsm : {}", responseData);
            if (contextTag != null) {
                responseDispatcher.completeResponse(contextTag, responseData);
            }
        } catch (Exception e) {
            log.error("Error processing response: {}", e.getMessage());
        }
    }

    public CompletableFuture<String> pingHsm() {
        return sendCommandToHSM(commandBuilder.buildHSMPingCommand(), generatePingCorrelationId(), PING_HSM_TIMEOUT);
    }

    private String generatePingCorrelationId() {
        return "PING_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public CompletableFuture<String> sendCommandToHSM(String command, String contextTag) {
        return sendCommandToHSM(command, contextTag, null);
    }

    public CompletableFuture<String> sendCommandToHSM(String command, String contextTag, Long timeoutMs) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            log.info("Command to HSM -> {}", command);
            if (timeoutMs != null) {
                responseDispatcher.registerRequest(contextTag, future, timeoutMs);
            } else {
                responseDispatcher.registerRequest(contextTag, future);
            }
            String formattedCommand = commandBuilder.embedContextTag(command, contextTag);
            log.info("Formatted command to HSM -> {}", formattedCommand);
            if (!isConnected()) {
                throw new HSMConnectionException("Socket is not connected");
            }
            writer.println(formattedCommand);
            writer.flush();
            if (writer.checkError()) {
                throw new HSMIOException("Failed to write command to HSM output stream");
            }
        } catch (Exception e) {
            responseDispatcher.completeHSMCommandSendFailureWithError(command, contextTag, e);
        }
        return future;
    }

    public void close() {
        running = false;
        try {
            listenerThread.interrupt();
            listenerThread.join(5000);
            log.debug("Closing socket and associated response dispatcher");
            responseDispatcher.shutdown();
            socket.close();
        } catch (IOException e) {
            log.warn("Exception occurred while closing socket!", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for listener thread to terminate");
        }
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }
}