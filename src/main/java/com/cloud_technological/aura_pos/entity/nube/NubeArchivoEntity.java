package com.cloud_technological.aura_pos.entity.nube;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Archivo de la nube de una empresa (V236). Los bytes están cifrados en el bucket
 * privado; aquí solo la clave del objeto, el IV y la versión de la llave. Nunca
 * se guarda ni se devuelve una URL.
 */
@Entity
@Table(name = "nube_archivo")
@Getter
@Setter
@NoArgsConstructor
public class NubeArchivoEntity {

    /** 1 MB: tope por archivo (la base tiene el mismo CHECK). */
    public static final int MAX_BYTES = 1_048_576;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Integer empresaId;

    @Column(name = "carpeta_id")
    private Long carpetaId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 10)
    private String extension;

    /** Tipo detectado por el contenido, no el que mandó el navegador. */
    @Column(nullable = false, length = 100)
    private String mime;

    @Column(name = "tamano_bytes", nullable = false, updatable = false)
    private Integer tamanoBytes;

    /** SHA-256 del archivo original, en hexadecimal. */
    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "object_key", nullable = false, length = 120, updatable = false)
    private String objectKey;

    @Column(nullable = false, updatable = false)
    private byte[] iv;

    @Column(name = "version_llave", nullable = false, updatable = false)
    private Short versionLlave;

    @Column(name = "subido_por", nullable = false, updatable = false)
    private Integer subidoPor;

    @Column(name = "subido_por_firma_id", updatable = false)
    private Integer subidoPorFirmaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "eliminado_por")
    private Integer eliminadoPor;

    @Column(name = "purgar_en")
    private LocalDateTime purgarEn;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now();
    }
}
