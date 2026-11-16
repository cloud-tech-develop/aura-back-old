package com.cloud_technological.aura_pos.services.nube;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifrado de los archivos de la nube (docs/PLAN_NUBE_DOCUMENTOS.md §3.6).
 *
 * <p>AES-256-GCM con una llave por empresa derivada de la llave maestra con
 * HKDF-SHA256. Así un archivo de la empresa A no se puede descifrar con la llave
 * de la empresa B, y si se filtran las credenciales del bucket lo que se descarga
 * es ilegible.
 *
 * <p>La clave del objeto va como dato autenticado (AAD): si alguien cambia los
 * bytes o mueve un objeto cifrado a otra clave, descifrar falla en vez de
 * devolver el archivo equivocado.
 *
 * <p>Clase sin estado de Spring para poder probarla sola.
 */
public final class NubeCifrado {

    /** Versión de la llave maestra actual; sube al rotarla. */
    public static final short VERSION_LLAVE_ACTUAL = 1;

    static final int BYTES_LLAVE = 32;
    static final int BYTES_IV = 12;
    private static final int BITS_TAG = 128;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final byte[] llaveMaestra;

    /**
     * @param llaveMaestraBase64 32 bytes en base64 ({@code openssl rand -base64 32})
     * @throws IllegalStateException si falta o no tiene 32 bytes
     */
    public NubeCifrado(String llaveMaestraBase64) {
        if (llaveMaestraBase64 == null || llaveMaestraBase64.isBlank())
            throw new IllegalStateException("NUBE_MASTER_KEY vacía");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(llaveMaestraBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("NUBE_MASTER_KEY no es base64 válido (openssl rand -base64 32)");
        }
        if (bytes.length != BYTES_LLAVE)
            throw new IllegalStateException("NUBE_MASTER_KEY debe tener 32 bytes y tiene " + bytes.length
                    + " (openssl rand -base64 32)");
        this.llaveMaestra = bytes;
    }

    /** Resultado de cifrar: los bytes a subir y el IV a guardar en la base. */
    public record Cifrado(byte[] datos, byte[] iv) {
    }

    /**
     * Cifra el archivo de una empresa.
     *
     * @param empresaId empresa dueña del archivo (de la que se deriva la llave)
     * @param objectKey clave del objeto en el bucket, ligada al cifrado como AAD
     * @param plano     contenido original
     */
    public Cifrado cifrar(Integer empresaId, String objectKey, byte[] plano) {
        byte[] iv = new byte[BYTES_IV];
        ALEATORIO.nextBytes(iv);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, llaveEmpresa(empresaId, VERSION_LLAVE_ACTUAL),
                    new GCMParameterSpec(BITS_TAG, iv));
            c.updateAAD(objectKey.getBytes(StandardCharsets.UTF_8));
            return new Cifrado(c.doFinal(plano), iv);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el archivo", e);
        }
    }

    /**
     * Descifra y verifica el archivo.
     *
     * @throws IllegalStateException si los bytes fueron alterados, la clave del
     *                               objeto no corresponde o la llave es otra
     */
    public byte[] descifrar(Integer empresaId, String objectKey, byte[] cifrado, byte[] iv, short versionLlave) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, llaveEmpresa(empresaId, versionLlave), new GCMParameterSpec(BITS_TAG, iv));
            c.updateAAD(objectKey.getBytes(StandardCharsets.UTF_8));
            return c.doFinal(cifrado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("El archivo no pasó la verificación de integridad", e);
        }
    }

    /** HKDF-SHA256 (RFC 5869): una llave de 32 bytes por empresa y versión. */
    SecretKeySpec llaveEmpresa(Integer empresaId, short version) throws GeneralSecurityException {
        if (version != VERSION_LLAVE_ACTUAL)
            throw new GeneralSecurityException("Versión de llave desconocida: " + version);
        byte[] salt = ("aura-nube-v" + version).getBytes(StandardCharsets.UTF_8);
        byte[] info = ByteBuffer.allocate(4 + 8).put("emp:".getBytes(StandardCharsets.US_ASCII))
                .putLong(empresaId.longValue()).array();

        Mac extraer = Mac.getInstance("HmacSHA256");
        extraer.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = extraer.doFinal(llaveMaestra);

        Mac expandir = Mac.getInstance("HmacSHA256");
        expandir.init(new SecretKeySpec(prk, "HmacSHA256"));
        expandir.update(info);
        expandir.update((byte) 1);
        byte[] okm = expandir.doFinal();
        Arrays.fill(prk, (byte) 0);
        return new SecretKeySpec(okm, 0, BYTES_LLAVE, "AES");
    }
}
