package com.cloud_technological.aura_pos.dto.contabilidad.declaraciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Borrador de apoyo para diligenciar una declaración (IVA 300 o retención 350).
 *
 * <p>No reemplaza el formulario de la DIAN: agrupa los valores del mayor por
 * concepto, con el detalle por cuenta, para que el contador los traslade y los
 * pueda cuadrar. Por eso trae advertencias en vez de esconder lo dudoso.
 */
@Data
public class BorradorDeclaracionDto {

    /** IVA | RETENCION */
    private String tipo;
    private String empresaNombre;
    private String nit;
    private LocalDate desde;
    private LocalDate hasta;

    /** Bloques del borrador, en el orden del formulario. */
    private List<Seccion> secciones = new ArrayList<>();

    /** Resultado: positivo = a pagar, negativo = saldo a favor. */
    private BigDecimal resultado = BigDecimal.ZERO;
    private String resultadoEtiqueta;

    /** Bases por tarifa leídas de los documentos (informativas). */
    private List<Tarifa> basesPorTarifa = new ArrayList<>();

    /** Movimiento de cada cuenta que alimentó el borrador. */
    private List<CuentaDetalle> cuentas = new ArrayList<>();

    private List<String> advertencias = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Seccion {
        private String titulo;
        private List<Renglon> renglones = new ArrayList<>();
        private BigDecimal total = BigDecimal.ZERO;
        /** true si el total resta en el resultado (descontables, retenciones a favor). */
        private boolean resta;
        /** true si solo informa y no entra al resultado (ingresos, ReteICA). */
        private boolean informativa;

        public Seccion(String titulo, boolean resta, boolean informativa) {
            this.titulo = titulo;
            this.resta = resta;
            this.informativa = informativa;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Renglon {
        private String concepto;
        private BigDecimal valor;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tarifa {
        /** VENTAS | COMPRAS | GASTOS */
        private String origen;
        private BigDecimal tarifa;
        private BigDecimal base;
        private BigDecimal valor;
        private int documentos;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CuentaDetalle {
        private String codigo;
        private String nombre;
        private String tipoOrigen;
        private BigDecimal debito;
        private BigDecimal credito;
    }
}
