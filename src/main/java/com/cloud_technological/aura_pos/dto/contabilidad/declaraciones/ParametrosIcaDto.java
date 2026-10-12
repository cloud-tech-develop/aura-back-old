package com.cloud_technological.aura_pos.dto.contabilidad.declaraciones;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * Datos del municipio para el borrador de ICA: la tarifa depende de la
 * actividad y del municipio, así que la escribe el contador.
 */
@Data
public class ParametrosIcaDto {
    private LocalDate desde;
    private LocalDate hasta;
    /** Tarifa de la actividad, por mil (ej. 9,66). */
    private BigDecimal tarifaPorMil;
    /** Avisos y tableros: 15 % del ICA. */
    private Boolean avisosTableros;
    /** Sobretasa bomberil, % sobre el ICA (0 si el municipio no la cobra). */
    private BigDecimal bomberilPct;
    /** Ingresos exentos, no sujetos o de otros municipios que se restan de la base. */
    private BigDecimal deducciones;
    /** Secretaría de hacienda del municipio: a quién se le causa y paga. */
    private Long terceroAcreedorId;
}
