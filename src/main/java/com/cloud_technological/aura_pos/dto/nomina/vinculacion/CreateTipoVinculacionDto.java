package com.cloud_technological.aura_pos.dto.nomina.vinculacion;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Crear o editar un tipo de vinculación. */
@Getter
@Setter
public class CreateTipoVinculacionDto {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 20, message = "El código admite hasta 20 caracteres")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
    private String nombre;

    @Pattern(regexp = "INDEFINIDO|FIJO|OBRA_LABOR|PRESTACION_SERVICIOS|APRENDIZAJE",
            message = "Tipo de contrato no válido")
    private String tipoContrato;

    @NotBlank(message = "El tipo de remuneración es obligatorio")
    @Pattern(regexp = "MENSUAL|INTEGRAL|JORNAL|HORA", message = "Tipo de remuneración no válido")
    private String tipoRemuneracion;

    @NotBlank(message = "La frecuencia de pago es obligatoria")
    @Pattern(regexp = "MENSUAL|QUINCENAL|SEMANAL", message = "Frecuencia de pago no válida")
    private String frecuenciaPago;

    private Boolean exigeFechaFin = false;

    private Boolean unicaPorEmpleado = true;

    @Size(max = 500, message = "La descripción admite hasta 500 caracteres")
    private String descripcion;

    private Boolean activo = true;
}
