package com.cloud_technological.aura_pos.dto.empresas;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** Tableros de inicio por línea de uso (docs/PLAN_PERFIL_EMPRESA.md, P5–P7). */
public final class TableroInicioDtos {

    private TableroInicioDtos() {
    }

    /** Plata disponible y cartera: lo primero que mira un contador o un gerente. */
    @Data
    public static class ResumenFinancieroDto {
        /** Saldo de las cuentas 11 (caja y bancos) en el mayor. */
        private BigDecimal disponible = BigDecimal.ZERO;
        private BigDecimal cxcSaldo = BigDecimal.ZERO;
        private BigDecimal cxcVencido = BigDecimal.ZERO;
        private long cxcVencidas;
        private BigDecimal cxpSaldo = BigDecimal.ZERO;
        private BigDecimal cxpVencido = BigDecimal.ZERO;
        private long cxpVencidas;
    }

    /** Lo comercial del mes en curso, sin mostrador. */
    @Data
    public static class TableroComercialDto {
        private long facturasMes;
        private BigDecimal facturadoMes = BigDecimal.ZERO;
        private long comprasMes;
        private BigDecimal compradoMes = BigDecimal.ZERO;
        private long cotizacionesAbiertas;
        private BigDecimal cotizadoAbierto = BigDecimal.ZERO;
        private long pedidosAbiertos;
        private long productosStockBajo;
        private ResumenFinancieroDto financiero;
    }

    /** Lo que le falta configurar a la empresa para operar, por línea. */
    @Data
    public static class PuestaEnMarchaDto {
        private List<GrupoChequeoDto> grupos = new ArrayList<>();
        private int pendientes;
        private boolean completa;
    }

    @Data
    public static class GrupoChequeoDto {
        private String linea;
        private String nombre;
        private List<ChequeoDto> chequeos = new ArrayList<>();
    }

    @Data
    public static class ChequeoDto {
        private String codigo;
        private String titulo;
        private boolean ok;
        /** Qué falta o qué se encontró, en palabras. */
        private String detalle;
        /** Pantalla del front donde se resuelve. */
        private String ruta;

        public static ChequeoDto de(String codigo, String titulo, boolean ok, String detalle, String ruta) {
            ChequeoDto c = new ChequeoDto();
            c.setCodigo(codigo);
            c.setTitulo(titulo);
            c.setOk(ok);
            c.setDetalle(detalle);
            c.setRuta(ruta);
            return c;
        }
    }
}
