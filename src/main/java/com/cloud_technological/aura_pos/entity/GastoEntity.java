package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gasto")
@Getter
@Setter
public class GastoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id")
    private SucursalEntity sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @Column(nullable = false, length = 100)
    private String categoria;

    private String descripcion;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private Boolean deducible;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ── Campos tributarios (V54) ────────────────────────────────
    @Column(name = "tercero_id")
    private Long terceroId;

    @Column(name = "cuenta_contable_id")
    private Long cuentaContableId;

    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    @Column(name = "base_iva", precision = 18, scale = 2)
    private BigDecimal baseIva = BigDecimal.ZERO;

    @Column(name = "tarifa_iva", precision = 5, scale = 2)
    private BigDecimal tarifaIva = BigDecimal.ZERO;

    @Column(name = "valor_iva", precision = 18, scale = 2)
    private BigDecimal valorIva = BigDecimal.ZERO;

    @Column(name = "base_retefuente", precision = 18, scale = 2)
    private BigDecimal baseRetefuente = BigDecimal.ZERO;

    @Column(name = "tarifa_retefuente", precision = 5, scale = 2)
    private BigDecimal tarifaRetefuente = BigDecimal.ZERO;

    @Column(name = "valor_retefuente", precision = 18, scale = 2)
    private BigDecimal valorRetefuente = BigDecimal.ZERO;

    @Column(name = "base_reteica", precision = 18, scale = 2)
    private BigDecimal baseReteica = BigDecimal.ZERO;

    @Column(name = "tarifa_reteica", precision = 5, scale = 2)
    private BigDecimal tarifaReteica = BigDecimal.ZERO;

    @Column(name = "valor_reteica", precision = 18, scale = 2)
    private BigDecimal valorReteica = BigDecimal.ZERO;

    @Column(name = "tipo_doc_soporte", length = 20)
    private String tipoDocSoporte;

    @Column(name = "numero_doc_soporte", length = 50)
    private String numeroDocSoporte;

    @Column(name = "periodo_contable_id")
    private Long periodoContableId;
}
