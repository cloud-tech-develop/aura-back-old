package com.cloud_technological.aura_pos.services.implementations;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
public class R2StorageService {

    private static final Set<String> TIPOS_PERMITIDOS = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/avif"
    );
    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB

    @Autowired(required = false)
    @Nullable
    private S3Client s3Client;

    @Value("${r2.bucket}")
    private String bucket;

    @Value("${r2.public-url}")
    private String publicUrl;

    public String subirImagen(MultipartFile file, String carpeta) {
        if (s3Client == null)
            throw new GlobalException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El almacenamiento de imágenes no está configurado en este entorno.");
        validar(file);

        String ext      = obtenerExtension(file.getOriginalFilename(), file.getContentType());
        String key      = carpeta + "/" + UUID.randomUUID() + "." + ext;

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );
        } catch (Exception e) {
            log.error("Error subiendo archivo a R2: {}", e.getMessage(), e);
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo subir la imagen. Intenta de nuevo.");
        }

        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        String url = base + "/" + key;
        log.info("Imagen subida: {}", url);
        return url;
    }

    private void validar(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El archivo no puede estar vacío");
        if (!TIPOS_PERMITIDOS.contains(file.getContentType()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Tipo de archivo no permitido. Solo se aceptan: JPG, PNG, WebP, GIF, AVIF");
        if (file.getSize() > MAX_BYTES)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El archivo supera el tamaño máximo permitido de 10 MB");
    }

    private String obtenerExtension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            if (!ext.isBlank()) return ext;
        }
        return switch (contentType != null ? contentType : "") {
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            case "image/gif"  -> "gif";
            case "image/avif" -> "avif";
            default           -> "jpg";
        };
    }
}
