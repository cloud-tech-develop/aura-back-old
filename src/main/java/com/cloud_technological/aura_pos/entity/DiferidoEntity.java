package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

/**
 * Gasto pagado por anticipado que nace de un documento distinto al gasto
 * (V185): hoy la línea DIFERIDO de una compra. Se amortiza en {@code meses}
 * cuotas (diferido_amortizacion.diferido_id): cada una DB gasto · CR la cuenta
 * del diferido, que es la misma que debitó la compra.
 */
@Entity
@Table(name = "diferido")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DiferidoEntity {

    public static final String ORIGEN_COMPRA = "COMPRA";
    public static final String VIGENTE = "VIGENTE";
    public static final String TERMINADO = "TERMINADO";
    public static final String ANULADO = "ANULADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "origen_tipo", nullable = false, length = 20)
    private String origenTipo;

    @Column(name = "origen_id", nullable = false)
    private Long origenId;

    @Column(name = "compra_detalle_id")
    private Long compraDetalleId;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private Integer meses;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "cuenta_gasto_id")
    private Long cuentaGastoId;

    @Column(name = "cuenta_diferido_id")
    private Long cuentaDiferidoId;

    @Column(name = "tercero_id")
    private Long terceroId;

    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = VIGENTE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
