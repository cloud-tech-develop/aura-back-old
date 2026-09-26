package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Líneas copiadas de Excel (separadas por tabulador) o de un CSV con punto y
 * coma. Columnas: cuenta (código), tercero (documento), centro de costo
 * (código), descripción, débito, crédito.
 */
@Getter @Setter
public class ImportarLineasDto {

    @NotBlank(message = "Pegue las líneas a importar")
    @Size(max = 500_000, message = "El texto es demasiado grande")
    private String texto;
}
