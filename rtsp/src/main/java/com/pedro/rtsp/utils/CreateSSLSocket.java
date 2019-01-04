package com.pedro.rtsp.utils;

import android.util.Log;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * Created by pedro on 25/02/17.
 *
 * this class is used for secure transport, to use replace socket on RtmpConnection with this and
 * you will have a secure stream under ssl/tls.
 */

public class CreateSSLSocket {

  /**
   * @param host variable from RtspConnection
   * @param port variable from RtspConnection
   */
  public static Socket createSSlSocket(String host, int port) {
    try {
      TLSSocketFactory socketFactory = new TLSSocketFactory();
      return socketFactory.createSocket(host, port);
    } catch (NoSuchAlgorithmException | KeyManagementException | IOException e) {
      Log.e("CreateSSLSocket", "Error", e);
      return null;
    }
  }
}
