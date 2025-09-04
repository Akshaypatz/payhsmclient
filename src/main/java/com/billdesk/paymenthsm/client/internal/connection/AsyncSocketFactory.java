package com.billdesk.paymenthsm.client.internal.connection;

import com.billdesk.paymenthsm.client.internal.core.CommandBuilder;
import com.billdesk.paymenthsm.client.internal.config.HSMConfig;
import com.billdesk.paymenthsm.client.internal.model.HSMNode;
import com.billdesk.paymenthsm.client.internal.core.ResponseDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

@Slf4j
public class AsyncSocketFactory implements PooledObjectFactory<AsyncSocketConnection> {
    public static final int HSM_SOCKET_CONNECT_TIMEOUT = 5000;
    public static final int HSM_SOCKET_READ_TIMEOUT = 45000;
    private final HSMNode hsmNode;
    private final HSMConfig hsmConfig;
    private final CommandBuilder commandBuilder;

    public AsyncSocketFactory(HSMNode hsmNode, HSMConfig hsmConfig,
                              CommandBuilder commandBuilder) {
        this.hsmNode = hsmNode;
        this.hsmConfig = hsmConfig;
        this.commandBuilder = commandBuilder;
    }

    @Override
    public PooledObject<AsyncSocketConnection> makeObject() throws IOException {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(hsmNode.getIp(), hsmNode.getPort()), HSM_SOCKET_CONNECT_TIMEOUT);
            socket.setSoTimeout(HSM_SOCKET_READ_TIMEOUT);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            ResponseDispatcher responseDispatcher = new ResponseDispatcher(hsmNode);
            log.debug("Creating socket connection to {}:{}", hsmNode.getIp(), hsmNode.getPort());
            AsyncSocketConnection asyncConn = new AsyncSocketConnection(socket, responseDispatcher, commandBuilder, hsmConfig, hsmNode);
            return new DefaultPooledObject<>(asyncConn);
        } catch (SocketTimeoutException ste) {
            throw new IOException("Timeout while connecting to HSM " + hsmNode.getIp() + ":" + hsmNode.getPort(), ste);
        } catch (ConnectException ce) {
            throw new IOException("Unable to connect to HSM " + hsmNode.getIp() + ":" + hsmNode.getPort(), ce);
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            throw new IOException("Failed to create AsyncSocketConnection for HSM " +
                    hsmNode.getIp() + ":" + hsmNode.getPort(), e);
        }
    }

    @Override
    public void destroyObject(PooledObject<AsyncSocketConnection> p) {
        p.getObject().close();
    }

    @Override
    public boolean validateObject(PooledObject<AsyncSocketConnection> p) {
        return p.getObject().isConnected();

    }

    @Override
    public void activateObject(PooledObject<AsyncSocketConnection> p) {
        // kept it blank intentionally
    }

    @Override
    public void passivateObject(PooledObject<AsyncSocketConnection> p) {
        // kept it blank intentionally
    }
}