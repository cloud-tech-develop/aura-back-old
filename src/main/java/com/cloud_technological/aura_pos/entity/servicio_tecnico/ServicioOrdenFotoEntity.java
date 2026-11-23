package com.cloud_technological.aura_pos.entity.servicio_tecnico;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Foto de un equipo, cifrada en la nube privada; aquí solo la clave del objeto y el IV (V249). */
@Entity
@Table(name = "servicio_orden_foto")
@Getter
@Setter
@NoArgsConstructor
public class ServicioOrdenFotoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public static final int MAX_BYTES = 3 * 1024 * 1024;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Integer empresaId;

    @Column(name = "orden_id", nullable = false, updatable = false)
    private Long ordenId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 50)
    private String mime;

    @Column(name = "tamano_bytes", nullable = false, updatable = false)
    private Integer tamanoBytes;

    @Column(name = "object_key", nullable = false, length = 120, updatable = false)
    private String objectKey;

    @Column(nullable = false, updatable = false)
    private byte[] iv;

    @Column(name = "version_llave", nullable = false, updatable = false)
    private Short versionLlave;

    @Column(nullable = false, length = 15)
    private String momento = "RECIBIDO";

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
