package com.cloud_technological.aura_pos.dto.cartera;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CuentaVencidaAlertaDto {
    private Long       cuentaId;
    private String     numeroCuenta;
    private Long       terceroId;
    private String     terceroNombre;
    private String     terceroDocumento;
    private BigDecimal saldoPendiente;
    private String     fechaVencimiento;   // ISO string
    private int        diasVencida;        // días desde la fecha de vencimiento
    private String     estadoCredito;      // estado en tercero_credito (puede ser null)
    private int        scoreCrediticio;
    private String     ultimaGestion;      // tipo de la última gestión de cobro
    private String     fechaUltimaGestion; // fecha de la última gestión
}
