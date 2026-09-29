package com.cloud_technological.aura_pos.dto.nomina.afiliacion;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** DTOs de afiliaciones a seguridad social (Fase 5.5). */
public final class AfiliacionDtos {

    private AfiliacionDtos() {}

    /** Una entrada del catálogo nacional (EPS/AFP/CCF/ARL). */
    @Getter
    @Setter
    public static class EntidadDto {
        private Long id;
        private String tipo;
        private String codigoOficial;
        private String nit;
        private String nombre;
    }

    /** Afiliación de un contrato, para listar. */
    @Getter
    @Setter
    public static class AfiliacionDto {
        private Long id;
        private String tipo;
        private Long entidadId;
        private String entidadNombre;
        private String entidadNit;
        /** null = el tercero no está enlazado al catálogo → sin código para PILA. */
        private String codigoOficial;
        private LocalDate fechaDesde;
        private LocalDate fechaHasta;
        private boolean vigente;
    }

    /** Cuerpo de afiliar/trasladar. */
    @Getter
    @Setter
    public static class AfiliarDto {
        /** Id del tercero (EPS/AFP/CCF/ARL) elegido del selector. */
        private Long entidadId;
        /** EPS | AFP | CCF | ARL. */
        private String tipo;
        private LocalDate desde;
    }

    /** Cambio del tipo de cotizante (código UGPP) del contrato. */
    @Getter
    @Setter
    public static class TipoCotizanteDto {
        private String tipoCotizante;
        private String subtipoCotizante;
    }
}
