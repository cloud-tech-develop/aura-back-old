package com.cloud_technological.aura_pos.dto.contabilidad;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreatePlanCuentaDto {

    @NotBlank
    private String codigo;

    @NotBlank
    private String nombre;

    @NotNull
    private String tipo;

    @NotNull
    private String naturaleza;

    @NotNull
    private Short nivel;

    private Long padreId;

    private Boolean auxiliar = Boolean.FALSE;

    private String codigoDian;
}
