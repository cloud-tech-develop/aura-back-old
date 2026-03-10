package com.cloud_technological.aura_pos.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AESencryptUtil {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private final SecretKey secretKey;

    /**
     * Carga la clave secreta AES desde el archivo application.properties
     */
    public AESencryptUtil(@Value("${app.aes.key}") String base64Key) {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        this.secretKey = new SecretKeySpec(decodedKey, ALGORITHM);
    }

    /**
     * Metodo de cifrado de datos en string
     *
     * @param data String
     * @return String
     */
    public String encrypt(String data) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            byte[] encryptedIvAndText = ByteBuffer.allocate(iv.length + encryptedBytes.length)
                    .put(iv)
                    .put(encryptedBytes)
                    .array();
            return Base64.getEncoder().encodeToString(encryptedIvAndText);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            return "";
        }
    }

    /**
     * Metodo de descifrado de datos en string
     *
     * @param encryptedData String
     * @return String
     */
    public String decrypt(String encryptedData) {
        try {
            byte[] encryptedIvAndTextBytes = Base64.getDecoder().decode(encryptedData);
            ByteBuffer bb = ByteBuffer.wrap(encryptedIvAndTextBytes);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            bb.get(iv);
            byte[] encryptedBytes = new byte[bb.remaining()];
            bb.get(encryptedBytes);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            return "";
        }
    }

    ////////////////////////////////////////////////////////
    ////////////////// METODOS AUXILIARES //////////////////
    ////////////////////////////////////////////////////////

    /**
     * Genera una clave secreta AES de 256 bits en formato String
     * * Se usa para cambiar la clave del application.properties
     * 
     * @return
     * @throws Exception
     */
    public String generateKeyString() throws Exception {
        SecretKey key = generateKey();
        byte[] encoded = key.getEncoded();
        return Base64.getEncoder().encodeToString(encoded);
    }

    /**
     * Genera una clave secreta AES de 256 bits
     *
     * @return key SecretKey
     * @throws Exception
     */
    public SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(256, new SecureRandom());
        return keyGen.generateKey();
    }
}
