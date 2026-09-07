package com.cloud_technological.aura_pos.dto.auditoria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * El resultado completo de la auditoría: el semáforo más los hallazgos.
 *
 * <p>Los contadores van aparte de la lista a propósito: la portada del reporte
 * los necesita sin recorrer nada, y quien abre el documento quiere saber si
 * tiene un problema antes de leer doce páginas.
 */
@Getter
@Setter
public class AuditoriaResultadoDto {

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private LocalDateTime generadoEn;

    /** Cuánto tardó. Se mide desde G1 para saber si hace falta generarlo aparte. */
    private Long duracionMs;

    /** Hallazgos de la operación del período. */
    private List<HallazgoDto> hallazgos = new ArrayList<>();

    /** Descuadres que vienen de antes: van en su propia sección del reporte. */
    private List<HallazgoDto> heredados = new ArrayList<>();

    private Integer alta = 0;
    private Integer media = 0;
    private Integer baja = 0;

    /**
     * La plata involucrada en los hallazgos ALTA: lo que no se puede explicar.
     *
     * <p>No se suman las tres severidades porque significan cosas distintas y
     * un total mezclado no quiere decir nada.
     */
    private BigDecimal montoEnRiesgo = BigDecimal.ZERO;

    /** true si no hay ni un hallazgo ALTA: el período cierra limpio. */
    public boolean estaLimpio() {
        return alta != null && alta == 0;
    }
}
