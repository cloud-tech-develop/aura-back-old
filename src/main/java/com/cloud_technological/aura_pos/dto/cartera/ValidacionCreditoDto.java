package com.cloud_technological.aura_pos.dto.cartera;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValidacionCreditoDto {
    private boolean permitido;
    private boolean requiereAutorizacion;
    private String  motivoBloqueo;       // null si está permitido

    private BigDecimal cupoActual;
    private BigDecimal saldoCartera;
    private BigDecimal saldoDisponible;
    private BigDecimal montoSolicitado;
    private BigDecimal excedente;        // 0 si no supera el cupo

    private int  diasMoraMaximo;
    private int  diasMoraTolerancia;
    private String estadoCredito;
}
