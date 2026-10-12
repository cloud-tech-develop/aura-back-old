package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Vinculación vigente evaluada para entrar a una prenómina. {@code motivo} vacío =
 * se puede agregar; con texto = la causa por la que se omite.
 */
@Getter
@Setter
public class CandidatoNominaDto {
    private Long contratoId;
    private Long empleadoId;
    private String codigoVinculacion;
    private String empleadoNombre;
    private String empleadoDocumento;
    private BigDecimal salarioBase;
    private String motivo;
}
