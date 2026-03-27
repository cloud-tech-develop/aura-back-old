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

@Entity
@Table(name = "pedido_vendedor")
@Getter
@Setter
public class PedidoVendedorEntity {

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
    @JoinColumn(name = "vendedor_id", referencedColumnName = "id")
    private UsuarioEntity vendedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private TerceroEntity cliente;

    @Column(name = "numero_pedido")
    private String numeroPedido;

    private String estado;

    private BigDecimal subtotal;

    @Column(name = "descuento_total")
    private BigDecimal descuentoTotal;

    @Column(name = "impuesto_total")
    private BigDecimal impuestoTotal;

    private BigDecimal total;

    private String observaciones;

    @Column(name = "metodo_pago")
    private String metodoPago;

    @Column(name = "referencia_pago")
    private String referenciaPago;

    @Column(name = "fecha_cobro")
    private LocalDateTime fechaCobro;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "pedidoVendedor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PedidoVendedorDetalleEntity> detalles;
}
