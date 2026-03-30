package com.cloud_technological.aura_pos.dto.cartera;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateGestionCobroDto {

    @NotNull(message = "El tercero es obligatorio")
    private Long terceroId;

    private Long cuentaCobrarId; // opcional: gestión sobre una cuenta específica

    @NotBlank(message = "El tipo de gestión es obligatorio")
    private String tipoGestion; // LLAMADA | EMAIL | VISITA | NOTA | ACUERDO_PAGO | MENSAJE

    private String resultado;  // CONTACTADO | NO_CONTESTO | PROMESA_PAGO | RENUENTE | PAGADO

    private String nota;

    private LocalDate fechaPromesaPago;

    private BigDecimal montoPrometido;
}
