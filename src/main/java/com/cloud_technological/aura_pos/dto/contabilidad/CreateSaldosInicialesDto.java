package com.cloud_technological.aura_pos.dto.contabilidad;

import java.time.LocalDate;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * Saldos iniciales de apertura al migrar una empresa al sistema. Produce un
 * asiento de apertura (tipoOrigen = APERTURA) con los saldos por cuenta.
 */
@Getter @Setter
public class CreateSaldosInicialesDto {

    @NotNull
    private LocalDate fechaApertura;

    /**
     * Cuenta de patrimonio que absorbe el descuadre (por defecto Resultados de
     * ejercicios anteriores). Si el asiento ya cuadra, no se usa.
     */
    private Long cuentaAjusteId;

    @NotEmpty
    @Valid
    private List<SaldoInicialLineaDto> lineas;
}
