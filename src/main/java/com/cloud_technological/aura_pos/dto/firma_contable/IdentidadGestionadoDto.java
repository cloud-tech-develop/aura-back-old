package com.cloud_technological.aura_pos.dto.firma_contable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Identidad tributaria de un cliente gestionado que la firma maneja
 * (docs/PLAN_FIRMA_CONTABLE.md §10): persona natural o jurídica, nombre y DV.
 * El NIT no se cambia: es la llave de la empresa en Aura.
 */
@Getter
@Setter
public class IdentidadGestionadoDto {

    /** Solo lectura: el NIT con el que se creó. */
    private String nit;

    @NotBlank(message = "Elija si es persona natural o jurídica")
    @Pattern(regexp = "NATURAL|JURIDICA", message = "El tipo de persona debe ser NATURAL o JURIDICA")
    private String tipoPersona;

    /** NIT o CC (persona natural); la jurídica siempre es NIT. */
    @Pattern(regexp = "^$|NIT|CC|CE|PA|PPT", message = "Tipo de documento no válido")
    private String tipoDocumento;

    /** Jurídica: razón social. Natural: se arma con nombres y apellidos. */
    @Size(max = 200, message = "La razón social no puede superar 200 caracteres")
    private String razonSocial;

    @Size(max = 120, message = "Los nombres no pueden superar 120 caracteres")
    private String nombres;

    @Size(max = 120, message = "Los apellidos no pueden superar 120 caracteres")
    private String apellidos;

    @Size(max = 200, message = "El nombre comercial no puede superar 200 caracteres")
    private String nombreComercial;

    /** Vacío = se calcula con el NIT. */
    @Size(max = 2, message = "El dígito de verificación es de un dígito")
    private String dv;
}
