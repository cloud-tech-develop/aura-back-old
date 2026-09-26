package com.cloud_technological.aura_pos.dto.cartera.ficha;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FichaAnticipoDto {
    private Long id;
    private LocalDate fecha;
    private BigDecimal monto;
    private BigDecimal saldo;
    private String metodoPago;
    private String observaciones;
    private String reciboNumero;
}
