package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SaveNotaDiarioPlantillaDto {

    @NotBlank(message = "El nombre de la plantilla es obligatorio")
    @Size(max = 150, message = "El nombre admite máximo 150 caracteres")
    private String nombre;

    @NotBlank(message = "El concepto es obligatorio")
    @Size(max = 500, message = "El concepto admite máximo 500 caracteres")
    private String descripcion;

    private String clasificacion;

    private Boolean recurrente = Boolean.FALSE;

    /** 1–28: todos los meses lo tienen. Obligatorio si es recurrente. */
    @Min(value = 1, message = "El día debe estar entre 1 y 28")
    @Max(value = 28, message = "El día debe estar entre 1 y 28")
    private Short diaMes;

    private Boolean activa = Boolean.TRUE;

    /** null = conservar las líneas actuales (editar solo la cabecera). */
    @Valid
    private List<SaveNotaDiarioLineaDto> lineas;

    /** Crear: las líneas son obligatorias. */
    public boolean traeLineas() {
        return lineas != null;
    }
}
