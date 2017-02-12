package com.pedro.rtsp.rtp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created by pedro on 11/02/17.
 */

public class RtpUDP {

    private DatagramSocket s;
    private InetAddress addr;
    private int port;

    public RtpUDP(String host, int port, boolean broadcast) {
        try {
            this.addr = InetAddress.getByName(host);
            this.port = port;
            s = new DatagramSocket();
            s.setBroadcast(broadcast);
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        s.close();
    }

    public void sendPacket(byte[] data, int offset, int size) {
        try {
            DatagramPacket p;
            p = new DatagramPacket(data, offset, size, addr, port);
            s.send(p);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
