package com.pedro.rtsp.rtp;

import android.util.Log;
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

    private String TAG = "RtpUDP";
    private DatagramSocket s;
    private InetAddress addr;

    public RtpUDP(String host, boolean broadcast) {
        try {
            this.addr = InetAddress.getByName(host);
            s = new DatagramSocket();
            s.setBroadcast(broadcast);
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        s.close();
    }

    public void sendPacket(final byte[] data, final int offset, final int size, final int port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramPacket p;
                    p = new DatagramPacket(data, offset, size, addr, port);
                    s.send(p);
                    Log.i(TAG, "send packet... " + size + " Size, " + port + " Port");
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }
}
