package com.cloud_technological.aura_pos.dto.conceptos_caja;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateConceptoCajaDto {

    @NotBlank
    private String nombre;

    @NotBlank
    @Pattern(regexp = "INGRESO|EGRESO", message = "tipo debe ser INGRESO o EGRESO")
    private String tipo;

    @NotNull
    private Long cuentaContableId;

    /** Solo usado al actualizar; en creación se ignora (queda activo). */
    private Boolean activo;
}
