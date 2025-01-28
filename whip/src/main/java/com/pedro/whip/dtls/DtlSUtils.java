package com.pedro.whip.dtls;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

public class DtlSUtils {

    private static int MAX_HANDSHAKE_LOOPS = 200;   
	public static int BUFFER_SIZE = 1024;
	
    private static Exception clientException = null;
    private static Exception serverException = null;
	
	public SSLEngine getEngine(boolean isClient) throws Exception {
		SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        SSLParameters paras = engine.getSSLParameters();
        engine.setSSLParameters(paras);
		engine.setUseClientMode(isClient);
		return engine;
	}
	
	public byte[] cipherData(SSLEngine engine, byte[] data) throws Exception {
        ByteBuffer appBuffer = ByteBuffer.wrap(data, 0, data.length);
        ByteBuffer netBuffer = ByteBuffer.allocate(BUFFER_SIZE * 2);
        engine.wrap(appBuffer, netBuffer);
        netBuffer.flip(); 
        return netBuffer.array();
	}
	
	public byte[] uncipherData(SSLEngine engine, byte[] data) throws Exception {
        ByteBuffer netBuffer = ByteBuffer.wrap(data, 0, data.length);
        ByteBuffer appBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        engine.unwrap(netBuffer, appBuffer);
        appBuffer.flip();  
        return appBuffer.array();
	}
	
