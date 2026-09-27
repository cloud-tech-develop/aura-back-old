package com.cloud_technological.aura_pos.dto.merma;

import java.math.BigDecimal;
import java.util.List;

import com.cloud_technological.aura_pos.dto.productos.ConsumoComponenteDto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MermaDetalleDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private Long productoPresentacionId;
    private String presentacionNombre;
    private BigDecimal cantidadPresentacion;
    private Long loteId;
    private String codigoLote;
    private BigDecimal cantidad;
    private BigDecimal costoUnitario;
    private BigDecimal subtotalCosto;
    /** Si el producto salió por receta, lo que se descontó de cada componente. */
    private List<ConsumoComponenteDto> componentes = List.of();
}
