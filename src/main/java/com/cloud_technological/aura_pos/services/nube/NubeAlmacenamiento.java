package com.cloud_technological.aura_pos.services.nube;

import java.net.URI;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.utils.GlobalException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Almacenamiento privado de la nube de documentos (docs/PLAN_NUBE_DOCUMENTOS.md, F0).
 *
 * <p>Va aparte de {@code R2StorageService} a propósito: usa otro bucket (privado,
 * sin URL pública) y otro token de R2 que solo da acceso a ese bucket. El cliente
 * S3 no se publica como bean para que nadie lo inyecte por error en otro lado.
 *
 * <p>Todo lo que entra se cifra (ver {@link NubeCifrado}) y nunca se devuelve una
 * URL: el contenido sale solo por el backend, después de revisar permisos.
 *
 * <p>Falla cerrado: sin credenciales o sin llave maestra la nube responde 503; con
 * una llave maestra mal formada el backend no arranca.
 */
@Slf4j
@Component
public class NubeAlmacenamiento {

    private static final String PREFIJO = "nube";

    @Value("${r2.endpoint}")
    private String endpoint;

    @Value("${nube.r2.bucket}")
    private String bucket;

    @Value("${nube.r2.access-key-id:}")
    private String accessKeyId;

    @Value("${nube.r2.secret-access-key:}")
    private String secretAccessKey;

    @Value("${nube.master-key:}")
    private String llaveMaestra;

    private S3Client s3;
    private NubeCifrado cifrado;

    /** Lo que hay que guardar en nube_archivo para poder leer el objeto después. */
    public record ObjetoGuardado(String objectKey, byte[] iv, short versionLlave) {
    }

    @PostConstruct
    void iniciar() {
        if (llaveMaestra != null && !llaveMaestra.isBlank())
            cifrado = new NubeCifrado(llaveMaestra); // mal formada = no arranca
        if (accessKeyId == null || accessKeyId.isBlank() || secretAccessKey == null || secretAccessKey.isBlank()) {
            log.warn("Nube de documentos deshabilitada: faltan R2_NUBE_ACCESS_KEY_ID / R2_NUBE_SECRET_ACCESS_KEY");
            return;
        }
        if (cifrado == null) {
            log.warn("Nube de documentos deshabilitada: falta NUBE_MASTER_KEY");
            return;
        }
        s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        // Prueba con lo que de verdad usa la nube (un token "Object Read & Write" no
        // siempre puede consultar el bucket en sí).
        String sonda = PREFIJO + "/_salud/" + UUID.randomUUID();
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(sonda).build(), RequestBody.fromBytes(new byte[] { 1 }));
            s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(sonda).build());
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(sonda).build());
            log.info("Nube de documentos habilitada (bucket {})", bucket);
        } catch (RuntimeException e) {
            // No tumba el backend: el resto de Aura sigue; la nube responderá error al usarla.
            log.error("Nube de documentos: el token no puede escribir/leer/borrar en el bucket {}: {}", bucket,
                    e.getMessage());
        }
    }

    @PreDestroy
    void cerrar() {
        if (s3 != null) s3.close();
    }

    /** ¿Están las credenciales y la llave? */
    public boolean disponible() {
        return s3 != null && cifrado != null;
    }

    /**
     * Cifra y sube el contenido de un archivo de la empresa.
     *
     * @param empresaId empresa del token (nunca de un parámetro)
     * @param contenido bytes originales, ya validados
     * @return clave del objeto, IV y versión de llave para guardar en la base
     */
    public ObjetoGuardado guardar(Integer empresaId, byte[] contenido) {
        exigirDisponible();
        // La clave no lleva el nombre del archivo: no filtra información ni permite rutas.
        String objectKey = PREFIJO + "/" + empresaId + "/" + UUID.randomUUID();
        NubeCifrado.Cifrado c = cifrado.cifrar(empresaId, objectKey, contenido);
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType("application/octet-stream")
                    .build(), RequestBody.fromBytes(c.datos()));
        } catch (RuntimeException e) {
            log.error("Error subiendo a la nube (empresa {}): {}", empresaId, e.getMessage());
            throw new GlobalException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo guardar el archivo. Intenta de nuevo en unos minutos.");
        }
        return new ObjetoGuardado(objectKey, c.iv(), NubeCifrado.VERSION_LLAVE_ACTUAL);
    }

    /**
     * Descarga y descifra un objeto. Quien llama ya revisó que el archivo es de la
     * empresa del token; aquí se revisa otra vez que la clave sea de esa empresa.
     */
    public byte[] leer(Integer empresaId, String objectKey, byte[] iv, short versionLlave) {
        exigirDisponible();
        exigirDeLaEmpresa(empresaId, objectKey);
        byte[] cifradoBytes;
        try {
            cifradoBytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(objectKey).build())
                    .asByteArray();
        } catch (RuntimeException e) {
            log.error("Error leyendo de la nube {}: {}", objectKey, e.getMessage());
            throw new GlobalException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo abrir el archivo. Intenta de nuevo en unos minutos.");
        }
        try {
            return cifrado.descifrar(empresaId, objectKey, cifradoBytes, iv, versionLlave);
        } catch (IllegalStateException e) {
            log.error("SEGURIDAD: el objeto {} no pasó la verificación de integridad", objectKey);
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El archivo está dañado o fue alterado y no se puede abrir.");
        }
    }

    /** Borra el objeto del bucket (purga definitiva desde la papelera). */
    public void eliminar(Integer empresaId, String objectKey) {
        exigirDisponible();
        exigirDeLaEmpresa(empresaId, objectKey);
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }

    private void exigirDisponible() {
        if (!disponible())
            throw new GlobalException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La nube de documentos no está configurada en este entorno.");
    }

    private static void exigirDeLaEmpresa(Integer empresaId, String objectKey) {
        if (objectKey == null || !objectKey.startsWith(PREFIJO + "/" + empresaId + "/"))
            throw new GlobalException(HttpStatus.NOT_FOUND, "Archivo no encontrado");
    }
}
