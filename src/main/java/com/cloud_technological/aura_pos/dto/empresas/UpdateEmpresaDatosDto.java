package com.cloud_technological.aura_pos.dto.empresas;

import javax.validation.constraints.Email;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Lo que el administrador de la empresa edita desde "Mi perfil" con la acción
 * especial caja.usuarios:EDITAR_EMPRESA (V198). Incluye la razón social (2026-10-20:
 * cambia el nombre en el RUT y el usuario la debe poder corregir); el NIT y las
 * credenciales de Factus siguen solo en la plataforma.
 * El formulario manda todos los campos: lo que llega vacío o null queda vacío.
 */
@Getter
@Setter
public class UpdateEmpresaDatosDto {

    /** null = no cambia (clientes que aún no mandan el campo). */
    @Size(max = 255, message = "La razón social no puede superar 255 caracteres")
    private String razonSocial;

    @Size(max = 255, message = "El nombre comercial no puede superar 255 caracteres")
    private String nombreComercial;

    @Size(max = 255, message = "La URL del logo no puede superar 255 caracteres")
    private String logoUrl;

    // ── Contacto (vive en el tercero de la empresa) ──────────────────
    @Size(max = 30, message = "El telefono no puede superar 30 caracteres")
    private String telefono;

    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 150, message = "El correo no puede superar 150 caracteres")
    private String correo;

    @Size(max = 200, message = "La direccion no puede superar 200 caracteres")
    private String direccion;

    @Size(max = 120, message = "El municipio no puede superar 120 caracteres")
    private String municipio;

    // ── Resolución de facturación ────────────────────────────────────
    @Size(max = 50, message = "El número de resolución no puede superar 50 caracteres")
    private String resolucionNumero;

    @Size(max = 20, message = "El prefijo no puede superar 20 caracteres")
    private String resolucionPrefijo;

    @Min(value = 0, message = "El consecutivo inicial no puede ser negativo")
    private Integer resolucionDesde;

    @Min(value = 0, message = "El consecutivo final no puede ser negativo")
    private Integer resolucionHasta;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "La fecha inicial debe ser AAAA-MM-DD")
    private String resolucionFechaDesde;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "La fecha final debe ser AAAA-MM-DD")
    private String resolucionFechaHasta;

    // ── Rangos de numeración en Factus ───────────────────────────────
    @Min(value = 1, message = "El ID del rango de facturación debe ser mayor que cero")
    private Integer factusNumberingRangeId;

    @Size(max = 40, message = "El ID del rango de nómina no puede superar 40 caracteres")
    private String factusNominaNumberingRangeId;
}
