package com.cloud_technological.aura_pos.dto.productos;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** "Pasar a unidad": la presentación pequeña pasa a ser la unidad base del producto. */
@Getter
@Setter
public class CambioUnidadRequestDto {

    /** Presentación más pequeña que la base (contiene 1/N) que pasa a ser la base. */
    @NotNull(message = "Indica la presentación que pasará a ser la unidad base")
    private Long presentacionId;

    /** Unidad de medida del producto después del cambio (p. ej. UNIDAD). */
    @NotNull(message = "Indica la unidad de medida nueva")
    private Long unidadMedidaId;

    /** Nombre de la presentación que queda con la base vieja (p. ej. "Paca"). */
    @NotBlank(message = "Indica el nombre de la presentación grande")
    private String nombrePresentacion;
}
