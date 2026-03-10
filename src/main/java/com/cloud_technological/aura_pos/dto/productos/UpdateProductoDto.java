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
    private Boolean manejaInventario;
    private Boolean manejaLotes;
    private Boolean manejaSerial;
    private BigDecimal costo;
    private BigDecimal precio;
    private BigDecimal ivaPorcentaje;
    private BigDecimal impoconsumo;
    private Boolean activo;
    private Boolean visibleEnPos;
}
