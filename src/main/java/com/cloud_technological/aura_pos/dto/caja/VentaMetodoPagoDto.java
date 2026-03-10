package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VentaMetodoPagoDto {
    private String     metodoPago;    // EFECTIVO, NEQUI, TARJETA
    private Integer    totalPagos;    // cantidad de transacciones con ese método
    private BigDecimal totalMonto;    // suma total recibida por ese método
}
