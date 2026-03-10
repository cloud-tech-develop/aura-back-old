package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoPosDto {
    private Long id;
    private String sku;
    private String codigoBarras;
    private String nombre;
    private String descripcion;
    private String imagenUrl;
    private String tipoProducto;
    private Boolean manejaInventario;
    private Boolean manejaLotes;
    private Boolean manejaSerial;
    private BigDecimal precio;
    private BigDecimal costo;
    private BigDecimal ivaPorcentaje;
    private BigDecimal impoconsumo;
    private Long categoriaId;
    private String categoriaNombre;
    private Long marcaId;
    private String marcaNombre;
    private Long unidadMedidaId;
    private String unidadMedidaNombre;
    private BigDecimal stockActual;
    private Boolean activo;
    private BigDecimal precioFinal;      // precio después de descuento automático
    private String descuentoNombre;      // "Happy Hour", "Promo viernes", etc.
    private BigDecimal descuentoValor;
    private Boolean esCompuesto = false;
    private Boolean visibleEnPos;
    private List<ComponentePosDto> componentes = new ArrayList<>();
    
    // Presentación por defecto para venta
    private Long presentacionId;
    private String presentacionNombre;
    private String presentacionCodigoBarras;
    private BigDecimal presentacionPrecio;
    private BigDecimal presentacionFactorConversion;
}
