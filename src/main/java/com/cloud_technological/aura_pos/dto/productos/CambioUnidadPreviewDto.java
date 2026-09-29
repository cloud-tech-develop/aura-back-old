package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Vista previa (y resumen aplicado) de "Pasar a unidad". */
@Getter
@Setter
public class CambioUnidadPreviewDto {

    private Long productoId;
    private String productoNombre;

    private Long presentacionId;
    private String presentacionNombre;

    /** Unidades pequeñas que caben en la base actual (paca ×25 → 25). */
    private BigDecimal factor;

    private Long unidadActualId;
    private String unidadActualNombre;
    private Long unidadSugeridaId;
    private String nombrePresentacionSugerido;

    private BigDecimal precioAntes;
    private BigDecimal precioDespues;
    private BigDecimal costoAntes;
    private BigDecimal costoDespues;

    private List<StockSucursal> stock = new ArrayList<>();

    private int movimientosKardex;
    private int lotes;
    private int lineasVenta;
    private int recetas;
    private int preciosLista;

    private List<String> avisos = new ArrayList<>();
    private List<String> bloqueos = new ArrayList<>();

    public boolean isPuedeAplicar() {
        return bloqueos.isEmpty();
    }

    @Getter
    @Setter
    public static class StockSucursal {
        private String sucursalNombre;
        private BigDecimal antes;
        private BigDecimal despues;
    }
}
