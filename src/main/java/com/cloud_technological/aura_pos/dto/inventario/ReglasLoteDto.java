package com.cloud_technological.aura_pos.dto.inventario;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** Reglas de vencimiento de la empresa. */
@Getter
@Setter
public class ReglasLoteDto {
    /** La venta, el obsequio y el consumo interno no sacan de lotes vencidos. */
    @NotNull(message = "Indica si se bloquean los lotes vencidos")
    private Boolean bloquearVencidos;

    /** Días antes del vencimiento en que se empieza a avisar. */
    @NotNull(message = "Indica los días de alerta")
    @Min(value = 0, message = "Los días de alerta no pueden ser negativos")
    @Max(value = 365, message = "Los días de alerta no pueden superar 365")
    private Integer diasAlerta;
}
