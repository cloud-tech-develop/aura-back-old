package com.cloud_technological.aura_pos.dto.terceros;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TerceroDto {
    private Long id;
    private Integer empresaId;
    private String tipoDocumento;
    private String numeroDocumento;
    private String dv;
    private String razonSocial;

    /** @deprecated Usar nombre1/nombre2. Se sigue exponiendo para el front actual. */
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

    // ── Persona natural (V97) ───────────────────────────────────────
    private java.time.LocalDate fechaNacimiento;
    private String sexo;
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

    // ── Bancario (V97) ──────────────────────────────────────────────
    private Long bancoTerceroId;
    /** Nombre del banco (resuelto), para que el selector lo muestre al cargar. */
    private String bancoTerceroNombre;
    private String tipoCuenta;
    private String numeroCuenta;

    // NOTA: `entidadSeguridadSocialId` llega en la FASE 5.5 (V110).

    /** Roles del tercero (V98). Se llena desde tercero_rol, no de los booleanos. */
    private java.util.Set<String> roles;

    /** Código oficial UGPP si es EPS/AFP/CCF/ARL (V120). */
    private String codigoSeguridadSocial;
}
