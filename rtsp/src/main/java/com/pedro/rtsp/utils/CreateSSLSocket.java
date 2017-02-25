package com.pedro.rtsp.utils;

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
 * this class is used for secure transport, to use replace socket on RtspClient with this and you
 * will have a secure stream under ssl/tls.
 */

public class CreateSSLSocket {

  /**
   *
   * @param inputStream data of your .jks file
   * @param passPhrase passphrase of your .jks
   * @return
   */
  public static KeyStore createKeyStore(InputStream inputStream, String passPhrase) {
    try {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(inputStream, passPhrase.toCharArray());
      return keyStore;
    } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   *
   * @param keyStore created with createKeyStore()
   * @param host variable from RtspClient
   * @param port variable from RtspClient
   * @return
   */
  public static Socket createSSlSocket(KeyStore keyStore, String host, int port) {
    try {
      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(keyStore);
      KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(),
          null);
      return sslContext.getSocketFactory().createSocket(host, port);
    } catch (KeyStoreException e) {
      e.printStackTrace();
      return null;
    } catch (NoSuchAlgorithmException | IOException e) {
      e.printStackTrace();
      return null;
    } catch (KeyManagementException e) {
      e.printStackTrace();
    }
    return null;
  }
}
