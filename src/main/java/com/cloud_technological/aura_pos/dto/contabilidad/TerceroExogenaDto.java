package com.cloud_technological.aura_pos.dto.contabilidad;

/**
 * Datos fiscales del tercero que exige el prevalidador DIAN (E11): el
 * validador previo marca como incompleto al que le falte documento, DV
 * (solo NIT), dirección o municipio.
 *
 * @param codigoDane código DANE del municipio (5 dígitos: departamento + municipio)
 * @param codigoPais código de país del tercero (ISO, p. ej. CO)
 * @param tipoPersona NATURAL | JURIDICA
 */
public record TerceroExogenaDto(
        Long id,
        String tipoDocumento,
        String numeroDocumento,
        String dv,
        String razonSocial,
        String nombres,
        String apellidos,
        String direccion,
        String municipio,
        String nombre1,
        String nombre2,
        String apellido1,
        String apellido2,
        String tipoPersona,
        String codigoDane,
        String codigoPais) {

    public String nombreCompleto() {
        if (razonSocial != null && !razonSocial.isBlank()) {
            return razonSocial;
        }
        return ((nombres != null ? nombres : "") + " "
                + (apellidos != null ? apellidos : "")).trim();
    }
}
