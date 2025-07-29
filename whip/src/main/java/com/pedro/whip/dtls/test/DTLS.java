/*
    A WHIP client for raspberry pi
    Copyright (C) 2021  Tim Panton

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.pedro.whip.dtls.test;


import android.util.Log;

import com.pedro.rtsp.utils.ExtensionsKt;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.tls.DatagramTransport;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;

/**
 *
 * @author thp
 */
public class DTLS {

    DTLSServer bcdtls;
    Properties[] cprops;

    public DTLS() {}

    Properties[] extractCryptoProps() {
        return cprops;
    }

    Properties[] extractCryptoProps(DTLSServer end) {
        Properties[] p = new Properties[2];
        byte empty[] = new byte[0];
        // assume SRTP_AES128_CM_HMAC_SHA1_80
        int keyLength = 16;
        int saltLength = 14;
        int ts = 2 * (keyLength + saltLength);
        byte[] keys = end.getContext().exportKeyingMaterial("EXTRACTOR-dtls_srtp", null, ts);
        byte[] clientKeyParams = new byte[30];
        byte[] serverKeyParams = new byte[30];
        int offs = 0;
        System.arraycopy(keys, offs, clientKeyParams, 0, keyLength);
        offs += keyLength;
        System.arraycopy(keys, offs, serverKeyParams, 0, keyLength);
        offs += keyLength;
        System.arraycopy(keys, offs, clientKeyParams, keyLength, saltLength);
        offs += saltLength;
        System.arraycopy(keys, offs, serverKeyParams, keyLength, saltLength);
        offs += saltLength;


        String client = ExtensionsKt.encodeToString(clientKeyParams);
        String server = ExtensionsKt.encodeToString(serverKeyParams);

        p[0] = new Properties();
        p[0].put("required", "1");
        p[0].put("crypto-suite", "AES_CM_128_HMAC_SHA1_80");
        p[0].put("key-params", "inline:" + client);
        p[1] = new Properties();
        p[1].put("required", "1");
        p[1].put("crypto-suite", "AES_CM_128_HMAC_SHA1_80");
        p[1].put("key-params", "inline:" + server);

        /* required='1' crypto-suite='AES_CM_128_HMAC_SHA1_80' key-params='inline:WVNfX19zZW1jdGwgKCkgewkyMjA7fQp9CnVubGVz' session-params='KDR=1 UNENCRYPTED_SRTCP' tag='1'
         */
        return p;
    }

    public void stop() {
        bcdtls.stop();
    }


    public void start(DatagramTransport dt, String ffp, BcTlsCrypto crypto, AsymmetricKeyParameter key, TlsCertificate cert) {
        try {
            bcdtls = new DTLSServer(key,cert,crypto,dt,ffp) {
                @Override
                public void notifyHandshakeComplete() {
                    Log.e("Pedro", "complete!!!!");
                    cprops = extractCryptoProps(this);
                }

                @Override
                public void onVerified() {
                    Log.e("Pedro", "ready!!!!");
                }
            };

        } catch (Exception x) {
            Log.e("Pedro", "error dtls", x);
        }

    }

    public static String getPrint(TlsCertificate fpc, boolean withColon) throws IOException {
        StringBuilder b = new StringBuilder();
        byte[] enc = fpc.getEncoded();
        SHA256Digest d = new SHA256Digest();
        d.update(enc, 0, enc.length);
        byte[] result = new byte[d.getDigestSize()];
        d.doFinal(result, 0);
        for (byte r : result) {
            String dig = Integer.toHexString((0xff) & r).toUpperCase();
            if (dig.length() == 1) {
                b.append('0');
            }
            b.append(dig);
            if (withColon) {
                b.append(":");
            }
        }
        if (withColon) {
            b.deleteCharAt(b.length() - 1);
        }
        return b.toString();
    }
    public static boolean validate(TlsCertificate fpc) throws IOException, CertificateException {
        boolean ret = false;

        ByteArrayInputStream bis = new ByteArrayInputStream(fpc.getEncoded());

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        while (bis.available() > 0) {
            X509Certificate fiveohnine = (X509Certificate) cf.generateCertificate(bis);
            long now = System.currentTimeMillis();
            fiveohnine.checkValidity();
            ret = true;
        }
        return ret;
    }
}
