package com.cloud_technological.aura_pos.dto.ordenes_compra;

import java.math.BigDecimal;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class RecepcionLineaDto {
    @NotNull private Long detalleId;
    @NotNull @PositiveOrZero private BigDecimal cantidadRecibida;
}
