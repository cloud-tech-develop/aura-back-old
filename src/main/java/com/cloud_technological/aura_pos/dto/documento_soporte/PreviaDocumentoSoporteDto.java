package com.cloud_technological.aura_pos.dto.documento_soporte;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Lo que se va a enviar a la DIAN, antes de enviarlo: proveedor, ítems,
 * totales y qué falta. Con faltantes el botón de emitir no se habilita.
 */
@Data
public class PreviaDocumentoSoporteDto {
    private String origenTipo;
    private Long origenId;
    private String origenNumero;

    private Long terceroId;
    private String proveedorDocumento;
    private String proveedorNombre;
    private String proveedorDireccion;
    private String proveedorMunicipio;

    private List<Item> items = new ArrayList<>();
    private BigDecimal base;
    private BigDecimal iva;
    private BigDecimal total;
    private BigDecimal retenciones;
    private BigDecimal netoAPagar;
    private String formaPago;

    private List<String> faltantes = new ArrayList<>();
    private List<String> advertencias = new ArrayList<>();
    private boolean puedeEmitirse;

    /** Documento soporte ya aceptado para este origen, si existe. */
    private DocumentoSoporteDto aceptado;
    /** Intentos anteriores (rechazados o eliminados), el más reciente primero. */
    private List<DocumentoSoporteDto> intentos = new ArrayList<>();

    @Data
    public static class Item {
        private String codigo;
        private String nombre;
        private BigDecimal cantidad;
        private BigDecimal precio;
        private BigDecimal descuentoPct;
        private BigDecimal ivaPct;
    }
}
