package com.cloud_technological.aura_pos.dto.contabilidad.declaraciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Certificados de retención que la empresa, como agente retenedor, expide a
 * quienes les retuvo: en la fuente (2365), de IVA (2367) o de ICA (2368).
 *
 * <p>El valor sale del mayor por tercero (lo mismo que se declaró en el 350 o
 * en el municipio). La base y la tarifa salen del documento que originó la
 * retención (compra o gasto), porque el mayor no las guarda: si la retención
 * se registró en una nota contable no hay base y se dice en advertencias.
 * El PDF lo arma el front, uno por tercero.
 */
@Data
public class CertificadoRetencionDto {

    /** FUENTE | IVA | ICA */
    private String tipo;
    private String empresaNombre;
    private String nit;
    private String dv;
    private LocalDate desde;
    private LocalDate hasta;

    /** Un certificado por tercero, ordenados por nombre. */
    private List<Certificado> certificados = new ArrayList<>();

    private List<String> advertencias = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class Certificado {
        private Long terceroId;
        private String terceroNombre;
        private String tipoDocumento;
        private String numeroDocumento;
        private String dv;
        private String direccion;
        private String municipio;
        private String correo;
        private List<Linea> lineas = new ArrayList<>();
        private BigDecimal totalBase = BigDecimal.ZERO;
        private BigDecimal totalRetenido = BigDecimal.ZERO;
    }

    /** Lo retenido por concepto y tarifa en el período. */
    @Data
    @NoArgsConstructor
    public static class Linea {
        private String concepto;
        private String cuentaCodigo;
        /** Porcentaje; null cuando la retención no viene de un documento con tarifa. */
        private BigDecimal tarifa;
        /** null cuando ningún documento aporta la base (notas contables). */
        private BigDecimal base;
        private BigDecimal retenido = BigDecimal.ZERO;
    }
}
