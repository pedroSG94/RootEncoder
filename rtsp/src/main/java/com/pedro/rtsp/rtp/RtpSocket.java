package com.pedro.rtsp.rtp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Created by pedro on 13/02/17.
 */

public class RtpSocket {

    private MulticastSocket socket;
    private DatagramPacket packet;

    public RtpSocket() {
        try {
            socket = new MulticastSocket();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendPacket(byte[] data, String ip, int port) {
        synchronized (socket) {
            try {
                packet = new DatagramPacket(data, data.length, InetAddress.getByName(ip), port);
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
