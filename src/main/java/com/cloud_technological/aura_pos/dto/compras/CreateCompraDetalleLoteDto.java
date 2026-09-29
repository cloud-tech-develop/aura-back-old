package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * Un lote de la línea de compra. En una compra se escribe el código y el
 * vencimiento; en una nota crédito se elige un lote existente (loteId).
 * La cantidad va en la presentación de la línea (bultos); el back la pasa a base.
 */
@Getter
@Setter
public class CreateCompraDetalleLoteDto {
    private Long loteId;
    private String codigoLote;
    private LocalDate fechaVencimiento;
    private LocalDate fechaFabricacion;

    @NotNull(message = "La cantidad del lote es obligatoria")
    private BigDecimal cantidad;
}
