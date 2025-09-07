package com.billdesk.paymenthsm.client.internal.connection;

import com.billdesk.paymenthsm.client.internal.core.CommandBuilder;
import com.billdesk.paymenthsm.client.internal.config.HSMConfig;
import com.billdesk.paymenthsm.client.internal.model.HSMNode;
import com.billdesk.paymenthsm.client.internal.core.ResponseDispatcher;
import com.billdesk.paymenthsm.client.internal.util.HSMConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.KeyStore;

@Slf4j
public class AsyncSocketFactory implements PooledObjectFactory<AsyncSocketConnection> {
    private final HSMNode hsmNode;
    private final HSMConfig hsmConfig;
    private final CommandBuilder commandBuilder;
    private final SSLSocketFactory sslSocketFactory;

    public AsyncSocketFactory(HSMNode hsmNode, HSMConfig hsmConfig,
                              CommandBuilder commandBuilder) {
        this.hsmNode = hsmNode;
        this.hsmConfig = hsmConfig;
        this.commandBuilder = commandBuilder;
        if (hsmConfig.isEnableSSL()) {
            try {
                this.sslSocketFactory = createSSLSocketFactory(
                        hsmConfig.getTruststorePath(),
                        hsmConfig.getTruststorePassword()
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize SSL context for HSM client", e);
            }
        } else {
            this.sslSocketFactory = null;
        }
    }

    private SSLSocketFactory createSSLSocketFactory(String truststorePath, String truststorePassword) throws Exception {
        KeyStore ts = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            ts.load(fis, truststorePassword.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return sslContext.getSocketFactory();
    }

    @Override
    public PooledObject<AsyncSocketConnection> makeObject() throws IOException {
        try {
            Socket socket;
            if (hsmConfig.isEnableSSL()) {
                SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(hsmNode.getIp(), hsmNode.getPort());
                sslSocket.setSoTimeout(HSMConstants.HSM_SOCKET_READ_TIMEOUT);
                sslSocket.setKeepAlive(true);
                sslSocket.setTcpNoDelay(true);
                sslSocket.startHandshake();
                socket = sslSocket;
                log.info("Created TLS socket connection to {}:{}", hsmNode.getIp(), hsmNode.getPort());
            } else {
                socket = new Socket();
                socket.connect(new InetSocketAddress(hsmNode.getIp(), hsmNode.getPort()), HSMConstants.HSM_SOCKET_CONNECT_TIMEOUT);
                socket.setSoTimeout(HSMConstants.HSM_SOCKET_READ_TIMEOUT);
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                log.info("Created plain socket connection to {}:{}", hsmNode.getIp(), hsmNode.getPort());
            }
            ResponseDispatcher responseDispatcher = new ResponseDispatcher(hsmNode);
            AsyncSocketConnection asyncConn = new AsyncSocketConnection(socket, responseDispatcher, commandBuilder, hsmConfig, hsmNode);
            return new DefaultPooledObject<>(asyncConn);
        } catch (SocketTimeoutException ste) {
            throw new IOException("Timeout while connecting to HSM " + hsmNode.getIp() + ":" + hsmNode.getPort(), ste);
        } catch (ConnectException ce) {
            throw new IOException("Unable to connect to HSM " + hsmNode.getIp() + ":" + hsmNode.getPort(), ce);
        } catch (IOException ioe) {
            log.error("IO Exception due to {}",ioe.getMessage());
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