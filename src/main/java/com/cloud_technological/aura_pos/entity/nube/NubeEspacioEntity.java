package com.cloud_technological.aura_pos.entity.nube;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Espacio de la empresa en la nube (V236): cuota y aceptación de los términos.
 * Sin fila, la empresa tiene los valores por defecto (500 MB, 5 000 archivos).
 */
@Entity
@Table(name = "nube_espacio")
@Getter
@Setter
@NoArgsConstructor
public class NubeEspacioEntity {

    public static final int CUOTA_MB_POR_DEFECTO = 500;
    public static final int MAX_ARCHIVOS_POR_DEFECTO = 5000;

    @Id
    @Column(name = "empresa_id")
    private Integer empresaId;

    @Column(name = "cuota_mb", nullable = false)
    private Integer cuotaMb = CUOTA_MB_POR_DEFECTO;

    @Column(name = "max_archivos", nullable = false)
    private Integer maxArchivos = MAX_ARCHIVOS_POR_DEFECTO;

    @Column(name = "terminos_version", length = 10)
    private String terminosVersion;

    @Column(name = "terminos_aceptados_por")
    private Integer terminosAceptadosPor;

    @Column(name = "terminos_aceptados_at")
    private LocalDateTime terminosAceptadosAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (cuotaMb == null) cuotaMb = CUOTA_MB_POR_DEFECTO;
        if (maxArchivos == null) maxArchivos = MAX_ARCHIVOS_POR_DEFECTO;
    }

    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now();
    }
}
