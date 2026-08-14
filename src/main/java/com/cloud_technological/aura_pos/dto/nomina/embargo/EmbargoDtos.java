package com.cloud_technological.aura_pos.dto.nomina.embargo;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** DTOs de embargos (V113). */
public final class EmbargoDtos {

    private EmbargoDtos() {}

    @Getter
    @Setter
    public static class EmbargoDto {
        private Long id;
        private String expediente;
        private String tipo;
        private Integer prioridad;
        private BigDecimal valorTotal;
        private BigDecimal porcentaje;
        private BigDecimal saldo;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private String estado;
        private String observacion;
        /** Alimentos y cooperativa llegan al 50% sin el piso de 1 SMMLV. */
        private boolean cupoAmpliado;
    }

    @Getter
    @Setter
    public static class CreateEmbargoDto {
        private Long contratoId;
        private String expediente;
        private String tipo;
        private Integer prioridad;
        private Long juzgadoId;
        private Long demandanteId;
        /** O valor total O porcentaje, nunca ambos (hay CHECK en BD). */
        private BigDecimal valorTotal;
        private BigDecimal porcentaje;
        private LocalDate fechaInicio;
        private String observacion;
    }

    @Getter
    @Setter
    public static class TerminarEmbargoDto {
        private LocalDate fechaFin;
    }
}
