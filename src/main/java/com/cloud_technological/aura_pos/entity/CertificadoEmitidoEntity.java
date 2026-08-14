package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Certificado emitido (V114).
 *
 * <h2>El contenido es un SNAPSHOT, no se regenera</h2>
 * Un certificado tiene valor probatorio. Si el empleado vuelve por el mismo
 * certificado dentro de un año, debe recibir <b>exactamente el mismo
 * documento</b> — aunque los datos hayan cambiado.
 *
 * <p>Mismo criterio que {@code pila_cotizante.cod_eps} y el XML de nómina
 * electrónica: los documentos emitidos guardan literales; los maestros guardan FK.
 */
@Getter
@Setter
@Entity
@Table(name = "certificado_emitido")
public class CertificadoEmitidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id")
    private ContratoLaboralEntity contrato;

    /** A quién se le emite. La identidad vive en tercero (Fase 1). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tercero_id", nullable = false)
    private TerceroEntity tercero;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo;

    /** Para certificados anuales (ingresos y retenciones). */
    @Column(name = "agno")
    private Integer agno;

    /** Para desprendibles. */
    @Column(name = "nomina_id")
    private Long nominaId;

    /** El documento tal como se emitió. NO se regenera. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contenido_json", columnDefinition = "jsonb")
    private String contenidoJson;

    @Column(name = "pdf_ruta", length = 500)
    private String pdfRuta;

    @Column(name = "emitido_por")
    private Long emitidoPor;

    @Column(name = "emitido_at", nullable = false)
    private LocalDateTime emitidoAt;

    @PrePersist
    void onCreate() {
        if (emitidoAt == null) emitidoAt = LocalDateTime.now();
    }

    public static final class Tipo {
        /** Detalle de una nómina. Sale de nomina_detalle + traza. */
        public static final String DESPRENDIBLE = "DESPRENDIBLE";
        /** Formato 220 DIAN. Obligación anual, antes del 31 de marzo. */
        public static final String INGRESOS_RETENCIONES = "INGRESOS_RETENCIONES";
        /** Cargo, salario, fechas. */
        public static final String LABORAL = "LABORAL";
        /** El empleado a veces no quiere que aparezca el salario. */
        public static final String LABORAL_CON_SALARIO = "LABORAL_CON_SALARIO";
        /** Para retiro parcial de cesantías. */
        public static final String CESANTIAS = "CESANTIAS";
        private Tipo() {}
    }
}
