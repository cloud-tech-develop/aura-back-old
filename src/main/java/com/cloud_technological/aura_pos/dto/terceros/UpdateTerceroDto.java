package com.cloud_technological.aura_pos.dto.terceros;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTerceroDto {
    private Long id;
    private String tipoDocumento;
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
    private Boolean esCliente;
    private Boolean esProveedor;
    private Boolean esEmpleado;
    private Boolean esBanco;
    private Long municipioId;
    private String municipio;
    private Boolean activo;
    // Campos fiscales (V52)
    private String tipoPersona;
    private String regimen;
    private Boolean granContribuyente;
    private Boolean autoRetenedor;
    private String codigoCIIU;
    private String actividadEconomica;
    private String pais;
    private String codigoPais;

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
    private Boolean esAutoretenedorIca;
    private Boolean esAutoretenedorFuente;
    private Boolean declarante;

    // ── Seguridad social (V120) — EPS/AFP/CCF/ARL ──────────────────
    private java.util.List<String> roles;
    private String codigoSeguridadSocial;

    // ── Bancario (V97) ──────────────────────────────────────────────
    private Long bancoTerceroId;         // FK a un tercero con rol BANCO
    private String tipoCuenta;
    private String numeroCuenta;

    // NOTA: `entidadSeguridadSocialId` (enlace al catálogo nacional de
    // EPS/AFP/CCF/ARL) se agrega en la FASE 5.5, cuando corra V110. Ponerlo
    // antes rompe ddl-auto=validate: la columna no existe todavía.
}
