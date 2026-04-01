package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MovimientoCajaDto {
    private Long       id;
    private String     tipo;          // INGRESO | EGRESO
    private String     concepto;
    private BigDecimal monto;
    private String     fecha;
    private String     usuarioNombre;
    /** Número de la cuenta por cobrar o pagar asociada */
    private String     cuentaNumero;
    /** Nombre del cliente (INGRESO) o proveedor (EGRESO) */
    private String     terceroNombre;
    private String     metodoPago;
    private String     entregadoA;
    private Long       comprobanteId;
    private String     numeroComprobante;
}

