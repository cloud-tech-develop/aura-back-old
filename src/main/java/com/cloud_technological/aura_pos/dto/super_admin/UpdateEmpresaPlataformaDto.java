package com.cloud_technological.aura_pos.dto.super_admin;

import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateEmpresaPlataformaDto {
    @Size(max = 200)
    private String razonSocial;

    @Size(max = 200)
    private String nombreComercial;

    private String dv;
    private String logoUrl;
    private String telefono;
    private String municipio;
    private Integer municipioId;
    private Boolean activa;
    /** AUTOMATICO | REVISION */
    private String modoContabilizacion;

    /** null = no cambia. La firma que la atiende se cambia en PUT /empresas/{id}/firma-contable. */
    private Boolean esFirmaContable;

    /** Clientes gestionados que puede crear la firma; null = no cambia. */
    @javax.validation.constraints.Min(value = 0, message = "El cupo no puede ser negativo")
    private Integer cupoGestionados;

    // ── Facturación electrónica (Factus): las credenciales las pone solo la plataforma ──
    private Boolean facturaElectronica;
    private String factusClientId;
    private String factusClientSecret;
    private String factusUsername;
    private String factusPassword;
    private Integer factusNumberingRangeId;
    private String factusPrefijo;
}
