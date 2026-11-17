package com.cloud_technological.aura_pos.dto.asistencia_frente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Asistencia de un frente en un mes, por trabajador y por día: para ver de un
 * vistazo qué días asistió cada uno, sus horas y sus faltas.
 */
public final class CalendarioAsistenciaDtos {

    private CalendarioAsistenciaDtos() {
    }

    /** Lo registrado de un trabajador en un día. */
    @Getter @Setter
    public static class DiaAsistenciaDto {
        private LocalDate fecha;
        /** ASISTIO, NO_ASISTIO, PERMISO… */
        private String estadoAsistencia;
        /** Estado del día del frente: BORRADOR, ENVIADO_REVISION, APROBADO, ENVIADO_NOMINA… */
        private String estadoDia;
        private String horaEntrada;
        private String horaSalida;
        private BigDecimal horas = BigDecimal.ZERO;
        private BigDecimal horasExtra = BigDecimal.ZERO;
        /** Horas ordinarias nocturnas y dominicales/festivas (llevan recargo). */
        private BigDecimal horasRecargo = BigDecimal.ZERO;
    }

    /** Un trabajador del frente en el mes. */
    @Getter @Setter
    public static class TrabajadorMesDto {
        private Long empleadoId;
        private String nombre;
        private String documento;
        /** Días con asistencia (asistió, llegó tarde o salió temprano). */
        private int diasAsistidos;
        private int faltas;
        private BigDecimal horas = BigDecimal.ZERO;
        private BigDecimal horasExtra = BigDecimal.ZERO;
        private List<DiaAsistenciaDto> dias = new ArrayList<>();
    }

    /** El mes completo. */
    @Getter @Setter
    public static class CalendarioMesDto {
        private int anio;
        private int mes;
        /** Festivos del mes según el calendario laboral de la empresa. */
        private List<LocalDate> festivos = new ArrayList<>();
        private List<TrabajadorMesDto> trabajadores = new ArrayList<>();
    }
}
