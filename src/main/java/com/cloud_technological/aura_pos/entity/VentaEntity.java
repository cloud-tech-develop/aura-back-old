package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
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
@Table(name = "venta")
@Getter
@Setter
public class VentaEntity {
    
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
    @JoinColumn(name = "cliente_id")
    private TerceroEntity cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_caja_id")
    private TurnoCajaEntity turnoCaja;

    @Column(name = "tipo_documento")
    private String tipoDocumento;

    private String prefijo;
    private Long consecutivo;

    @Column(name = "fecha_emision")
    private LocalDateTime fechaEmision;

    private BigDecimal subtotal;

    @Column(name = "descuento_total")
    private BigDecimal descuentoTotal;

    @Column(name = "impuestos_total")
    private BigDecimal impuestosTotal;

    @Column(name = "total_pagar")
    private BigDecimal totalPagar;

    private String cufe;

    @Column(name = "qr_data")
    private String qrData;

    @Column(name = "estado_dian")
    private String estadoDian;

    @Column(name = "estado_venta")
    private String estadoVenta;

    @Column(name = "pago_parcial")
    private Boolean pagoParcial = false;

    @Column(name = "saldo_pendiente")
    private BigDecimal saldoPendiente = BigDecimal.ZERO;

    private String observaciones;

    @Column(name = "factus_url")
    private String factusUrl;

    @Column(name = "factus_numero")
    private String factusNumero;
}
