package com.cloud_technological.aura_pos.dto.nomina.pila;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * DTOs de PILA (Fase 6) para consulta desde el front.
 *
 * <p>Exponen un resumen: el cotizante tiene ~120 columnas (todo lo que el plano
 * exige), pero la pantalla necesita identificar a cada quien y ver sus IBC y
 * aportes. El archivo plano —que varía por operador— es un export aparte.
 */
public final class PilaDtos {

    private PilaDtos() {}

    @Getter
    @Setter
    public static class EncabezadoDto {
        private Long id;
        private String periodo;
        private String razonSocial;
        private String numeroDocumento;
        private String estado;
        private Integer totalEmpleados;
        private BigDecimal totalNomina;
    }

    @Getter
    @Setter
    public static class CotizanteDto {
        private Integer secuencia;
        private String nombreCompleto;
        private String tipoDocumento;
        private String numeroIdentificacion;
        private String tipoCotizante;

        private Integer diasCotizadosSalud;
        private Integer diasCotizadosPension;

        private BigDecimal ibcSalud;
        private BigDecimal ibcPension;

        private BigDecimal aporteSalud;
        private BigDecimal aportePension;
        private BigDecimal aporteRiesgos;
        private BigDecimal aporteCcf;

        private String codEps;
        private String codAfp;
        private String codArl;
        private String codCcf;

        /** Novedades de ingreso/retiro del período, para pintar en la fila. */
        private boolean ingreso;
        private boolean retiro;
    }
}
