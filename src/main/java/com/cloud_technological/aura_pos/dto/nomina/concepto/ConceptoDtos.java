package com.cloud_technological.aura_pos.dto.nomina.concepto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** DTOs del catálogo de conceptos (Fase 3; ampliado en V230). */
public final class ConceptoDtos {

    private ConceptoDtos() {}

    @Getter @Setter
    public static class ConceptoDto {
        private Long id;
        /** NULL = concepto global del sistema (de ley). Solo lectura para el cliente. */
        private Integer empresaId;
        /** Código visible del concepto. */
        private Integer numero;
        /**
         * Clave interna. No se muestra: la usan como valor las pestañas de la
         * vinculación y las cuentas de nómina hasta la fase 2 del plan.
         */
        private String codigo;
        private String nombre;
        private String clase;
        private Integer tipoId;
        private String tipoNombre;
        private String entrada;
        private String redondeoPrecision;
        private String redondeoModo;
        private String frecuencia;
        private Long grupoId;
        private String grupoNombre;
        private Boolean aplicaArt383;
        private Boolean deLey;
        private String descripcion;
        /** Fórmula cuando base = FORMULA (V233). */
        private String formula;
        /** Suma a la base de prima y cesantías (V233). */
        private Boolean haceBasePrestacional;
        /** Centro de costo fijo del concepto (V233). */
        private Long centroCostoId;
        private String centroCostoNombre;
        private Boolean constituyeIbc;
        private String base;
        private BigDecimal porcentaje;
        private BigDecimal valorFijo;
        private LocalDate vigenteDesde;
        private LocalDate vigenteHasta;
        private String codigoDian;
        private Integer orden;
        private Boolean activo;

        /** Derivado: los globales no se pueden editar desde el cliente. */
        private Boolean esGlobal;
        /** Derivado: si la empresa tiene su propia versión de este concepto. */
        private Boolean personalizado;

        /** Filas totales de la búsqueda paginada. */
        private long totalRows;
    }

    /**
     * Alta de un concepto propio de la empresa.
     *
     * <p><b>{@code base} es un enum acotado, no una fórmula.</b> Las fórmulas
     * llegan en la fase 3 del plan, con un evaluador cerrado.
     */
    @Getter @Setter
    public static class CreateConceptoDto {
        /** Código visible. Si llega vacío se asigna el siguiente libre de la empresa. */
        private Integer numero;
        private String nombre;
        /** DEVENGADO | DEDUCCION | APORTE_EMPLEADOR | PROVISION | NO_IMPUTABLE */
        private String clase;
        private Integer tipoId;
        private String entrada;
        private String redondeoPrecision;
        private String redondeoModo;
        private String frecuencia;
        private Long grupoId;
        private Boolean aplicaArt383;
        private String descripcion;
        /** Requerida si base = FORMULA. */
        private String formula;
        private Boolean haceBasePrestacional;
        private Long centroCostoId;
        private Boolean constituyeIbc = Boolean.TRUE;
        /** SALARIO | SALARIO_MAS_AUXILIO | IBC | DEVENGADO_TOTAL | FIJO | MANUAL | NOVEDAD */
        private String base;
        /** Requerido salvo que base sea FIJO, MANUAL o NOVEDAD. */
        private BigDecimal porcentaje;
        /** Requerido si base = FIJO. */
        private BigDecimal valorFijo;
        /**
         * Desde cuándo rige.
         *
         * <p>Cambiar una tarifa NO edita el concepto: crea una versión nueva.
         * El backend rechaza vigencias solapadas.
         */
        private LocalDate vigenteDesde;
        private LocalDate vigenteHasta;
        private String codigoDian;
        private Integer orden = 100;
    }

