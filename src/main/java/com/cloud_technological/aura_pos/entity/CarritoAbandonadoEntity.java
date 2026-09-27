package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Carrito del POS que se vació sin vender (V182). */
@Entity
@Table(name = "carrito_abandonado")
@Getter
@Setter
@NoArgsConstructor
public class CarritoAbandonadoEntity {

    public static final String MOTIVO_VACIADO = "VACIADO";
    public static final String MOTIVO_PESTANA_CERRADA = "PESTANA_CERRADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "sucursal_id")
    private Integer sucursalId;

    @Column(name = "turno_caja_id")
    private Long turnoCajaId;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(nullable = false, length = 20)
    private String motivo;

    @Column(name = "iniciado_at", nullable = false)
    private LocalDateTime iniciadoAt;

    @Column(name = "vaciado_at", nullable = false)
    private LocalDateTime vaciadoAt;

    @Column(name = "duracion_segundos", nullable = false)
    private Integer duracionSegundos;

    @Column(nullable = false)
    private Integer items;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "carrito", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CarritoAbandonadoItemEntity> detalle = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
