package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
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
 * Desglose de la liquidación, una fila por concepto (V105).
 *
 * <p><b>Esto es núcleo, no capa de proyectos.</b> Lo necesita toda empresa:
 * <ul>
 *   <li>Sin él no hay desprendible que mostrarle al empleado.</li>
 *   <li>Sin él no hay cómo responder un reclamo — "el sistema dice 1.234.567"
 *       no es una respuesta.</li>
 *   <li>Sin él <b>no hay nómina electrónica</b>: la DIAN exige cada devengado y
 *       deducción en su etiqueta específica, no un total.</li>
 * </ul>
 *
 * <p>Las dimensiones de proyecto/frente llegan en la Fase 4 (V115) como
 * columnas nullable. Una empresa sin proyectos usa esta tabla igual.
 */
@Getter
@Setter
@Entity
@Table(name = "nomina_detalle")
public class NominaDetalleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nomina_id", nullable = false)
    private NominaEntity nomina;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concepto_id", nullable = false)
    private ConceptoNominaEntity concepto;

    /** Si la línea vino de una novedad, cuál. Rastro para auditoría. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "novedad_id")
    private NominaNovedadEntity novedad;

    /** Horas, días, unidades. */
    @Column(name = "cantidad", precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "base", nullable = false, precision = 15, scale = 2)
    private BigDecimal base = BigDecimal.ZERO;

    @Column(name = "porcentaje", precision = 7, scale = 4)
    private BigDecimal porcentaje;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "valor_empleado", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorEmpleado = BigDecimal.ZERO;

    @Column(name = "valor_empleador", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorEmpleador = BigDecimal.ZERO;

    /**
     * Desglose paso a paso del cálculo.
     *
     * <p><b>Snapshot: no se recalcula.</b> Si mañana cambia la tarifa de salud,
     * la nómina de marzo debe seguir explicándose con la tarifa de marzo. Mismo
     * criterio que {@code pila_cotizante.cod_eps} y el XML de nómina electrónica:
     * los documentos generados guardan literales; los maestros guardan FK.
     *
     * <p>Formato: {@code [{"paso":"Base","valor":1300000}, {"paso":"Tarifa","valor":"4%"}, ...]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "traza", columnDefinition = "jsonb")
    private String traza;

    // ── Dimensiones de proyecto (V115) — CAPA OPCIONAL ──────────────────────
    //
    // Nullable a propósito: muchas empresas no manejan proyectos ni frentes.
    // Para ellas quedan en null, `porcentajeDistrib` en 100, y el resultado es
    // IDÉNTICO a no tener esta fase. Ese es el caso mayoritario, no el borde.
    //
    // Columnas explícitas, no tabla genérica de dimensiones: sigue el criterio
    // de la V92 ("sería sobre-ingeniería a este tamaño").

    @Column(name = "proyecto_id")
    private Long proyectoId;

    @Column(name = "frente_id")
    private Long frenteId;

    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    /**
     * Porcentaje del concepto imputado a esta dimensión.
     *
     * <p>100 = sin distribución. Deja auditable el reparto: si un empleado
     * trabajó 10 días en el frente A y 10 en el B, cada concepto genera dos
     * filas al 50%.
     */
    @Column(name = "porcentaje_distrib", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDistrib = new BigDecimal("100");

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
