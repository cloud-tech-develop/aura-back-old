package com.cloud_technological.aura_pos.dto.asistencia_frente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Marcaje del mes desde una plantilla de Excel (V242). El front lee el archivo y
 * manda las filas; el back valida, clasifica las horas y, al confirmar, guarda
 * cada día como borrador del frente.
 */
public final class MarcajeExcelDtos {

    private MarcajeExcelDtos() {
    }

    /** Una fila del Excel: un trabajador en un día. */
    @Getter @Setter
    public static class FilaMarcajeDto {
        /** Número de fila en el Excel, para señalar errores. */
        private Integer fila;
        private String documento;
        private LocalDate fecha;
        /** "HH:mm". */
        private String horaEntrada;
        /** "HH:mm". */
        private String horaSalida;
        /** Descanso del día en minutos; vacío = el del turno del frente. */
        private Integer minutosDescanso;
        /** Texto del estado (Asistió, No asistió, Permiso…); vacío con horas = Asistió. */
        private String estado;
        private String observacion;
    }

    /** Cuerpo de validar y confirmar. */
    @Getter @Setter
    public static class ImportarMarcajeDto {
        private List<FilaMarcajeDto> filas = new ArrayList<>();
    }

    /** Cómo quedó una fila tras validarla. */
    @Getter @Setter
    public static class ResultadoFilaMarcajeDto {
        private Integer fila;
        private String documento;
        private String empleado;
        private LocalDate fecha;
        private String horaEntrada;
        private String horaSalida;
        private Integer minutosDescanso;
        private String estadoAsistencia;
        private BigDecimal horasTrabajadas = BigDecimal.ZERO;
        private BigDecimal horasExtra = BigDecimal.ZERO;
        private BigDecimal horasRecargo = BigDecimal.ZERO;
        /** OK | ADVERTENCIA | ERROR | OMITIDA (fila sin datos de asistencia). */
        private String resultado;
        private String mensaje;
    }

    /** Resultado de validar (o de confirmar) el Excel. */
    @Getter @Setter
    public static class ResultadoMarcajeDto {
        private boolean confirmado;
        /** Días distintos que trae el archivo. */
        private int dias;
        private int filasValidas;
        private int advertencias;
        private int errores;
        private int omitidas;
        private BigDecimal horasTrabajadas = BigDecimal.ZERO;
        private BigDecimal horasExtra = BigDecimal.ZERO;
        private BigDecimal horasRecargo = BigDecimal.ZERO;
        private List<ResultadoFilaMarcajeDto> filas = new ArrayList<>();
        /** Mensajes generales (configuración faltante, soporte no guardado…). */
        private List<String> avisos = new ArrayList<>();
    }

    /** Rango de días para enviar a revisión o aprobar en bloque. */
    @Getter @Setter
    public static class RangoDiasDto {
        private LocalDate desde;
        private LocalDate hasta;
        /** Al aprobar: enviar además a nómina (genera las horas extra y recargos). */
        private Boolean enviarNomina = Boolean.TRUE;
    }

    /** Un día del rango que no se pudo procesar. */
    @Getter @Setter
    public static class ErrorDiaDto {
        private LocalDate fecha;
        private String mensaje;

        public ErrorDiaDto(LocalDate fecha, String mensaje) {
            this.fecha = fecha;
            this.mensaje = mensaje;
        }
    }

    /** Resultado de una acción en bloque. */
    @Getter @Setter
    public static class ResultadoRangoDto {
        private int procesados;
        private List<ErrorDiaDto> errores = new ArrayList<>();
    }
}
