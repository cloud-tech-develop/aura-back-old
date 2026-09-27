package com.cloud_technological.aura_pos.dto.consumo_interno;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateConsumoInternoDto {

    @NotNull(message = "La sucursal es obligatoria")
    private Long sucursalId;

    /** Bodega de la que sale o a la que entra. Sin ella, la principal de la sucursal. */
    private Long bodegaId;

    @NotNull(message = "El concepto es obligatorio")
    private Long conceptoId;

    /** Quién lo retiró o para quién. Opcional. */
    private Long responsableTerceroId;

    @Size(max = 300, message = "La observación no puede superar 300 caracteres")
    private String observacion;

    /** Causar el IVA por retiro. Si no viene, manda el valor del concepto. */
    private Boolean generaIva;

    @Valid
    @NotEmpty(message = "Debe agregar al menos un producto")
    private List<CreateConsumoInternoDetalleDto> detalles;
}
