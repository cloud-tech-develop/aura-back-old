package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "empresa")
@Getter @Setter
@Builder
@NoArgsConstructor  // ✅
@AllArgsConstructor // ✅
public class EmpresaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    @Column(name = "nombre_comercial")
    private String nombreComercial;

    @Column(nullable = false, unique = true)
    private String nit;

    private String dv;

    @Column(name = "logo_url")
    private String logoUrl;

    private String telefono;

    private String municipio;

    @Column(name = "municipio_id")
    private Integer municipioId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Object configuracion;

    private Boolean activa = true;

    @Column(name = "factura_electronica", nullable = false)
    private boolean facturaElectronica = false;

    @Column(name = "factus_client_id", length = 255)
    private String factusClientId;

    @Column(name = "factus_client_secret", length = 500)
    private String factusClientSecret;

    @Column(name = "factus_username", length = 255)
    private String factusUsername;

    @Column(name = "factus_password", length = 500)
    private String factusPassword;

    @Column(name = "factus_numbering_range_id")
    private Integer factusNumberingRangeId;

    @Column(name = "factus_prefijo", length = 20)
    private String factusPrefijo;

    @Column(name = "factus_access_token", columnDefinition = "TEXT")
    private String factusAccessToken;

    @Column(name = "factus_refresh_token", columnDefinition = "TEXT")
    private String factusRefreshToken;

    @Column(name = "factus_token_expiry")
    private java.time.LocalDateTime factusTokenExpiry;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
