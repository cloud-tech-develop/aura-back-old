package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
 * Concepto de nómina (V104).
 *
 * <p>Reemplaza a las constantes compiladas de {@code NominaServiceImpl}
 * ({@code PCT_PRIMA = 8.33}, etc.). Un cambio de ley, un devengado propio de un
 * cliente o una bonificación distinta por empresa pasan de ser código nuevo +
 * release + despliegue a ser una fila.
 *
 * <h2>Lo que NO se copió del ERP de referencia</h2>
 * Ese sistema guarda PHP en base64 en una columna {@code formula} y lo ejecuta
 * con {@code eval()}. Es ejecución de código arbitrario para quien pueda editar
 * un concepto, no se puede testear, y hace imposible entender un cálculo leyendo
 * el repo.
 *
 * <p>Aquí {@link #base} es un <b>enum acotado</b>, no una expresión. Cubre los
 * casos reales sin abrir esa puerta. Si algún día hace falta más expresividad:
 * SpEL restringido o un DSL propio. <b>Nunca {@code eval()} de código almacenado.</b>
 *
 * <h2>Vigencia</h2>
 * {@link #vigenteDesde}/{@link #vigenteHasta} es lo que permite cambiar tarifas
 * por ley sin perder la capacidad de reliquidar períodos anteriores con las
 * tarifas de su momento.
 */
@Getter
@Setter
@Entity
@Table(name = "concepto_nomina")
public class ConceptoNominaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL = concepto global del sistema (los de ley). Con valor = personalización de un cliente. */
    @Column(name = "empresa_id")
    private Integer empresaId;

    @Column(name = "codigo", length = 30, nullable = false)
    private String codigo;

    @Column(name = "nombre", length = 150, nullable = false)
    private String nombre;

    @Column(name = "clase", length = 20, nullable = false)
    private String clase;   // DEVENGADO | DEDUCCION | APORTE_EMPLEADOR | PROVISION

    /**
     * ¿Entra en la base de seguridad social?
     *
     * <p>El auxilio de transporte NO. Un bono no salarial NO. Horas extra SÍ.
     * Esta marca es la mitad de la corrección del bug del IBC (Fase 0).
     */
    @Column(name = "constituye_ibc", nullable = false)
    private Boolean constituyeIbc = Boolean.TRUE;

    /** Sobre qué se calcula. Enum acotado — no es una expresión. */
    @Column(name = "base", length = 30, nullable = false)
    private String base;

    @Column(name = "porcentaje", precision = 7, scale = 4)
    private BigDecimal porcentaje;

    @Column(name = "valor_fijo", precision = 15, scale = 2)
    private BigDecimal valorFijo;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    /** NULL = vigente indefinidamente. */
    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    /** Etiqueta que exige la DIAN en el XML de nómina electrónica (Fase 5). */
    @Column(name = "codigo_dian", length = 30)
    private String codigoDian;

    /** Orden de cálculo. Los que dependen de otros deben ir después de sus bases. */
    @Column(name = "orden", nullable = false)
    private Integer orden = 100;

    @Column(name = "activo", nullable = false)
    private Boolean activo = Boolean.TRUE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // ── Constantes del dominio ──────────────────────────────────────────────

    /** Valores válidos de {@link #clase}. Deben coincidir con el CHECK de la tabla. */
    public static final class Clase {
        public static final String DEVENGADO        = "DEVENGADO";
        public static final String DEDUCCION        = "DEDUCCION";
        public static final String APORTE_EMPLEADOR = "APORTE_EMPLEADOR";
        public static final String PROVISION        = "PROVISION";
        private Clase() {}
    }

    /** Valores válidos de {@link #base}. Deben coincidir con el CHECK de la tabla. */
    public static final class Base {
        /** Salario proporcional a días trabajados. */
        public static final String SALARIO = "SALARIO";
        /** Base prestacional: salario + auxilio de transporte (prima, cesantías). */
        public static final String SALARIO_MAS_AUXILIO = "SALARIO_MAS_AUXILIO";
        /** Base de seguridad social. SIN auxilio de transporte. */
        public static final String IBC = "IBC";
        /** Todo lo devengado. */
        public static final String DEVENGADO_TOTAL = "DEVENGADO_TOTAL";
        /** {@link #valorFijo}, sin cálculo. */
        public static final String FIJO = "FIJO";
        /** Lo resuelve el motor o lo captura el usuario (novedades, ARL, retefuente). */
        public static final String MANUAL = "MANUAL";
        private Base() {}
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    @Transient
    public boolean estaVigenteEn(LocalDate fecha) {
        if (!Boolean.TRUE.equals(activo)) return false;
        if (fecha.isBefore(vigenteDesde)) return false;
        return vigenteHasta == null || !fecha.isAfter(vigenteHasta);
    }

    @Transient
    public boolean esGlobal() {
        return empresaId == null;
    }
}
