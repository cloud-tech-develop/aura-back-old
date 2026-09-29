package com.cloud_technological.aura_pos.dto.contabilidad;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Forma de pago con su cuenta contable asociada. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormaPagoContableDto {

    private Long id;

    @NotBlank
    private String codigo;

    @NotBlank
    private String nombre;

    private Long cuentaContableId;
    private String cuentaContable;
    private Boolean requiereCuentaBancaria;
    private Boolean activo;
}
