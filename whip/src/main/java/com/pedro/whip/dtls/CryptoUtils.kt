/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.whip.dtls

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v1CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Created by pedro on 15/7/25.
 */
object CryptoUtils {

  fun generateCert(
    cn: String, secureRandom: SecureRandom
  ): DtlsCertificate {
    val crypto = BcTlsCrypto(secureRandom)
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)

    val keyPair = generator.generateKeyPair()
    val private = keyPair.private
    val key = PrivateKeyFactory.createKey(private.encoded)
    val dirName = "CN=$cn"
    val x509Certificate = generateCertificate(
      dirName, keyPair, 1, "SHA256withRSA"
    )
    val chain = arrayOfNulls<Certificate>(1)
    val byteArrayInputStream = ByteArrayInputStream(x509Certificate.encoded)
    val cf = CertificateFactory.getInstance("X.509")
    while (byteArrayInputStream.available() > 0) {
      chain[0] = cf.generateCertificate(byteArrayInputStream)
    }
    val certificate = crypto.createCertificate(chain[0]!!.encoded)

    val messageDigest = MessageDigest.getInstance("SHA-256")
    val fingerprintBytes = messageDigest.digest(certificate.encoded)

    val fingerprintHex = fingerprintBytes.toHexString()
    val fingerprint = fingerprintHex.replace("(.{2})", "$1:").replace(":$", "")

    return DtlsCertificate(key, certificate, crypto, fingerprint)
  }

  @Throws(CertificateException::class)
  private fun generateCertificate(
    dirName: String?, pair: KeyPair,
    days: Int, algorithm: String?
  ): X509Certificate {
    try {
      val sigAlgId: AlgorithmIdentifier? =
        DefaultSignatureAlgorithmIdentifierFinder().find(algorithm)
      val digAlgId: AlgorithmIdentifier? = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
      val privateKeyAsymKeyParam = PrivateKeyFactory.createKey(pair.private.encoded)
      val subPubKeyInfo = SubjectPublicKeyInfo.getInstance(pair.public.encoded)
      val sigGen: ContentSigner? =
        BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(privateKeyAsymKeyParam)
      val name = X500Name(dirName)
      val from = Date()
      val to = Date(from.time + days * 86400000L)
      val sn = BigInteger(64, SecureRandom())

      val v1CertGen = X509v1CertificateBuilder(name, sn, from, to, name, subPubKeyInfo)
      val certificateHolder: X509CertificateHolder? = v1CertGen.build(sigGen)
      return JcaX509CertificateConverter().getCertificate(certificateHolder)
    } catch (e: Exception) {
      throw e
    }
  }
}