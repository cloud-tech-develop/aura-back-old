package com.cloud_technological.aura_pos.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;

/**
 * Fallo cerrado de las llaves (docs/PLAN_SEGURIDAD.md, C-01 y §5.2).
 *
 * <p>Las llaves {@code app.jwt.secret} y {@code app.aes.key} estuvieron
 * escritas en {@code application.properties} y siguen en el historial de git:
 * con la JWT cualquiera puede firmarse un token de SUPER_ADMIN de cualquier
 * empresa. Aquí solo se guarda su huella SHA-256 (nunca el valor) y el backend
 * se niega a arrancar si alguna se vuelve a usar, o si la JWT es demasiado
 * corta para HS256.
 */
@Component
public class LlavesGuard {

    /** Huellas SHA-256 de las llaves que estuvieron publicadas en git. Quemadas para siempre. */
    static final Set<String> QUEMADAS = Set.of(
            "dfa14fe5fa166d5c7d7e1eac503b70be1bf8650972cc9da272919c7bfb7e45ad", // app.jwt.secret (hasta 2026-08-13)
            "8671d38857d11d9c991fbe93b1b36eaa5aa72149cd52b61972bcf30687d71a32"); // app.aes.key (hasta 2026-08-13)

    /** HS256 exige al menos 256 bits. */
    static final int MIN_BYTES_JWT = 32;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.aes.key}")
    private String aesKey;

    @PostConstruct
    void validar() {
        validar(jwtSecret, aesKey);
    }

    static void validar(String jwtSecret, String aesKey) {
        if (jwtSecret == null || jwtSecret.isBlank())
            throw new IllegalStateException("JWT_SECRET vacío: defina la variable de entorno (openssl rand -base64 64)");
        if (QUEMADAS.contains(huella(jwtSecret)))
            throw new IllegalStateException("JWT_SECRET es la llave que estuvo publicada en git: está quemada. "
                    + "Genere una nueva (openssl rand -base64 64), póngala en el entorno y reinicie. "
                    + "Todos los usuarios tendrán que volver a iniciar sesión.");
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(jwtSecret.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET no es base64 válido (openssl rand -base64 64)");
        }
        if (bytes.length < MIN_BYTES_JWT)
            throw new IllegalStateException("JWT_SECRET es muy corta (" + bytes.length + " bytes): HS256 exige al menos "
                    + MIN_BYTES_JWT + ". Use openssl rand -base64 64");
        if (aesKey != null && !aesKey.isBlank() && QUEMADAS.contains(huella(aesKey)))
            throw new IllegalStateException("AES_KEY es la llave que estuvo publicada en git: está quemada. "
                    + "Genere una nueva (openssl rand -base64 32) y reinicie.");
    }

    static String huella(String valor) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(valor.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
