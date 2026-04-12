package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "asiento_contable")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AsientoContableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 500)
    private String descripcion;

    /** MANUAL | VENTA | COMPRA | GASTO | NOMINA | TESORERIA */
    @Column(name = "tipo_origen", nullable = false, length = 30)
    @Builder.Default
    private String tipoOrigen = "MANUAL";

    @Column(name = "origen_id")
    private Long origenId;

    @Column(name = "total_debito", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalDebito = BigDecimal.ZERO;

    @Column(name = "total_credito", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalCredito = BigDecimal.ZERO;

    /**
     * Consecutivo contable visible por tipo: CD-000001, RC-000045, CE-000120…
     * Prefijos: CD=Diario, VT=Venta, CO=Compra, GS=Gasto, NM=Nómina, TE=Tesorería
     */
    @Column(name = "numero_comprobante", length = 20)
    private String numeroComprobante;

    /** BORRADOR | CONTABILIZADO | ANULADO */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "CONTABILIZADO";

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "periodo_contable_id")
    private Long periodoContableId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** mappedBy apunta al campo @ManyToOne en AsientoDetalleEntity */
    @OneToMany(mappedBy = "asiento", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<AsientoDetalleEntity> detalles = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
