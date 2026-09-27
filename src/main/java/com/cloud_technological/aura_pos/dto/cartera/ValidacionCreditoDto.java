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

    /** Aprobación vigente que permite pasar el cupo; la venta la consume. */
    private Long solicitudAutorizadaId;
    /** Solicitud ya enviada y aún sin respuesta: el POS la sigue esperando. */
    private Long solicitudPendienteId;
    /** "Autorizado por X hasta las HH:mm". */
    private String autorizacion;
}
