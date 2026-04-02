package com.cloud_technological.aura_pos.dto.ordenes_compra;

import java.time.LocalDate;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateOrdenCompraDto {
    @NotNull private Long proveedorId;
    @NotNull private Integer sucursalId;
    private LocalDate fecha;
    private LocalDate fechaEntregaEsperada;
    private String observaciones;
    @NotEmpty @Valid
    private List<CreateOrdenCompraDetalleDto> detalles;
}
