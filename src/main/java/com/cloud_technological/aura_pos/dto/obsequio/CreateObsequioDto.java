package com.cloud_technological.aura_pos.dto.obsequio;

import java.util.List;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateObsequioDto {

    @NotNull(message = "La sucursal es obligatoria")
    private Long sucursalId;

    /** Quién recibe. Opcional: una muestra en punto de venta no siempre lo tiene. */
    private Long terceroId;

    @NotNull(message = "El motivo es obligatorio")
    @Pattern(regexp = "MUESTRA_COMERCIAL|PROMOCION|CORTESIA_CLIENTE|DONACION|OTRO",
             message = "Motivo inválido")
    private String motivo;

    @Size(max = 300, message = "La observación no puede superar 300 caracteres")
    private String observacion;

    /**
     * Causar el IVA por retiro de inventario. Por defecto sí: el retiro se
     * considera venta para efectos de IVA. Se puede apagar cuando el contador
     * determine que el caso no lo genera (p. ej. producto excluido).
     */
    private Boolean generaIva = Boolean.TRUE;

    @NotEmpty(message = "Debe agregar al menos un producto")
    private List<CreateObsequioDetalleDto> detalles;
}
