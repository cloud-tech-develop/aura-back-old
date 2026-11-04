package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

    /**
     * Código visible del concepto (V230). Único por empresa; las versiones de
     * vigencia y la personalización de un global comparten el número.
     * {@link #codigo} queda como clave interna que el usuario no ve.
     */
    @Column(name = "numero", nullable = false)
    private Integer numero;

    /** Qué es el concepto: fila de {@code nomina_concepto_tipo}. */
    @Column(name = "tipo_id")
    private Integer tipoId;

    /** Qué captura la novedad: {@link Entrada}. */
    @Column(name = "entrada", length = 12, nullable = false)
    private String entrada = Entrada.NINGUNA;

    @Column(name = "redondeo_precision", length = 10, nullable = false)
    private String redondeoPrecision = RedondeoPrecision.NINGUNO;

    @Column(name = "redondeo_modo", length = 10, nullable = false)
    private String redondeoModo = RedondeoModo.AUTOMATICO;

    @Column(name = "frecuencia", length = 12, nullable = false)
    private String frecuencia = Frecuencia.CADA_PAGO;

    /** Elemento del catálogo GRUPO_CONCEPTO. */
    @Column(name = "grupo_id")
    private Long grupoId;

    @Column(name = "aplica_art383", nullable = false)
    private Boolean aplicaArt383 = Boolean.FALSE;

    /**
     * Concepto de ley: en la personalización de la empresa no se cambian clase,
     * tipo, número ni cálculo.
     */
    @Column(name = "de_ley", nullable = false)
    private Boolean deLey = Boolean.FALSE;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    /**
     * Expresión del lenguaje de fórmulas de Aura (V233), cuando {@link #base} es
     * FORMULA. Se analiza y evalúa con un intérprete propio: nunca se ejecuta.
     */
    @Column(name = "formula", columnDefinition = "text")
    private String formula;

    /** Suma a la base de prima y cesantías (salario + auxilio + lo marcado). V233. */
    @Column(name = "hace_base_prestacional", nullable = false)
    private Boolean haceBasePrestacional = Boolean.FALSE;

    /** Centro de costo fijo: el concepto no se reparte por los de la vinculación. V233. */
    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Constantes del dominio ──────────────────────────────────────────────

    /** Valores válidos de {@link #clase}. Deben coincidir con el CHECK de la tabla. */
    public static final class Clase {
        public static final String DEVENGADO        = "DEVENGADO";
        public static final String DEDUCCION        = "DEDUCCION";
        public static final String APORTE_EMPLEADOR = "APORTE_EMPLEADOR";
        public static final String PROVISION        = "PROVISION";
        /** Cuenta días (suspensión, licencia no remunerada) pero no mueve plata. */
        public static final String NO_IMPUTABLE     = "NO_IMPUTABLE";
        public static final List<String> TODAS =
                List.of(DEVENGADO, DEDUCCION, APORTE_EMPLEADOR, PROVISION, NO_IMPUTABLE);
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
        /**
         * El valor lo trae una novedad o un concepto propio de la vinculación (V217):
         * el motor no lo calcula en su recorrido de conceptos.
         */
        public static final String NOVEDAD = "NOVEDAD";
        /** El valor sale de {@link #formula} (V233). */
        public static final String FORMULA = "FORMULA";
        private Base() {}
    }

    /** Qué se captura al registrar el concepto como novedad (V230). */
    public static final class Entrada {
        /** Lo calcula el motor. */
        public static final String NINGUNA    = "NINGUNA";
        public static final String VALOR      = "VALOR";
        /** Horas o días. */
        public static final String CANTIDAD   = "CANTIDAD";
        public static final String PORCENTAJE = "PORCENTAJE";
        public static final List<String> TODAS = List.of(NINGUNA, VALOR, CANTIDAD, PORCENTAJE);
        private Entrada() {}
    }

    /** A qué múltiplo se redondea el valor del concepto (V230). */
    public static final class RedondeoPrecision {
        public static final String NINGUNO = "NINGUNO";
        public static final String ENTERO  = "ENTERO";
        public static final String DECENA  = "DECENA";
        public static final String CENTENA = "CENTENA";
        public static final String MIL     = "MIL";
        public static final List<String> TODAS = List.of(NINGUNO, ENTERO, DECENA, CENTENA, MIL);
        private RedondeoPrecision() {}
    }

    /** Hacia dónde se redondea (V230). AUTOMATICO = mitad hacia arriba. */
    public static final class RedondeoModo {
        public static final String AUTOMATICO = "AUTOMATICO";
        public static final String ARRIBA     = "ARRIBA";
        public static final String ABAJO      = "ABAJO";
        public static final List<String> TODOS = List.of(AUTOMATICO, ARRIBA, ABAJO);
        private RedondeoModo() {}
    }

    /** Cada cuánto se liquida el concepto (V230). */
    public static final class Frecuencia {
        public static final String CADA_PAGO   = "CADA_PAGO";
        /** En la última nómina del mes. */
        public static final String MENSUAL     = "MENSUAL";
        public static final String SEMESTRAL   = "SEMESTRAL";
        public static final String ANUAL       = "ANUAL";
        /** Solo en la liquidación definitiva. */
        public static final String LIQUIDACION = "LIQUIDACION";
        public static final List<String> TODAS = List.of(CADA_PAGO, MENSUAL, SEMESTRAL, ANUAL, LIQUIDACION);
        private Frecuencia() {}
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
