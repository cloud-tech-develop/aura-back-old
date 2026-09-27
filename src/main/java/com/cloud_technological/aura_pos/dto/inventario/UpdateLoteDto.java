package com.cloud_technological.aura_pos.dto.inventario;

import java.time.LocalDate;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Corrección de un lote: solo código y vencimiento, siempre con motivo. */
@Getter
@Setter
public class UpdateLoteDto {
    @NotBlank(message = "El código de lote es obligatorio")
    @Size(max = 100, message = "El código de lote no puede superar 100 caracteres")
    private String codigoLote;

    private LocalDate fechaVencimiento;

    @NotBlank(message = "Indica el motivo de la corrección")
    @Size(max = 300, message = "El motivo no puede superar 300 caracteres")
    private String motivo;
}
