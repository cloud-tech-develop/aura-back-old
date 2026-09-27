package com.cloud_technological.aura_pos.dto.cartera.ficha;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FichaGestionDto {
    private Long id;
    private String tipoGestion;
    private String resultado;
    private String nota;
    private LocalDate fechaPromesaPago;
    private BigDecimal montoPrometido;
    private String estadoPromesa;
    private BigDecimal montoPagadoPromesa;
    private String numeroCuenta;
    private String usuarioNombre;
    private LocalDateTime createdAt;
}
