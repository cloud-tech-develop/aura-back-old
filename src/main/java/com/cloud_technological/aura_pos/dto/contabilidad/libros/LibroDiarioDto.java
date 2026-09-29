package com.cloud_technological.aura_pos.dto.contabilidad.libros;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Libro diario: cada comprobante contabilizado del rango con todas sus
 * partidas (cuenta, tercero, débito, crédito), en orden cronológico.
 *
 * <p>Es el libro que se imprime y se firma: por eso trae los totales generales
 * y dice si cuadran, en vez de dejar que el contador lo sume a mano.
 */
@Data
public class LibroDiarioDto {

    /** Encabezado del libro impreso. */
    private String empresaNombre;
    private String nit;
    private LocalDate desde;
    private LocalDate hasta;
    private List<Comprobante> comprobantes = new ArrayList<>();
    private BigDecimal totalDebito = BigDecimal.ZERO;
    private BigDecimal totalCredito = BigDecimal.ZERO;
    private boolean cuadrado;

    @Data
    public static class Comprobante {
        private Long asientoId;
        private LocalDate fecha;
        private String numeroComprobante;
        private String tipoOrigen;
        private String descripcion;
        private BigDecimal debito = BigDecimal.ZERO;
        private BigDecimal credito = BigDecimal.ZERO;
        private List<Linea> lineas = new ArrayList<>();
    }

    @Data
    public static class Linea {
        private String cuentaCodigo;
        private String cuentaNombre;
        private String terceroDocumento;
        private String terceroNombre;
        private String descripcion;
        private BigDecimal debito;
        private BigDecimal credito;
    }
}
