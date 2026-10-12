package com.cloud_technological.aura_pos.dto.terceros;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTerceroDto {
    @NotBlank(message = "El tipo de documento es obligatorio")
    private String tipoDocumento;
    @NotBlank(message = "El número de documento es obligatorio")
    private String numeroDocumento;
    private String dv;
    private String razonSocial;

    /** @deprecated Usar nombre1/nombre2. DIAN y UGPP exigen desagregado. */
    @Deprecated
    private String nombres;
    /** @deprecated Usar apellido1/apellido2. */
    @Deprecated
    private String apellidos;

    // ── Identificación desagregada (V97) ────────────────────────────
    private String nombre1;
    private String nombre2;
    private String apellido1;
    private String apellido2;

    private String direccion;
    private String telefono;
    private String email;
    private String emailFe;
    private String responsabilidadFiscal;
    private Boolean esCliente = true;
    private Boolean esProveedor = false;
    private Boolean esEmpleado = false;
    private Boolean esBanco = false;
    private Long municipioId;
    private String municipio;
    private Boolean activo = true;
    // Campos fiscales (V52)
    private String tipoPersona = "NATURAL";
    private String regimen = "NO_RESPONSABLE_IVA";
    private Boolean granContribuyente = false;
    private Boolean autoRetenedor = false;
    private String codigoCIIU;
    private String actividadEconomica;
    private String pais = "Colombia";
    private String codigoPais = "CO";

    // ── Persona natural (V97) — requeridos por PILA ─────────────────
    private java.time.LocalDate fechaNacimiento;
    private String sexo;                 // M | F | OTRO
    private java.time.LocalDate fechaExpedicionDocumento;
    private Long municipioExpedicionId;

    // ── Persona jurídica (V97) ──────────────────────────────────────
    private String nombreComercial;
    private String representanteLegalNombre;
    private String representanteLegalDocumento;

    // ── Fiscal (V97) ────────────────────────────────────────────────
    // `autoRetenedor` es uno solo; estos discriminan renta vs. ICA.
    private Boolean esAutoretenedorIca = false;
    private Boolean esAutoretenedorFuente = false;
    private Boolean declarante = false;

    // ── Seguridad social (V120) — cuando el tercero es EPS/AFP/CCF/ARL ──
    /** Roles de seguridad social a asignar: EPS | AFP | CCF | ARL. Los básicos
     *  (cliente/proveedor/etc.) siguen yendo por los booleanos. */
    private java.util.List<String> roles;
    /** Código oficial UGPP de la entidad. Lo exige PILA. */
    private String codigoSeguridadSocial;

    // ── Bancario (V97) ──────────────────────────────────────────────
    private Long bancoTerceroId;         // FK a un tercero con rol BANCO
    private String tipoCuenta;
    private String numeroCuenta;

    // ── Persona natural ampliada (V211) ─────────────────────────────
    @Size(max = 20, message = "El RUT admite hasta 20 caracteres")
    private String rut;
    @Size(max = 3)
    private String codigoPaisExpedicion;
    @Pattern(regexp = "SOLTERO|CASADO|UNION_LIBRE|SEPARADO|DIVORCIADO|VIUDO", message = "Estado civil no válido")
    private String estadoCivil;
    @Pattern(regexp = "(O|A|B|AB)[+-]", message = "RH no válido")
    private String rh;
    @Size(max = 3)
    private String codigoPaisNacimiento;
    private Long municipioNacimientoId;
    @Pattern(regexp = "URBANA|RURAL", message = "Zona no válida")
    private String zona;
    @Size(max = 80, message = "La comuna admite hasta 80 caracteres")
    private String comuna;
    @Size(max = 80, message = "El barrio admite hasta 80 caracteres")
    private String barrio;
    @Size(max = 150, message = "El nombre del contacto admite hasta 150 caracteres")
    private String contactoNombre;
    /** Teléfonos (propios y del contacto). null = no se tocan los guardados. */
    @Valid
    private List<TerceroTelefonoDto> telefonos;
    /** Correos (propios y del contacto). null = no se tocan los guardados. */
    @Valid
    private List<TerceroCorreoDto> correos;
}
