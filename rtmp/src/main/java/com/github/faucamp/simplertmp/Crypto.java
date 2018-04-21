package com.github.faucamp.simplertmp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Some helper utilities for SHA256, mostly (used during handshake)
 * This is separated in order to be more easily replaced on platforms that
 * do not have the javax.crypto.* and/or java.security.* packages
 * 
 * This implementation is directly inspired by the RTMPHandshake class of the
 * Red5  Open Source Flash Server project
 * 
 * @author francois
 */
public class Crypto {

    private final static Logger logger = LoggerFactory.getLogger(Crypto.class);

    private Mac hmacSHA256;

    public Crypto() {
        try {
            hmacSHA256 = Mac.getInstance("HmacSHA256");
        } catch (SecurityException e) {
            logger.error("Security exception when getting HMAC", e);
        } catch (NoSuchAlgorithmException e) {
            logger.error("HMAC SHA256 does not exist");
        }
    }

    /**
     * Calculates an HMAC SHA256 hash using a default key length.
     *
     *
     * @param input
     * @param key
     * @return hmac hashed bytes
     */
    public byte[] calculateHmacSHA256(byte[] input, byte[] key) {
        byte[] output = null;
        try {
            hmacSHA256.init(new SecretKeySpec(key, "HmacSHA256"));
            output = hmacSHA256.doFinal(input);
        } catch (InvalidKeyException e) {
            logger.error("Invalid key", e);
        }
        return output;
    }

    /**
     * Calculates an HMAC SHA256 hash using a set key length.
     *
     * @param input
     * @param key
     * @param length
     * @return hmac hashed bytes
     */
    public byte[] calculateHmacSHA256(byte[] input, byte[] key, int length) {
        byte[] output = null;
        try {
            hmacSHA256.init(new SecretKeySpec(key, 0, length, "HmacSHA256"));
            output = hmacSHA256.doFinal(input);
        } catch (InvalidKeyException e) {
            logger.error("Invalid key", e);
        }
        return output;
    }
}
