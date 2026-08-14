package com.cloud_technological.aura_pos.dto.cuentas_pagar;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
    @Size(max = 30, message = "El método de pago no puede superar 30 caracteres")
    private String metodoPago;

    @Size(max = 255, message = "La referencia no puede superar 255 caracteres")
    private String referencia;
    @Size(max = 100, message = "El banco no puede superar 100 caracteres")
    private String banco;
    /** Cuenta bancaria DE DONDE sale el dinero del abono (origen). Null si es efectivo. */
    private Long cuentaBancariaId;
    private LocalDateTime fechaPago;
    private LocalDateTime createdAt;
}