	// si peerAddr == null, el handshake es del lado servidor y se tomará la dirección del cliente
	void handshake(SSLEngine engine, DatagramSocket socket, SocketAddress peerAddr) throws Exception {

		 	String side = engine.getUseClientMode() ? "Client" : "Server";
	        boolean endLoops = false;
	        int loops = MAX_HANDSHAKE_LOOPS;
	        engine.beginHandshake();
	        while (!endLoops &&
	                (serverException == null) && (clientException == null)) {

	            if (--loops < 0) {
	                throw new RuntimeException(
	                        "Too much loops to produce handshake packets");
	            }

	            SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
	            log(side, "=======handshake(" + loops + ", " + hs + ")=======");
	            if (hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {

	                log(side, "Receive DTLS records, handshake status is " + hs);

	                ByteBuffer iNet;
	                ByteBuffer iApp;
	                if (hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
	                    byte[] buf = new byte[BUFFER_SIZE];
	                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
	                    try {
	                        socket.receive(packet);
	                        ////
	                        if(peerAddr == null) {
	                        	peerAddr = packet.getSocketAddress();
	                        }
	                        ////
	                    } catch (SocketTimeoutException ste) {
	                        log(side, "Warning: " + ste);

	                        List<DatagramPacket> packets = new ArrayList<>();
	                        boolean finished = onReceiveTimeout(
	                                engine, peerAddr, side, packets);

	                        log(side, "Reproduced " + packets.size() + " packets");
	                        for (DatagramPacket p : packets) {
//	                            printHex("Reproduced packet",
//	                                p.getData(), p.getOffset(), p.getLength());
	                            System.out.println("Reproduced packet: " + p.getData());
	                            socket.send(p);
	                        }

	                        if (finished) {
	                            log(side, "Handshake status is FINISHED "
	                                    + "after calling onReceiveTimeout(), "
	                                    + "finish the loop");
	                            endLoops = true;
	                        }

	                        log(side, "New handshake status is "
	                                + engine.getHandshakeStatus());

	                        continue;
	                    }

	                    iNet = ByteBuffer.wrap(buf, 0, packet.getLength());
	                    iApp = ByteBuffer.allocate(BUFFER_SIZE);
	                } else {
	                    iNet = ByteBuffer.allocate(0);
	                    iApp = ByteBuffer.allocate(BUFFER_SIZE);
	                }

	                SSLEngineResult r = engine.unwrap(iNet, iApp);
	                SSLEngineResult.Status rs = r.getStatus();
	                hs = r.getHandshakeStatus();
	                if (rs == SSLEngineResult.Status.OK) {
	                    // OK
	                } else if (rs == SSLEngineResult.Status.BUFFER_OVERFLOW) {
	                    log(side, "BUFFER_OVERFLOW, handshake status is " + hs);

	                    // the client maximum fragment size config does not work?
	                    throw new Exception("Buffer overflow: " +
	                        "incorrect client maximum fragment size");
	                } else if (rs == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
	                    log(side, "BUFFER_UNDERFLOW, handshake status is " + hs);

	                    // bad packet, or the client maximum fragment size
	                    // config does not work?
	                    if (hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
	                        throw new Exception("Buffer underflow: " +
	                            "incorrect client maximum fragment size");
	                    } // otherwise, ignore this packet
	                } else if (rs == SSLEngineResult.Status.CLOSED) {
	                    throw new Exception(
	                            "SSL engine closed, handshake status is " + hs);
	                } else {
	                    throw new Exception("Can't reach here, result is " + rs);
	                }

	                if (hs == SSLEngineResult.HandshakeStatus.FINISHED) {
	                    log(side, "Handshake status is FINISHED, finish the loop");
	                    endLoops = true;
	                }
	            } else if (hs == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
	                List<DatagramPacket> packets = new ArrayList<>();
	                boolean finished = produceHandshakePackets(
	                    engine, peerAddr, side, packets);

	                log(side, "Produced " + packets.size() + " packets");
	                for (DatagramPacket p : packets) {
	                    socket.send(p);
	                }

	                if (finished) {
	                    log(side, "Handshake status is FINISHED "
	                            + "after producing handshake packets, "
	                            + "finish the loop");
	                    endLoops = true;
	                }
	            } else if (hs == SSLEngineResult.HandshakeStatus.NEED_TASK) {
	                runDelegatedTasks(engine);
	            } else if (hs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
	                log(side,
	                    "Handshake status is NOT_HANDSHAKING, finish the loop");
	                endLoops = true;
	            } else if (hs == SSLEngineResult.HandshakeStatus.FINISHED) {
	                throw new Exception(
	                        "Unexpected status, SSLEngine.getHandshakeStatus() "
	                                + "shouldn't return FINISHED");
	            } else {
	                throw new Exception(
	                        "Can't reach here, handshake status is " + hs);
	            }
	        }

	        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
	        log(side, "Handshake finished, status is " + hs);

	        SSLSession session = engine.getSession();
	        if (session == null) {
	            throw new Exception("Handshake finished, but session is null");
	        }
	        log(side, "Negotiated protocol is " + session.getProtocol());
	        log(side, "Negotiated cipher suite is " + session.getCipherSuite());

	        // handshake status should be NOT_HANDSHAKING
	        //
	        // According to the spec, SSLEngine.getHandshakeStatus() can't
	        // return FINISHED.
	        if (hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
	            throw new Exception("Unexpected handshake status " + hs);
	        }
	    }

	    // produce handshake packets
	    boolean produceHandshakePackets(SSLEngine engine, SocketAddress socketAddr,
	            String side, List<DatagramPacket> packets) throws Exception {

	        boolean endLoops = false;
	        int loops = MAX_HANDSHAKE_LOOPS / 2;
	        while (!endLoops &&
	                (serverException == null) && (clientException == null)) {

	            if (--loops < 0) {
	                throw new RuntimeException(
	                        "Too much loops to produce handshake packets");
	            }

	            ByteBuffer oNet = ByteBuffer.allocate(32768);
	            ByteBuffer oApp = ByteBuffer.allocate(0);
	            SSLEngineResult r = engine.wrap(oApp, oNet);
	            oNet.flip();

	            SSLEngineResult.Status rs = r.getStatus();
	            SSLEngineResult.HandshakeStatus hs = r.getHandshakeStatus();
	            log(side, "----produce handshake packet(" +
	                    loops + ", " + rs + ", " + hs + ")----");
	            if (rs == SSLEngineResult.Status.BUFFER_OVERFLOW) {
	                // the client maximum fragment size config does not work?
	                throw new Exception("Buffer overflow: " +
	                            "incorrect server maximum fragment size");
	            } else if (rs == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
	                log(side,
	                        "Produce handshake packets: BUFFER_UNDERFLOW occured");
	                log(side,
	                        "Produce handshake packets: Handshake status: " + hs);
	                // bad packet, or the client maximum fragment size
	                // config does not work?
	                if (hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
	                    throw new Exception("Buffer underflow: " +
	                            "incorrect server maximum fragment size");
	                } // otherwise, ignore this packet
	            } else if (rs == SSLEngineResult.Status.CLOSED) {
	                throw new Exception("SSLEngine has closed");
	            } else if (rs == SSLEngineResult.Status.OK) {
	                // OK
	            } else {
	                throw new Exception("Can't reach here, result is " + rs);
	            }

	            // SSLEngineResult.Status.OK:
	            if (oNet.hasRemaining()) {
	                byte[] ba = new byte[oNet.remaining()];
	                oNet.get(ba);
	                DatagramPacket packet = createHandshakePacket(ba, socketAddr);
	                packets.add(packet);
	            }

	            if (hs == SSLEngineResult.HandshakeStatus.FINISHED) {
	                log(side, "Produce handshake packets: "
	                            + "Handshake status is FINISHED, finish the loop");
	                return true;
	            }

	            boolean endInnerLoop = false;
	            SSLEngineResult.HandshakeStatus nhs = hs;
	            while (!endInnerLoop) {
	                if (nhs == SSLEngineResult.HandshakeStatus.NEED_TASK) {
	                    runDelegatedTasks(engine);
	                } else if (nhs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP ||
	                    nhs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {

	                    endInnerLoop = true;
	                    endLoops = true;
	                } else if (nhs == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
	                    endInnerLoop = true;
	                } else if (nhs == SSLEngineResult.HandshakeStatus.FINISHED) {
	                    throw new Exception(
	                            "Unexpected status, SSLEngine.getHandshakeStatus() "
	                                    + "shouldn't return FINISHED");
	                } else {
	                    throw new Exception("Can't reach here, handshake status is "
	                            + nhs);
	                }
	                nhs = engine.getHandshakeStatus();
	            }
	        }

	        return false;
	    }

	    DatagramPacket createHandshakePacket(byte[] ba, SocketAddress socketAddr) {
	        return new DatagramPacket(ba, ba.length, socketAddr);
	    }

	    // produce application packets
	    List<DatagramPacket> produceApplicationPackets(
	            SSLEngine engine, ByteBuffer source,
	            SocketAddress socketAddr) throws Exception {

	        List<DatagramPacket> packets = new ArrayList<>();
	        ByteBuffer appNet = ByteBuffer.allocate(32768);
	        SSLEngineResult r = engine.wrap(source, appNet);
	        appNet.flip();

	        SSLEngineResult.Status rs = r.getStatus();
	        if (rs == SSLEngineResult.Status.BUFFER_OVERFLOW) {
	            // the client maximum fragment size config does not work?
	            throw new Exception("Buffer overflow: " +
	                        "incorrect server maximum fragment size");
	        } else if (rs == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
	            // unlikely
	            throw new Exception("Buffer underflow during wraping");
	        } else if (rs == SSLEngineResult.Status.CLOSED) {
	                throw new Exception("SSLEngine has closed");
	        } else if (rs == SSLEngineResult.Status.OK) {
	            // OK
	        } else {
	            throw new Exception("Can't reach here, result is " + rs);
	        }

	        // SSLEngineResult.Status.OK:
	        if (appNet.hasRemaining()) {
	            byte[] ba = new byte[appNet.remaining()];
	            appNet.get(ba);
	            DatagramPacket packet =
	                    new DatagramPacket(ba, ba.length, socketAddr);
	            packets.add(packet);
	        }

	        return packets;
	    }

	    // Get a datagram packet for the specified handshake type.
	    static DatagramPacket getPacket(
	            List<DatagramPacket> packets, byte handshakeType) {
	        boolean matched = false;
	        for (DatagramPacket packet : packets) {
	            byte[] data = packet.getData();
	            int offset = packet.getOffset();
	            int length = packet.getLength();

	            // Normally, this pakcet should be a handshake message
	            // record.  However, even if the underlying platform
	            // splits the record more, we don't really worry about
	            // the improper packet loss because DTLS implementation
	            // should be able to handle packet loss properly.
	            //
	            // See RFC 6347 for the detailed format of DTLS records.
	            if (handshakeType == -1) {      // ChangeCipherSpec
	                // Is it a ChangeCipherSpec message?
	                matched = (length == 14) && (data[offset] == 0x14);
	            } else if ((length >= 25) &&    // 25: handshake mini size
	                (data[offset] == 0x16)) {   // a handshake message

	                // check epoch number for initial handshake only
	                if (data[offset + 3] == 0x00) {     // 3,4: epoch
	                    if (data[offset + 4] == 0x00) { // plaintext
	                        matched =
	                            (data[offset + 13] == handshakeType);
	                    } else {                        // cipherext
	                        // The 1st ciphertext is a Finished message.
	                        //
	                        // If it is not proposed to loss the Finished
	                        // message, it is not necessary to check the
	                        // following packets any mroe as a Finished
	                        // message is the last handshake message.
	                        matched = (handshakeType == 20);
	                    }
	                }
	            }

	            if (matched) {
	                return packet;
	            }
	        }

	        return null;
	    }

	    // run delegated tasks
	    void runDelegatedTasks(SSLEngine engine) throws Exception {
	        Runnable runnable;
	        while ((runnable = engine.getDelegatedTask()) != null) {
	            runnable.run();
	        }

	        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
	        if (hs == SSLEngineResult.HandshakeStatus.NEED_TASK) {
	            throw new Exception("handshake shouldn't need additional tasks");
	        }
	    }	 
	 
	    // retransmission if timeout
	    boolean onReceiveTimeout(SSLEngine engine, SocketAddress socketAddr,
	            String side, List<DatagramPacket> packets) throws Exception {

	        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
	        if (hs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
	            return false;
	        } else {
	            // retransmission of handshake messages
	            return produceHandshakePackets(engine, socketAddr, side, packets);
	        }
	    }	 
	 
	    static void log(String side, String message) {
	        System.out.println(side + ": " + message);
	    }
	
}