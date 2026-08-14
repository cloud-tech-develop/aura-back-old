package com.cloud_technological.aura_pos.dto.cuentas_cobrar;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AbonoCobrarDto {
    private Long id;
    private Long cuentaCobrarId;
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
    private LocalDateTime fechaPago;
    private LocalDateTime createdAt;
}
