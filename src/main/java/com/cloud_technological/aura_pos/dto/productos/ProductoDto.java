package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoDto {
    private Long id;
    private Integer empresaId;
    private Long categoriaId;
    private String categoriaNombre;
    private Long marcaId;
    private String marcaNombre;
    private Long unidadMedidaBaseId;
    private String unidadMedidaNombre;
    private String sku;
    private String codigoBarras;
    private String nombre;
    private String descripcion;
    private String imagenUrl;
    private String tipoProducto;
    private Boolean manejaInventario;
    private Boolean manejaLotes;
    private Boolean manejaSerial;
    private Boolean permitirStockNegativo;
    private BigDecimal costo;
    private BigDecimal precio;
    private BigDecimal ivaPorcentaje;
    private BigDecimal impoconsumo;
    private Boolean activo;
    private Boolean visibleEnPos;
}
