package com.cloud_technological.aura_pos.dto.comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Imputación contable de un comprobante de caja (recibo de caja / egreso): el
 * asiento del documento que movió la plata, con sus cuentas, para el PDF.
 */
@Getter
@Setter
public class ComprobanteContableDto {
    private Long asientoId;
    /** Número del asiento (GT-000003, RC-000012…). */
    private String numeroAsiento;
    private LocalDate fecha;
    private String descripcion;
    private String estado;
    /** Documento de identidad del beneficiario (si el asiento lo tiene). */
    private String beneficiarioDocumento;
    private String elaboradoPor;
    private BigDecimal totalDebito = BigDecimal.ZERO;
    private BigDecimal totalCredito = BigDecimal.ZERO;
    private List<Linea> lineas = new ArrayList<>();

    @Getter
    @Setter
    public static class Linea {
        private String cuentaCodigo;
        private String cuentaNombre;
        private String terceroNombre;
        private String terceroDocumento;
        private String centroCostoNombre;
        private String descripcion;
        private BigDecimal debito;
        private BigDecimal credito;
    }
}
