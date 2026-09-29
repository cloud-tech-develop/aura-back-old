package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class UpdateProductoDto {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    private String sku;
    private String codigoBarras;
    private String descripcion;
    private String imagenUrl;
    private Long categoriaId;
    private Long marcaId;
    @NotNull(message = "La unidad de medida es obligatoria")
    private Long unidadMedidaBaseId;
    private String tipoProducto;
    /** VENTA | INSUMO | AMBOS. Null conserva el actual. */
    private String usoProducto;
    private Boolean manejaInventario;
    private Boolean manejaLotes;
    private Boolean manejaSerial;
    private Integer mesesGarantia;
    private Boolean permitirStockNegativo;
    private BigDecimal costo;
    private BigDecimal precio;
    private BigDecimal precio2;
    private BigDecimal precio3;
    private BigDecimal ivaPorcentaje;
    private Boolean ivaIncluido;
    private BigDecimal impoconsumo;
    private Boolean activo;
    private Boolean visibleEnPos;
    private Boolean vendePorUnidad;

    // ── Contabilidad (E4): null = hereda de la categoría / de la empresa ──
    private Long categoriaContableId;
    private Long cuentaIngresoId;
    private Long cuentaCostoId;
    private Long cuentaInventarioId;
}
