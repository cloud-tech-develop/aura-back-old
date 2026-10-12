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
 * Tipo de vinculación: agrupa empleados (planta, temporal, aprendiz, por días…) y
 * propone los valores por defecto de su vinculación.
 */
@Entity
@Table(name = "nomina_tipo_vinculacion")
@Getter
@Setter
public class NominaTipoVinculacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** INDEFINIDO | FIJO | OBRA_LABOR | PRESTACION_SERVICIOS | APRENDIZAJE (null = libre). */
    @Column(name = "tipo_contrato", length = 30)
    private String tipoContrato;

    /** MENSUAL | INTEGRAL | JORNAL | HORA. */
    @Column(name = "tipo_remuneracion", nullable = false, length = 20)
    private String tipoRemuneracion;

    /** MENSUAL | QUINCENAL | SEMANAL. */
    @Column(name = "frecuencia_pago", nullable = false, length = 15)
    private String frecuenciaPago;

    /** El contrato debe tener fecha de terminación (fijo, obra, aprendizaje). */
    @Column(name = "exige_fecha_fin", nullable = false)
    private Boolean exigeFechaFin;

    /** El empleado solo puede tener una vinculación activa de este tipo. */
    @Column(name = "unica_por_empleado", nullable = false)
    private Boolean unicaPorEmpleado;

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
