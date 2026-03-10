package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProductoDto {
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
    private String tipoProducto = "ESTANDAR";
    private Boolean manejaInventario = true;
    private Boolean manejaLotes = false;
    private Boolean manejaSerial = false;
    private BigDecimal costo = BigDecimal.ZERO;
    private BigDecimal precio = BigDecimal.ZERO;
    private BigDecimal ivaPorcentaje = BigDecimal.ZERO;
    private BigDecimal impoconsumo = BigDecimal.ZERO;
    private Boolean activo = true;
    private Boolean visibleEnPos;
}
