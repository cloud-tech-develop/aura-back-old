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
    private Boolean permitirStockNegativo;
    private BigDecimal precio;
    private BigDecimal precio2;
    private BigDecimal precio3;
    private BigDecimal costo;
    private BigDecimal ivaPorcentaje;
    private Boolean ivaIncluido = false;
    private BigDecimal impoconsumo;
    private Long categoriaId;
    private String categoriaNombre;
    private Long marcaId;
    private String marcaNombre;
    private Long unidadMedidaId;
    private String unidadMedidaNombre;
    private String unidadMedidaAbreviatura;
    private BigDecimal stockActual;
    /** Vencimiento del próximo lote con stock (sin contar los vencidos). */
    private java.time.LocalDate proximoVencimiento;
    private Integer diasParaVencer;
    /** Stock en lotes ya vencidos: no se vende si la empresa lo bloquea. */
    private BigDecimal stockVencido;
    private Integer diasAlertaVencimiento;
    private Boolean bloquearVencidos;
    private Boolean activo;
    private BigDecimal precioFinal;      // precio después de descuento automático
    private String descuentoNombre;      // "Happy Hour", "Promo viernes", etc.
    private BigDecimal descuentoValor;
    private Boolean esCompuesto = false;
    private Boolean visibleEnPos;
    private List<ComponentePosDto> componentes = new ArrayList<>();
    /** false = el POS solo ofrece sus presentaciones. */
    private Boolean vendePorUnidad = true;
    /** Presentaciones a la venta, con su stock en presentaciones completas. */
    private List<PresentacionPosDto> presentaciones = new ArrayList<>();
    
    // Presentación por defecto para venta
    private Long presentacionId;
    private String presentacionNombre;
    private String presentacionCodigoBarras;
    private BigDecimal presentacionPrecio;
    private BigDecimal presentacionFactorConversion;
}
