package com.cloud_technological.aura_pos.dto.nomina.pila;

import java.util.List;

import com.cloud_technological.aura_pos.services.nomina.pila.validacion.EstadoCotizantePila;
import com.cloud_technological.aura_pos.services.nomina.pila.validacion.EstadoPlanillaPila;
import com.cloud_technological.aura_pos.services.nomina.pila.validacion.SeveridadPila;

import lombok.Builder;
import lombok.Data;

/**
 * DTOs del reporte de validación de PILA (P1 del plan, agente validador §13).
 */
public final class ValidacionPilaDtos {

    private ValidacionPilaDtos() {}

    /** Fila de carga del catálogo de entidades (P2b). Fechas ISO 'yyyy-MM-dd'. */
    @Data
    public static class CargaEntidadDto {
        private String tipo;      // EPS | AFP | ARL | CCF
        private String codigo;
        private String nombre;
        private String vigenciaDesde;
        private String vigenciaHasta;
        private Boolean activo;
    }

    /** Un hallazgo (agente validador §13.2). */
    @Data
    @Builder
    public static class HallazgoDto {
        private String codigo;               // p.ej. PILA-ENT-001
        private SeveridadPila severidad;
        private String campo;
        private String valorRecibido;
        private String descripcion;
        private String condicionEsperada;
        private String accionSugerida;
        private String fundamento;
        private String riesgo;
    }

    /** Resultado por cotizante. */
    @Data
    @Builder
    public static class CotizanteValidacionDto {
        private Integer secuencia;
        private String nombreCompleto;
        private String tipoDocumento;
        private String numeroIdentificacion;
        private String tipoCotizante;
        private String subtipoCotizante;
        private EstadoCotizantePila estado;
        private List<HallazgoDto> hallazgos;
    }

    /** Reporte consolidado (agente validador §13.1). */
    @Data
    @Builder
    public static class ReporteValidacionDto {
        private String periodo;
        private String tipoPlanilla;
        private String tipoAportante;
        private String numeroAportante;

        private EstadoPlanillaPila estadoPlanilla;

        private int totalCotizantes;
        private int cotizantesAptos;
        private int cotizantesAptosConAdvertencias;
        private int cotizantesBloqueados;
        private int cotizantesNoEvaluables;

        private int totalErrores;
        private int totalAdvertencias;
        private int totalInformacion;
        private int totalNoEvaluable;

        /** Hallazgos de contexto/encabezado/totales (no atados a un cotizante). */
        private List<HallazgoDto> hallazgosPlanilla;
        private List<CotizanteValidacionDto> cotizantes;
    }
}
