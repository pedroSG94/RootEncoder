package com.pedro.rtsp.rtsp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

/**
 * Created by pedro on 10/02/17.
 */

public class RtspClient {

    private final String TAG = "RtspClient";
    private final long mTimestamp;

    private String host = "127.0.0.1";
    private int port = 1935;
    private String path = "/live/pedro123";

    private int trackVideo = 1;
    private int trackAudio = 0;
    private boolean isUDP = true;
    private int mCSeq = 0;
    private String authorization = null;
    private String sessionId;

    //sockets objects
    private Socket connectionSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread thread;

    public RtspClient() {
        long uptime = System.currentTimeMillis();
        mTimestamp = (uptime / 1000) << 32 & (((uptime - ((uptime / 1000) * 1000)) >> 32) / 1000); // NTP timestamp
    }

    public void connect() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connectionSocket = new Socket(host, port);
                    reader = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                    writer = new BufferedWriter(new OutputStreamWriter(connectionSocket.getOutputStream()));

                    writer.write(sendAnnounce());
                    writer.flush();
                    getResponse();
                    writer.write(sendSetup(trackAudio, isUDP));
                    writer.flush();
                    getResponse();
                    writer.write(sendSetup(trackVideo, isUDP));
                    writer.flush();
                    getResponse();
                    writer.write(sendRecord());
                    writer.flush();
                    getResponse();
                } catch (IOException e) {
                    e.printStackTrace();

                }
            }
        });
        thread.start();
    }

    public void disconnect() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    writer.write(sendTearDown());
                    connectionSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    private String sendAnnounce() {
        String body = createBody();
        String request;
        if (authorization == null) {
            request = "ANNOUNCE rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n" +
                    "CSeq: " + (++mCSeq) + "\r\n" +
                    "Content-Length: " + body.length() + "\r\n" +
                    "Content-Type: application/sdp\r\n\r\n" +
                    body;
        } else {
            request = "ANNOUNCE rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n" +
                    "CSeq: " + (++mCSeq) + "\r\n" +
                    "Content-Length: " + body.length() + "\r\n" +
                    "Authorization: " + authorization + "\r\n" +
                    "Content-Type: application/sdp\r\n\r\n" +
                    body;
        }
        return request;
    }

    private String createBody() {
        return "v=0\r\n" +
                // TODO: Add IPV6 support
                "o=- " + mTimestamp + " " + mTimestamp + " IN IP4 " + "127.0.0.1" + "\r\n" +
                "s=Unnamed\r\n" +
                "i=N/A\r\n" +
                "c=IN IP4 " + host + "\r\n" +
                // thread=0 0 means the session is permanent (we don'thread know when it will stop)
                "thread=0 0\r\n" +
                "a=recvonly\r\n" +
                AudioBody.createAudioBody(trackAudio) +
                VideoBody.createVideoBody(trackVideo);
    }

    private String sendSetup(int track, boolean isUDP) {
        String params = (isUDP) ?
                ("UDP;unicast;client_port=" + (5000 + 2 * track) + "-" + (5000 + 2 * track + 1) + ";mode=receive") :
                ("TCP;interleaved=" + 2 * track + "-" + (2 * track + 1));
        return "SETUP rtsp://" + host + ":" + port + path + "/trackID=" + track + " RTSP/1.0\r\n" +
                "Transport: RTP/AVP/" + params + "\r\n" +
                addHeaders(authorization);
    }

    private String sendOptions() {
        return "OPTIONS rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n" +
                addHeaders(authorization);
    }

    private String sendRecord() {
        return "RECORD rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n" +
                "Range: npt=0.000-\r\n" +
                addHeaders(authorization);
    }

    private String sendTearDown() {
        return "TEARDOWN rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n" +
                addHeaders(authorization);
    }

    private String addHeaders(String authorization) {
        return "CSeq: " + (++mCSeq) + "\r\n" +
                "Content-Length: 0\r\n" +
                "Session: " + sessionId + "\r\n" +
                // For some reason you may have to remove last "\r\n" in the next line to make the RTSP client work with your wowza server :/
                (authorization != null ? "Authorization: " + authorization + "\r\n" : "") + "\r\n";
    }

    private String getResponse() {
        try {
            String response = "";
            String line;
            while ((line = reader.readLine()) != null) {
                response += line + "\n";
                //end of response
                if(line.length() < 3)   break;
            }
            Log.i(TAG, response);
            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}

