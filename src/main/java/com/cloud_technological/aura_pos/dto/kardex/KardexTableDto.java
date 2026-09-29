package com.cloud_technological.aura_pos.dto.kardex;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KardexTableDto {
    private Long id;
    private String tipoMovimiento;
    /**
     * La etiqueta legible del tipo. La resuelve el backend desde el catálogo
     * para que el front no vuelva a mantener su propio mapa: el que tenía
     * cubría 9 de los 17 tipos y clasificaba la anulación de compra como
     * entrada, cuando saca stock.
     */
    private String tipoEtiqueta;
    /** ENTRADA | SALIDA | MIXTO. */
    private String grupo;
    private BigDecimal cantidad;
    private BigDecimal saldoAnterior;
    private BigDecimal saldoNuevo;
    private BigDecimal costoHistorico;
    private String referenciaOrigen;
    private LocalDateTime createdAt;
    private String productoNombre;
    /** El front ya lo mostraba, pero el SELECT no lo traía: salía vacío. */
    private String productoSku;
    private String sucursalNombre;
    private String codigoLote;
    private long totalRows;
}