    /**
     * Edición de un concepto.
     *
     * <p>Si el concepto es global, se crea la versión de la empresa con estos datos.
     * En un concepto de ley solo se aplican los campos descriptivos (nombre,
     * entrada, redondeo, frecuencia, grupo, art. 383, descripción, activo).
     */
    @Getter @Setter
    public static class UpdateConceptoDto {
        private String nombre;
        private String clase;
        private Integer tipoId;
        private String entrada;
        private String redondeoPrecision;
        private String redondeoModo;
        private String frecuencia;
        private Long grupoId;
        private Boolean aplicaArt383;
        private String descripcion;
        /** Requerida si base = FORMULA. */
        private String formula;
        private Boolean haceBasePrestacional;
        private Long centroCostoId;
        private Boolean constituyeIbc;
        private String base;
        private BigDecimal porcentaje;
        private BigDecimal valorFijo;
        private String codigoDian;
        private Integer orden;
        private Boolean activo;
    }

    /**
     * Nueva vigencia de un concepto propio: cambia el cálculo desde una fecha y
     * deja la versión anterior cerrada para reliquidar con la tarifa de su momento.
     */
    @Getter @Setter
    public static class NuevaVigenciaDto {
        private String base;
        /** Requerida si base = FORMULA. */
        private String formula;
        private BigDecimal porcentaje;
        private BigDecimal valorFijo;
        private Boolean constituyeIbc;
        private LocalDate vigenteDesde;
    }

    /** Filtros del buscador avanzado de conceptos. Todos opcionales. */
    @Getter @Setter
    public static class BuscarConceptoParams {
        private String clase;
        private Integer tipoId;
        private Long grupoId;
        private String entrada;
        private String frecuencia;
        /** Fecha de vigencia; por defecto, hoy. */
        private LocalDate fecha;
        /** true = incluye los inactivos (pantalla de administración). */
        private Boolean incluirInactivos;
    }

    /** Fila del catálogo de tipos de concepto. */
    @Getter @Setter
    public static class ConceptoTipoDto {
        private Integer id;
        private String nombre;
        private String clase;
    }

    /** Texto de una fórmula para validar. */
    @Getter @Setter
    public static class FormulaDto {
        private String formula;
        /** Concepto que se edita (para detectar que se referencia a sí mismo). Opcional. */
        private Integer numero;
    }

    /** Resultado de validar una fórmula. */
    @Getter @Setter
    public static class ValidacionFormulaDto {
        private boolean valida;
        private String error;
        /** Carácter (desde 1) donde está el error; 0 si no aplica. */
        private int posicion;
        private List<ReferenciaFormulaDto> conceptos = new ArrayList<>();
        private List<String> variables = new ArrayList<>();
    }

    /** Concepto referenciado en una fórmula: {@code [32]}. */
    @Getter @Setter
    public static class ReferenciaFormulaDto {
        private Integer numero;
        private String nombre;
        private boolean existe;
    }

    /** Probar una fórmula con valores de ejemplo, sin guardar. */
    @Getter @Setter
    public static class ProbarFormulaDto {
        private String formula;
        private Map<String, BigDecimal> variables = new LinkedHashMap<>();
        /** Valor de los conceptos referenciados, por número. */
        private Map<Integer, BigDecimal> conceptos = new LinkedHashMap<>();
    }

    /** Resultado de probar una fórmula: el valor y los datos que usó. */
    @Getter @Setter
    public static class PruebaFormulaDto {
        private BigDecimal valor;
        private String error;
        private List<PasoFormulaDto> pasos = new ArrayList<>();
    }

    @Getter @Setter
    public static class PasoFormulaDto {
        private String nombre;
        private BigDecimal valor;

        public PasoFormulaDto() {}

        public PasoFormulaDto(String nombre, BigDecimal valor) {
            this.nombre = nombre;
            this.valor = valor;
        }
    }

    /** Variable que puede usar una fórmula, con un valor de ejemplo para probar. */
    @Getter @Setter
    public static class VariableFormulaDto {
        private String nombre;
        private String descripcion;
        private BigDecimal ejemplo;
    }

    /** Concepto o dato que alimenta la base de otro concepto ("Afectado por"). */
    @Getter @Setter
    public static class AfectadoPorDto {
        /** Null cuando es una variable de la liquidación, no un concepto. */
        private Integer numero;
        private String nombre;
        private String clase;
        private String motivo;
    }
}
