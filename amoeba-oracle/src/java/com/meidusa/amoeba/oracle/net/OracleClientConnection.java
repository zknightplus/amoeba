package com.meidusa.amoeba.oracle.net;

import java.nio.channels.SocketChannel;

import org.apache.commons.pool.ObjectPool;

import com.meidusa.amoeba.net.Connection;
import com.meidusa.amoeba.net.Sessionable;
import com.meidusa.amoeba.oracle.context.OracleProxyRuntimeContext;
import com.meidusa.amoeba.oracle.handler.OracleMessageHandler;

public class OracleClientConnection extends OracleConnection {

	public OracleClientConnection(SocketChannel channel, long createStamp) {
		super(channel, createStamp);
		ObjectPool pool = OracleProxyRuntimeContext.getInstance().getPoolMap().get("default");
		try {
			Connection dst = (Connection)pool.borrowObject();
			this.setMessageHandler(new OracleMessageHandler(this,dst));
			dst.setMessageHandler(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void handleMessage(Connection conn, byte[] message) {
		this.getMessageHandler().handleMessage(conn, message);
	}
	
	protected void close(Exception exception){
		if(this.getMessageHandler() instanceof Sessionable){
			Sessionable session = (Sessionable)this.getMessageHandler();
			session.endSession();
			this.setMessageHandler(null);
		}
		super.close(exception);
	}
}