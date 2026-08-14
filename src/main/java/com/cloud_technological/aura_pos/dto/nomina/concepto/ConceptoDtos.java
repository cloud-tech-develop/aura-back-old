package com.cloud_technological.aura_pos.dto.nomina.concepto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** DTOs del catálogo de conceptos (Fase 3). */
public final class ConceptoDtos {

    private ConceptoDtos() {}

    @Getter @Setter
    public static class ConceptoDto {
        private Long id;
        /** NULL = concepto global del sistema (de ley). Solo lectura para el cliente. */
        private Integer empresaId;
        private String codigo;
        private String nombre;
        private String clase;
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
        /** Derivado: si la empresa tiene su propia versión de este código. */
        private Boolean personalizado;
    }

    /**
     * Alta de un concepto propio de la empresa.
     *
     * <p><b>{@code base} es un enum acotado, no una fórmula.</b> Ahí está la
     * diferencia con el ERP de referencia, que guarda código y lo evalúa.
     */
    @Getter @Setter
    public static class CreateConceptoDto {
        private String codigo;
        private String nombre;
        /** DEVENGADO | DEDUCCION | APORTE_EMPLEADOR | PROVISION */
        private String clase;
        private Boolean constituyeIbc = Boolean.TRUE;
        /** SALARIO | SALARIO_MAS_AUXILIO | IBC | DEVENGADO_TOTAL | FIJO | MANUAL */
        private String base;
        /** Requerido salvo que base sea FIJO o MANUAL. */
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
}
