package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Un cambio del costo promedio de un producto (V226).
 *
 * <p>{@code producto.costo} se sobrescribe con cada movimiento; esta fila guarda
 * cuánto valía antes, cuánto quedó y qué documento lo movió, para que los socios
 * y el contador puedan reconstruir el costo de la mercancía en cualquier fecha.
 */
@Getter
@Setter
@Entity
@Table(name = "producto_costo_historial")
public class ProductoCostoHistorialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** Qué lo movió: ver {@code CostoHistorialService.Origen}. */
    @Column(name = "origen", nullable = false, length = 30)
    private String origen;

    @Column(name = "documento_id")
    private Long documentoId;

    @Column(name = "documento_numero", length = 60)
    private String documentoNumero;

    /** Proveedor de la compra, cuando aplica. */
    @Column(name = "tercero_id")
    private Long terceroId;

    /** Unidades base del movimiento, con signo. */
    @Column(name = "cantidad", precision = 38, scale = 6)
    private BigDecimal cantidad;

    /** Costo unitario al que entró (o salió) la mercancía del documento. */
    @Column(name = "costo_entrada", precision = 38, scale = 6)
    private BigDecimal costoEntrada;

    @Column(name = "stock_antes", precision = 38, scale = 6)
    private BigDecimal stockAntes;

    @Column(name = "stock_despues", precision = 38, scale = 6)
    private BigDecimal stockDespues;

    @Column(name = "costo_anterior", precision = 38, scale = 6)
    private BigDecimal costoAnterior;

    @Column(name = "costo_nuevo", nullable = false, precision = 38, scale = 6)
    private BigDecimal costoNuevo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "observacion", length = 200)
    private String observacion;

    @PrePersist
    void onCreate() {
        if (fecha == null) fecha = LocalDateTime.now();
    }
}
