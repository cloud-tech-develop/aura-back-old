package com.cloud_technological.aura_pos.dto.cartera.credito;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A quién le aplicaría una regla hoy y qué le pasaría. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SimulacionReglaDto {
    private Long terceroId;
    private String terceroNombre;
    private Integer score;
    private Integer diasMora;
    private String estadoCredito;
    private BigDecimal cupoActual;
    private BigDecimal cupoResultante;
    private String estadoResultante;
    /** La regla ya actuó sobre este cliente dentro de los días de espera. */
    private boolean enEspera;
}
