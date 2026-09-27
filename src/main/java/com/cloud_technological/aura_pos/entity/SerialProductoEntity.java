package com.cloud_technological.aura_pos.entity;

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

@Getter
@Setter
@Entity
@Table(name = "serial_producto")
public class SerialProductoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoEntity producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id")
    private SucursalEntity sucursal;

    /** Dónde vive el stock (V172). Si el documento no la dice, es la
     *  bodega principal de la sucursal. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bodega_id")
    private BodegaEntity bodega;

    /** Único por producto (uq_serial_producto), no en toda la base. */
    private String serial;

    /** DISPONIBLE, VENDIDO, EN_GARANTIA, DEVUELTO_PROVEEDOR, MERMA, OBSEQUIADO, CONSUMO_INTERNO. */
    private String estado;

    @Column(name = "empresa_id")
    private Integer empresaId;

    @Column(precision = 15, scale = 2)
    private java.math.BigDecimal costo;

    @Column(name = "fecha_ingreso")
    private java.time.LocalDateTime fechaIngreso;

    /** Línea de compra que lo creó; null si se registró a mano sobre stock existente. */
    @Column(name = "compra_detalle_id")
    private Long compraDetalleId;

    /** Último documento por el que salió. */
    @Column(name = "documento_salida_tipo", length = 30)
    private String documentoSalidaTipo;

    @Column(name = "documento_salida_id")
    private Long documentoSalidaId;

    @Column(name = "garantia_cliente_hasta")
    private java.time.LocalDate garantiaClienteHasta;
}
