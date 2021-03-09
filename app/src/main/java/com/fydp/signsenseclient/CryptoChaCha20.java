package com.fydp.signsenseclient;

import android.util.Log;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.Destroyable;

/**
 *
 * The possible reasons for using ChaCha20-Poly1305 which is a
 * stream cipher based authenticated encryption algorithm
 * 1. If the CPU does not provide dedicated AES instructions,
 *    ChaCha20 is faster than AES
 * 2. ChaCha20 is not vulnerable to cache-collision timing
 *    attacks unlike AES
 * 3. Since the nonce is not required to be random. There is
 *    no overhead for generating cryptographically secured
 *    pseudo random number
 *
 */

public class CryptoChaCha20 {

    private static final String ENCRYPT_ALGO = "ChaCha20/None/NoPadding";

    private static final int KEY_LEN = 256;

    public static final int NONCE_LEN = 12; //bytes

    private static final BigInteger NONCE_MIN_VAL = new BigInteger("100000000000000000000000", 16);
    private static final BigInteger NONCE_MAX_VAL = new BigInteger("ffffffffffffffffffffffff", 16);

    private static BigInteger nonceCounter = NONCE_MIN_VAL;

    private static String keyString = "88ab02664ae3197ec531da8bd7ea0b5a";
    private static SecretKey secretKey = new SecretKeySpec(keyString.getBytes(), "ChaCha20");
    public static byte[] encrypt(byte[] input)
            throws Exception {
        Objects.requireNonNull(input, "Input message cannot be null");

        if (input.length == 0) {
            throw new IllegalArgumentException("Length of message cannot be 0");
        }

        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);
        byte[] nonce = getNonce();
        IvParameterSpec ivParameterSpec = new IvParameterSpec(nonce);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
        byte[] messageCipher = cipher.doFinal(input);
        byte[] cipherText = new byte[messageCipher.length + NONCE_LEN];
        System.arraycopy(nonce, 0, cipherText, 0, NONCE_LEN);
        System.arraycopy(messageCipher, 0, cipherText, NONCE_LEN,
                messageCipher.length);
        //Log.d("Encryption", new String(cipherText, StandardCharsets.UTF_8));
        return cipherText;
    }

    public static byte[] decrypt(byte[] input)
            throws Exception {
        Objects.requireNonNull(input, "Input message cannot be null");

        if (input.length == 0) {
            throw new IllegalArgumentException("Input array cannot be empty");
        }

        byte[] nonce = new byte[NONCE_LEN];
        System.arraycopy(input, 0, nonce, 0, NONCE_LEN);

        byte[] messageCipher = new byte[input.length - NONCE_LEN];
        System.arraycopy(input, NONCE_LEN, messageCipher, 0, input.length - NONCE_LEN);

        IvParameterSpec ivParameterSpec = new IvParameterSpec(nonce);

        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);

        return cipher.doFinal(messageCipher);
    }


    /**
     *
     * This method creates the 96 bit nonce. A 96 bit nonce
     * is required for ChaCha20-Poly1305. The nonce is not
     * a secret. The only requirement being it has to be
     * unique for a given key. The following function implements
     * a 96 bit counter which when invoked always increments
     * the counter by one.
     *
     * @return
     */
    public static byte[] getNonce() {
        if (nonceCounter.compareTo(NONCE_MAX_VAL) == -1) {
            return nonceCounter.add(BigInteger.ONE).toByteArray();
        } else {
            nonceCounter = NONCE_MIN_VAL;
            return NONCE_MIN_VAL.toByteArray();
        }
    }
    /**
     *
     * Strings should not be used to hold the clear text message or the key, as
     * Strings go in the String pool and they will show up in a heap dump. For the
     * same reason, the client calling these encryption or decryption methods
     * should clear all the variables or arrays holding the message or the key
     * after they are no longer needed. Since Java 8 does not provide an easy
     * mechanism to clear the key from {@code SecretKeySpec}, this method uses
     * reflection to clear the key
     *
     * @param key
     *          The secret key used to do the encryption
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws NoSuchFieldException
     * @throws SecurityException
     */
    @SuppressWarnings("unused")
    public static void clearSecret(Destroyable key)
            throws IllegalArgumentException, IllegalAccessException,
            NoSuchFieldException, SecurityException {
        Field keyField = key.getClass().getDeclaredField("key");
        keyField.setAccessible(true);
        byte[] encodedKey = (byte[]) keyField.get(key);
        Arrays.fill(encodedKey, Byte.MIN_VALUE);
    }
}