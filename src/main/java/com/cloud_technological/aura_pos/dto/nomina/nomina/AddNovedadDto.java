package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddNovedadDto {
    private String tipo;
    private String descripcion;
    private BigDecimal cantidad;
    private BigDecimal valorUnitario;

    // ── F0: novedades de ausencia con fechas ────────────────────────────────
    /** Inicio del hecho (incapacidad/licencia/vacaciones). Deriva los días. */
    private LocalDate fechaInicio;
    /** Fin del hecho. */
    private LocalDate fechaFin;
    /** Discrimina el tipo (p. ej. INCAPACIDAD → GENERAL / RIESGO_LABORAL). */
    private String subtipo;
    /** Número de autorización de la incapacidad/licencia (lo exige la UGPP). */
    private String numeroAutorizacion;
}
