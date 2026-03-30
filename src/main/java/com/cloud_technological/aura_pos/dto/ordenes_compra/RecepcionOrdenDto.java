package com.cloud_technological.aura_pos.dto.ordenes_compra;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class RecepcionOrdenDto {
    /** Número de factura del proveedor */
    private String numeroFactura;
    private String observaciones;
    @NotEmpty @Valid
    private List<RecepcionLineaDto> lineas;
}
