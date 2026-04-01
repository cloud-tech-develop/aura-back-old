package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "devolucion")
public class DevolucionEntity {

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
    @JoinColumn(name = "venta_id")
    private VentaEntity venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private TerceroEntity cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    private Long consecutivo;

    /** TOTAL | PARCIAL */
    private String tipo;

    /** COMPLETADA | ANULADA */
    private String estado;

    private String motivo;

    @Column(name = "total_devolucion")
    private BigDecimal totalDevolucion;

    @Column(name = "reintegra_inventario")
    private Boolean reintegraInventario;

    private String observaciones;

    /** EFECTIVO | TRANSFERENCIA | NOTA_CREDITO | SIN_DEVOLUCION */
    @Column(name = "metodo_devolucion", length = 30)
    private String metodoDevolucion;

    /** TRUE si se descontó saldo de cuentas_cobrar */
    @Column(name = "afecto_cartera")
    private Boolean afectoCartera;

    @Column(name = "monto_cartera_afectado", precision = 18, scale = 2)
    private BigDecimal montoCarteraAfectado;

    /** ID del MovimientoCaja creado al devolver en efectivo (para reversión en anulación) */
    @Column(name = "movimiento_caja_id")
    private Long movimientoCajaId;

    /** ID del TesoreriaMovimiento creado al devolver dinero (para reversión en anulación) */
    @Column(name = "tesoreria_movimiento_id")
    private Long tesoreriaMovimientoId;

    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DevolucionDetalleEntity> detalles;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
