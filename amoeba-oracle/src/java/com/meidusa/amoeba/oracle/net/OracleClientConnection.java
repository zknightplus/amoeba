package com.meidusa.amoeba.oracle.net;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.meidusa.amoeba.net.Connection;
import com.meidusa.amoeba.oracle.handler.OracleQueryDispatcher;
import com.meidusa.amoeba.oracle.net.packet.AcceptPacket;
import com.meidusa.amoeba.oracle.net.packet.AnoDataPacket;
import com.meidusa.amoeba.oracle.net.packet.AnoResponseDataPacket;
import com.meidusa.amoeba.oracle.net.packet.ConnectPacket;
import com.meidusa.amoeba.oracle.net.packet.SQLnetDef;
import com.meidusa.amoeba.oracle.net.packet.T4C7OversionDataPacket;
import com.meidusa.amoeba.oracle.net.packet.T4C7OversionResponseDataPacket;
import com.meidusa.amoeba.oracle.net.packet.T4C8TTIdtyDataPacket;
import com.meidusa.amoeba.oracle.net.packet.T4C8TTIdtyResponseDataPacket;
import com.meidusa.amoeba.oracle.net.packet.T4C8TTIproDataPacket;
import com.meidusa.amoeba.oracle.net.packet.T4C8TTIproResponseDataPacket;
import com.meidusa.amoeba.oracle.net.packet.T4CTTIMsgPacket;
import com.meidusa.amoeba.oracle.net.packet.T4CTTIfunPacket;
import com.meidusa.amoeba.oracle.net.packet.T4CTTIoAuthDataPacket;
import com.meidusa.amoeba.oracle.net.packet.T4CTTIoAuthKeyDataPacket;
import com.meidusa.amoeba.oracle.net.packet.T4CTTIoAuthKeyResponseDataPacket;
import com.meidusa.amoeba.oracle.net.packet.T4CTTIoAuthResponseDataPacket;
import com.meidusa.amoeba.oracle.net.packet.assist.T4CTTIoer;
import com.meidusa.amoeba.oracle.util.ByteUtil;
import com.meidusa.amoeba.util.StringUtil;

public class OracleClientConnection extends OracleConnection implements SQLnetDef {

    private static Logger        logger        = Logger.getLogger(OracleClientConnection.class);

    private String               encryptedSK   = null;
    private Map<Integer, byte[]> lobLocaterMap = new HashMap<Integer, byte[]>();
    private boolean              isLobOps      = false;

    public OracleClientConnection(SocketChannel channel, long createStamp){
        super(channel, createStamp);
    }

    public boolean isLobOps() {
        return isLobOps;
    }

    public void setLobOps(boolean isLobOps) {
        this.isLobOps = isLobOps;
    }

    public Map<Integer, byte[]> getLobLocaterMap() {
        return lobLocaterMap;
    }

    public void setLobLocaterMap(Map<Integer, byte[]> lobLocaterMap) {
        this.lobLocaterMap = lobLocaterMap;
    }

