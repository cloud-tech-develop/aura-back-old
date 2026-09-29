package com.cloud_technological.aura_pos.dto.nomina.retefuente;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** DTOs de deducciones de retefuente (Fase 4.5). */
public final class RetefuenteDtos {

    private RetefuenteDtos() {}

    @Getter
    @Setter
    public static class DeduccionDto {
        private Long id;
        private String tipo;
        private BigDecimal valor;
        private LocalDate vigenteDesde;
        private LocalDate vigenteHasta;
        private String soporte;
        /** DEPENDIENTES lo calcula el motor (10% topado a 32 UVT): no lleva valor. */
        private boolean valorLoCalculaElMotor;
    }

    @Getter
    @Setter
    public static class CreateDeduccionDto {
        private Long contratoId;
        private String tipo;
        private BigDecimal valor;
        private LocalDate vigenteDesde;
        private LocalDate vigenteHasta;
        private String soporte;
    }

    @Getter
    @Setter
    public static class CambiarProcedimientoDto {
        /** '1' | '2'. */
        private String procedimiento;
    }
}
