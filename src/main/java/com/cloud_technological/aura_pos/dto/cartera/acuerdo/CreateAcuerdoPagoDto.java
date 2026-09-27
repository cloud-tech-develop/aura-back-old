package com.cloud_technological.aura_pos.dto.cartera.acuerdo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Acuerdo de pago: qué cuentas se refinancian y en qué cuotas. */
@Getter
@Setter
public class CreateAcuerdoPagoDto {
    private Long terceroId;
    private List<Long> cuentaCobrarIds;
    private List<Cuota> cuotas;
    /** SEMANAL, QUINCENAL, MENSUAL o PERSONALIZADA (solo informativo: las fechas vienen en las cuotas). */
    private String frecuencia;
    /** Días después de la fecha de una cuota antes de darla por incumplida. */
    private Integer diasGracia;
    private String observaciones;

    @Getter
    @Setter
    public static class Cuota {
        private LocalDate fechaVencimiento;
        private BigDecimal valor;
    }
}
