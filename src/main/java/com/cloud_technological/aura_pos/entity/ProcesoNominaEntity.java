package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * Proceso asíncrono de nómina (V117).
 *
 * <p>{@code liquidarPeriodoCompleto()} corría síncrono dentro del request: con
 * 30 empleados va bien, con 500 y provisiones da timeout. Lo mismo aplica a los
 * envíos DIAN y a la generación de PILA.
 *
 * <p>Equivale a {@code nom_procesos_logs} + {@code ComProgressEvent} del ERP de
 * referencia.
 */
@Getter
@Setter
@Entity
@Table(name = "proceso_nomina")
public class ProcesoNominaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "tipo", length = 40, nullable = false)
    private String tipo;

    /** periodo_id, nomina_id, etc. según el tipo. */
    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = Estado.PENDIENTE;

    @Column(name = "progreso", nullable = false)
    private Integer progreso = 0;

    @Column(name = "mensaje", length = 300)
    private String mensaje;

    @Column(name = "total_items", nullable = false)
    private Integer totalItems = 0;

    @Column(name = "items_ok", nullable = false)
    private Integer itemsOk = 0;

    @Column(name = "items_error", nullable = false)
    private Integer itemsError = 0;

    /**
     * Errores por item, sin abortar el lote.
     *
     * <p>En un lote de 500 empleados, que 3 fallen no debe botar los 497
     * buenos. El usuario decide qué hacer con esos 3.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errores", columnDefinition = "jsonb")
    private String errores;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "iniciado_at", nullable = false)
    private LocalDateTime iniciadoAt;

    @Column(name = "finalizado_at")
    private LocalDateTime finalizadoAt;

    @Column(name = "reversado_por")
    private Long reversadoPor;

    @Column(name = "reversado_at")
    private LocalDateTime reversadoAt;

    @PrePersist
    void onCreate() {
        if (iniciadoAt == null) iniciadoAt = LocalDateTime.now();
    }

    public static final class Estado {
        public static final String PENDIENTE  = "PENDIENTE";
        public static final String EN_PROCESO = "EN_PROCESO";
        public static final String COMPLETADO = "COMPLETADO";
        /** El lote terminó, pero algunos items fallaron. Existe a propósito. */
        public static final String COMPLETADO_CON_ERRORES = "COMPLETADO_CON_ERRORES";
        public static final String FALLIDO   = "FALLIDO";
        public static final String REVERSADO = "REVERSADO";
        private Estado() {}
    }

    public static final class Tipo {
        public static final String LIQUIDACION_PERIODO       = "LIQUIDACION_PERIODO";
        public static final String LIQUIDACION_PRESTACIONES   = "LIQUIDACION_PRESTACIONES";
        public static final String NOMINA_ELECTRONICA         = "NOMINA_ELECTRONICA";
        public static final String NOMINA_ELECTRONICA_AJUSTE  = "NOMINA_ELECTRONICA_AJUSTE";
        public static final String PILA_GENERACION            = "PILA_GENERACION";
        public static final String CONTABILIZACION            = "CONTABILIZACION";
        public static final String IMPORTACION                = "IMPORTACION";
        private Tipo() {}
    }

    @Transient
    public boolean estaActivo() {
        return Estado.PENDIENTE.equals(estado) || Estado.EN_PROCESO.equals(estado);
    }
}