    public void handleMessage(Connection conn, byte[] message) {
        OracleClientConnection clientConn = (OracleClientConnection) conn;
        ByteBuffer byteBuffer = null;

        String receivePacket = null;
        String responsePacket = null;

        switch (message[4]) {
            case NS_PACKT_TYPE_CONNECT:
                ConnectPacket connPacket = new ConnectPacket();
                connPacket.init(message, clientConn);

                clientConn.setAnoEnabled(connPacket.anoEnabled);
                AcceptPacket aptPacket = new AcceptPacket();
                byteBuffer = aptPacket.toByteBuffer(clientConn);
                if (logger.isDebugEnabled()) {
                    receivePacket = "NS_PACKT_TYPE_CONNECT";
                    responsePacket = "AcceptPacket";
                }
                break;

            case NS_PACKT_TYPE_DATA:
                if (clientConn.isAnoEnabled() && AnoDataPacket.isAnoType(message)) {
                    AnoResponseDataPacket anoRespPacket = new AnoResponseDataPacket();
                    byteBuffer = anoRespPacket.toByteBuffer(clientConn);
                    if (logger.isDebugEnabled()) {
                        receivePacket = "AnoDataPacket";
                        responsePacket = "AnoResponseDataPacket";
                    }

                } else if (T4CTTIMsgPacket.isMsgType(message, T4CTTIMsgPacket.TTIPRO)) {
                    T4C8TTIproDataPacket proPacket = new T4C8TTIproDataPacket();
                    proPacket.init(message, clientConn);

                    T4C8TTIproResponseDataPacket proRespPacket = new T4C8TTIproResponseDataPacket();
                    clientConn.setProtocolField(proRespPacket);
                    byteBuffer = proRespPacket.toByteBuffer(clientConn);
                    if (logger.isDebugEnabled()) {
                        receivePacket = "T4C8TTIproDataPacket";
                        responsePacket = "T4C8TTIproResponseDataPacket";
                    }

                } else if (T4CTTIMsgPacket.isMsgType(message, T4CTTIMsgPacket.TTIDTY)) {
                    T4C8TTIdtyDataPacket dtyPacket = new T4C8TTIdtyDataPacket();
                    dtyPacket.init(message, clientConn);

                    T4C8TTIdtyResponseDataPacket dtyRespPacket = new T4C8TTIdtyResponseDataPacket();
                    clientConn.setBasicTypes();
                    byteBuffer = dtyRespPacket.toByteBuffer(clientConn);
                    if (logger.isDebugEnabled()) {
                        receivePacket = "T4C8TTIdtyDataPacket";
                        responsePacket = "T4C8TTIdtyResponseDataPacket";
                    }

                } else if (T4CTTIfunPacket.isFunType(message, T4CTTIfunPacket.OVERSION)) {
                    T4C7OversionDataPacket versionPacket = new T4C7OversionDataPacket();
                    versionPacket.init(message, clientConn);

                    T4C7OversionResponseDataPacket versionRespPacket = new T4C7OversionResponseDataPacket();
                    byteBuffer = versionRespPacket.toByteBuffer(clientConn);
                    if (logger.isDebugEnabled()) {
                        receivePacket = "T4C7OversionDataPacket";
                        responsePacket = "T4C7OversionResponseDataPacket";
                    }

                } else if (T4CTTIfunPacket.isFunType(message, T4CTTIfunPacket.OSESSKEY)) {
                    T4CTTIoAuthKeyDataPacket authKeyPacket = new T4CTTIoAuthKeyDataPacket();
                    authKeyPacket.init(message, clientConn);

                    T4CTTIoAuthKeyResponseDataPacket authKeyRespPacket = new T4CTTIoAuthKeyResponseDataPacket();
                    this.encryptedSK = StringUtil.getRandomString(16);
                    authKeyRespPacket.encryptedSK = this.encryptedSK;
                    byteBuffer = authKeyRespPacket.toByteBuffer(clientConn);
                    if (logger.isDebugEnabled()) {
                        receivePacket = "T4CTTIoAuthKeyDataPacket";
                        responsePacket = "T4CTTIoAuthKeyResponseDataPacket";
                    }

                } else if (T4CTTIfunPacket.isFunType(message, T4CTTIfunPacket.OAUTH)) {
                    T4CTTIoAuthDataPacket authPacket = new T4CTTIoAuthDataPacket();
                    authPacket.init(message, clientConn);

                    String encryptedPassword = T4CTTIoAuthDataPacket.encryptPassword(getUser(), getPassword(), encryptedSK.getBytes(), getConversion());

                    T4CTTIoAuthResponseDataPacket authRespPacket = new T4CTTIoAuthResponseDataPacket();
                    authRespPacket.oer = new T4CTTIoer();
                    if (!StringUtil.equals(encryptedPassword, authPacket.map.get(T4CTTIoAuthResponseDataPacket.AUTH_PASSWORD))) {
                        authRespPacket.oer.retCode = 1017;
                        authRespPacket.oer.errorMsg = "ORA-01017: invalid username/password; logon denied";
                        this.setAuthenticated(false);
                    } else {
                        this.setMessageHandler(new OracleQueryDispatcher(this));
                    }

                    byteBuffer = authRespPacket.toByteBuffer(clientConn);

                    if (logger.isDebugEnabled()) {
                        receivePacket = "T4CTTIoAuthDataPacket";
                        responsePacket = "T4CTTIoAuthResponseDataPacket";
                    }
                }

                break;
        }

        if (byteBuffer != null) {
            if (logger.isDebugEnabled()) {
                int size = ((message[0] & 0xff) << 8) | (message[1] & 0xff);
                logger.debug("");
                logger.debug("#amoeba auth message from client ===================================================");
                logger.debug(">>" + receivePacket + " receive from client[" + size + "]:" + ByteUtil.toHex(message, 0, message.length));

                byte[] respMessage = byteBuffer.array();
                size = ((respMessage[0] & 0xff) << 8) | (respMessage[1] & 0xff);
                logger.debug("<<" + responsePacket + " send to client[" + size + "]:" + ByteUtil.toHex(respMessage, 0, respMessage.length));
            }
            this.postMessage(byteBuffer);
        }

    }

}
