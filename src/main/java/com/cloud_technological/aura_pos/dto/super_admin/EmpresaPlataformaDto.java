package com.cloud_technological.aura_pos.dto.super_admin;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmpresaPlataformaDto {
    private Integer id;
    private String razonSocial;
    private String nombreComercial;
    private String nit;
    private String dv;
    private String logoUrl;
    private String telefono;
    private String municipio;
    private Integer municipioId;
    private Boolean activa;
    private String modoContabilizacion;
    private LocalDateTime createdAt;
    // Firma contable (V199, docs/PLAN_FIRMA_CONTABLE.md)
    private Boolean esFirmaContable;
    private Integer firmaContableId;
    private String firmaContableNombre;
    private Integer totalClientesFirma;
    private Integer cupoGestionados;
    private Integer totalGestionadosFirma;
    /** Modalidad del vínculo con su firma: CLIENTE_PAGA | GESTIONADO; null sin firma. */
    private String firmaModalidad;

    // ── Facturación electrónica (Factus): las credenciales las pone solo la plataforma ──
    private Boolean facturaElectronica;
    private String factusClientId;
    private String factusClientSecret;
    private String factusUsername;
    private String factusPassword;
    private Integer factusNumberingRangeId;
    private String factusPrefijo;
    // Stats en vivo
    private Integer totalSucursales;
    private Integer totalUsuarios;
    private Long totalVentas;
}
