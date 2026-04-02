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
@Table(name = "producto")
@Getter
@Setter
public class ProductoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private CategoriaEntity categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private MarcaEntity marca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidad_medida_base_id")
    private UnidadMedidaEntity unidadMedidaBase;

    private String sku;

    @Column(name = "codigo_barras")
    private String codigoBarras;

    private String nombre;
    private String descripcion;

    @Column(name = "imagen_url")
    private String imagenUrl;

    @Column(name = "tipo_producto")
    private String tipoProducto;

    @Column(name = "maneja_inventario")
    private Boolean manejaInventario;

    @Column(name = "maneja_lotes")
    private Boolean manejaLotes;

    @Column(name = "maneja_serial")
    private Boolean manejaSerial;

    @Column(name = "permitir_stock_negativo")
    private Boolean permitirStockNegativo = false;

    private BigDecimal costo;
    private BigDecimal precio;

    @Column(name = "precio_2")
    private BigDecimal precio2;

    @Column(name = "precio_3")
    private BigDecimal precio3;

    @Column(name = "iva_porcentaje")
    private BigDecimal ivaPorcentaje;

    private BigDecimal impoconsumo;
    private Boolean activo;
    @Column(name = "visible_en_pos")
    private Boolean visibleEnPos = true;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
