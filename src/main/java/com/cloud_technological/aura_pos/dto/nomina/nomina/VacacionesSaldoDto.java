package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * Saldo de vacaciones de un empleado (F3): lo causado por antigüedad más el saldo
 * traído de otro sistema, menos lo ya tomado.
 */
@Data
public class VacacionesSaldoDto {
    private Long empleadoId;
    private LocalDate fechaIngreso;
    private Integer antiguedadDias;
    /** 15 días hábiles por año → antigüedad × 15/360. */
    private BigDecimal diasCausados;
    private BigDecimal saldoInicial;
    private Integer diasTomados;
    /** saldoInicial + causados − tomados. Lo que puede tomar hoy. */
    private BigDecimal diasDisponibles;
    /** Si la empresa permite tomar más de lo disponible (anticipadas). */
    private Boolean permiteAnticipadas;
}
