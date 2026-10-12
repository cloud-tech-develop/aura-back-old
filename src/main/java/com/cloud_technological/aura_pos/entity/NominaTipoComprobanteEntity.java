package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Tipo de comprobante de nómina: define qué liquida un documento (nómina periódica,
 * prima, cesantías, liquidación definitiva…) y su numeración. Los tipos de vinculación
 * admitidos viven en {@code nomina_tipo_comprobante_vinculacion}.
 */
@Entity
@Table(name = "nomina_tipo_comprobante")
@Getter
@Setter
public class NominaTipoComprobanteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Ver {@code ClaseComprobanteNomina}. */
    @Column(nullable = false, length = 30)
    private String clase;

    /** Prefijo del número del documento (NOM-00001). */
    @Column(nullable = false, length = 10)
    private String prefijo;

    /** MENSUAL | QUINCENAL | SEMANAL; solo los periódicos. */
    @Column(length = 15)
    private String frecuencia;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private Boolean activo = Boolean.TRUE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
