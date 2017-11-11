package net.ossrs.rtmp;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Created by pedro on 25/02/17.
 * this class is used for secure transport, to use replace socket on RtmpConnection with this and
 * you will have a secure stream under ssl/tls.
 */

public class CreateSSLSocket {

  /**
   *
   * @param host variable from RtmpConnection
   * @param port variable from RtmpConnection
   * @return
   */
  public static Socket createSSlSocket(String host, int port) {
    try {
      TLSSocketFactory socketFactory = new TLSSocketFactory();
      return socketFactory.createSocket(host, port);
    } catch (NoSuchAlgorithmException | KeyManagementException | IOException e) {
      e.printStackTrace();
      return null;
    }
  }
}
