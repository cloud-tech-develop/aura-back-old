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
 * Carpeta de la nube de una empresa (V236, docs/PLAN_NUBE_DOCUMENTOS.md).
 * La base exige que su padre sea de la misma empresa (llave foránea compuesta).
 */
@Entity
@Table(name = "nube_carpeta")
@Getter
@Setter
@NoArgsConstructor
public class NubeCarpetaEntity {

    public static final String COMPARTIDA = "COMPARTIDA";
    public static final String SOLO_FIRMA = "SOLO_FIRMA";
    public static final int NIVEL_MAXIMO = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Integer empresaId;

    @Column(name = "padre_id")
    private Long padreId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 15)
    private String visibilidad = COMPARTIDA;

    @Column(nullable = false)
    private Short nivel = 1;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private Integer creadoPor;

    /** Firma por la que entró quien la creó; null = usuario de la propia empresa. */
    @Column(name = "creado_por_firma_id", updatable = false)
    private Integer creadoPorFirmaId;

    /** Firma dueña de la carpeta SOLO_FIRMA (V237); null si es COMPARTIDA. Solo esa firma la ve. */
    @Column(name = "privada_firma_id")
    private Integer privadaFirmaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "eliminado_por")
    private Integer eliminadoPor;

    /** Fecha en que la purga la borra definitivamente (30 días en la papelera). */
    @Column(name = "purgar_en")
    private LocalDateTime purgarEn;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (visibilidad == null) visibilidad = COMPARTIDA;
        if (nivel == null) nivel = 1;
    }

    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now();
    }
}
