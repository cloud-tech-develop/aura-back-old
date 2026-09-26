package com.cloud_technological.aura_pos.dto.cartera.ficha;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FichaPagoDto {
    /** ABONO | CRUCE_ANTICIPO */
    private String tipo;
    private Long id;
    private LocalDateTime fecha;
    private BigDecimal monto;
    private String metodoPago;
    private String referencia;
    private Long cuentaCobrarId;
    private String numeroCuenta;
    private Long reciboCajaId;
    private String reciboNumero;
    private String usuarioNombre;
}
