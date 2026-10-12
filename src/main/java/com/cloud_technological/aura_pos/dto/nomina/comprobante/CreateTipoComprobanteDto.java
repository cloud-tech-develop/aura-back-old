package com.cloud_technological.aura_pos.dto.nomina.comprobante;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Crear o editar un tipo de comprobante de nómina. */
@Getter
@Setter
public class CreateTipoComprobanteDto {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 20, message = "El código admite hasta 20 caracteres")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
    private String nombre;

    @NotBlank(message = "La clase es obligatoria")
    @Pattern(regexp = "PERIODICO|PRIMA|CESANTIAS|INTERESES_CESANTIAS|LIQUIDACION_DEFINITIVA|RETROACTIVO|OTROS_PAGOS",
            message = "Clase de comprobante no válida")
    private String clase;

    @NotBlank(message = "El prefijo es obligatorio")
    @Pattern(regexp = "[A-Za-z0-9]{1,10}", message = "El prefijo admite hasta 10 letras o números, sin espacios")
    private String prefijo;

    /** Obligatoria para los periódicos; se ignora en las demás clases. */
    @Pattern(regexp = "MENSUAL|QUINCENAL|SEMANAL", message = "Frecuencia no válida")
    private String frecuencia;

    @Size(max = 500, message = "La descripción admite hasta 500 caracteres")
    private String descripcion;

    /** Tipos de vinculación cuyos empleados entran al comprobante; vacío = todos. */
    private List<Long> tiposVinculacionIds = new ArrayList<>();

    private Boolean activo = true;
}
