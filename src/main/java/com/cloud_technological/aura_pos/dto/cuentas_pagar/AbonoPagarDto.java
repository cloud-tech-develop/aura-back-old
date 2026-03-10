package com.cloud_technological.aura_pos.dto.cuentas_pagar;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AbonoPagarDto {
    private Long id;
    private Long cuentaPagarId;
    private Long usuarioId;
    private String usuarioNombre;
    private Long turnoCajaId;
    
    @NotNull(message = "El monto es requerido")
    private BigDecimal monto;

    @NotNull(message = "El método de pago es requerido")
    private String metodoPago;

    private String referencia;
    private String banco;
    private LocalDateTime fechaPago;
    private LocalDateTime createdAt;
}
