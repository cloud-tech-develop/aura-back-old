package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.laboral.JornadaConfigDto;
import com.cloud_technological.aura_pos.entity.TurnoTrabajoEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.TurnoTrabajoJPARepository;
import com.cloud_technological.aura_pos.repositories.laboral.CalendarioLaboralJPARepository;
import com.cloud_technological.aura_pos.repositories.laboral.FrenteTurnoJPARepository;

/**
 * Motor de clasificación de horas (G2): a partir de entrada/salida + turno +
 * calendario + config legal vigente, clasifica ordinarias/nocturnas/extras y
 * dominicales/festivas, y genera alertas de límites.
 */
@Service
public class ClasificadorHorasService {

    @Autowired private LaboralConfigService jornadaService;
    @Autowired private CalendarioLaboralJPARepository calendarioRepo;
    @Autowired private FrenteTurnoJPARepository frenteTurnoRepo;
    @Autowired private TurnoTrabajoJPARepository turnoRepo;

    /** Alerta simple {tipo, nivel, descripcion}. */
    public record Alerta(String tipo, String nivel, String descripcion) {}

    public static class Resultado {
        public BigDecimal total = BigDecimal.ZERO;
        public BigDecimal ordDiurna = BigDecimal.ZERO;
        public BigDecimal ordNocturna = BigDecimal.ZERO;
        public BigDecimal extraDiurna = BigDecimal.ZERO;
        public BigDecimal extraNocturna = BigDecimal.ZERO;
        public BigDecimal domFest = BigDecimal.ZERO;
        public BigDecimal extraDiurnaDomFest = BigDecimal.ZERO;
        public BigDecimal extraNocturnaDomFest = BigDecimal.ZERO;
        public boolean esDomFest = false;
        public final List<Alerta> alertas = new ArrayList<>();
    }

    public Resultado clasificar(Integer empresaId, Long frenteId, LocalDate fecha,
            LocalTime entrada, LocalTime salida, String estadoAsistencia) {
        Resultado r = new Resultado();

        boolean cuentaHoras = entrada != null && salida != null
                && !"NO_ASISTIO".equals(estadoAsistencia) && !"SIN_REGISTRO".equals(estadoAsistencia)
                && !"SUSPENDIDO".equals(estadoAsistencia);
        if (!cuentaHoras) return r;

        // ── Config legal vigente para la fecha ──
        JornadaConfigDto cfg = jornadaService.vigente(empresaId, fecha);
        LocalTime diurnaIni = cfg != null && cfg.getHoraDiurnaInicio() != null ? cfg.getHoraDiurnaInicio() : LocalTime.of(6, 0);
        LocalTime diurnaFin = cfg != null && cfg.getHoraDiurnaFin() != null ? cfg.getHoraDiurnaFin() : LocalTime.of(19, 0);
        BigDecimal maxDia = cfg != null && cfg.getMaxHorasExtraDia() != null ? cfg.getMaxHorasExtraDia() : new BigDecimal("2");
        if (cfg == null) {
            r.alertas.add(new Alerta("JORNADA_CONFIG_NO_EXISTE", "CRITICA",
                    "No hay configuración laboral vigente para " + fecha + "; se usaron valores por defecto"));
        }

        // ── Turno del frente (para horas ordinarias programadas y descanso) ──
        BigDecimal turnoHoras = new BigDecimal("8");
        int descansoMin = 0;
        boolean cruzaMedianoche = false;
        TurnoTrabajoEntity turno = resolverTurno(frenteId, empresaId, fecha);
        if (turno == null) {
            r.alertas.add(new Alerta("TURNO_NO_CONFIGURADO", "ADVERTENCIA",
                    "El frente no tiene turno configurado; se asumen 8 horas ordinarias"));
        } else {
            if (turno.getHorasOrdinariasProgramadas() != null) turnoHoras = turno.getHorasOrdinariasProgramadas();
            if (turno.getMinutosDescanso() != null) descansoMin = turno.getMinutosDescanso();
            cruzaMedianoche = Boolean.TRUE.equals(turno.getCruzaMedianoche());
        }

        // ── Intervalo bruto (minutos), manejando cruce de medianoche ──
        int entMin = entrada.toSecondOfDay() / 60;
        int salMin = salida.toSecondOfDay() / 60;
        if (salMin <= entMin) {
            if (cruzaMedianoche) {
                salMin += 24 * 60;
            } else {
                r.alertas.add(new Alerta("SALIDA_MENOR_ENTRADA", "CRITICA",
                        "La hora de salida es menor o igual a la de entrada"));
                return r;
            }
        }

        int diurnaIniMin = diurnaIni.toSecondOfDay() / 60;
        int diurnaFinMin = diurnaFin.toSecondOfDay() / 60;

        // Minutos diurnos = solape con la franja diurna del día y del día siguiente.
        int diurnaMin = solape(entMin, salMin, diurnaIniMin, diurnaFinMin)
                + solape(entMin, salMin, diurnaIniMin + 1440, diurnaFinMin + 1440);
        int nocturnaMin = (salMin - entMin) - diurnaMin;

        // Descanso: se descuenta primero de la franja diurna (almuerzo), luego nocturna.
        int desc = Math.max(0, descansoMin);
        int netDiurnaMin = Math.max(0, diurnaMin - desc);
        desc -= (diurnaMin - netDiurnaMin);
        int netNocturnaMin = Math.max(0, nocturnaMin - desc);

        BigDecimal netDiurna = horas(netDiurnaMin);
        BigDecimal netNocturna = horas(netNocturnaMin);
        BigDecimal total = netDiurna.add(netNocturna);
        r.total = total;
        if (total.signum() <= 0) return r;

        // ── Ordinaria vs extra (reparto proporcional por franja) ──
        BigDecimal ordTotal = total.min(turnoHoras);
        BigDecimal extraTotal = total.subtract(ordTotal).max(BigDecimal.ZERO);
        BigDecimal fOrd = ordTotal.divide(total, 6, RoundingMode.HALF_UP);
        BigDecimal fExtra = extraTotal.divide(total, 6, RoundingMode.HALF_UP);

        BigDecimal ordDiurna = netDiurna.multiply(fOrd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ordNocturna = netNocturna.multiply(fOrd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal extraDiurna = netDiurna.multiply(fExtra).setScale(2, RoundingMode.HALF_UP);
        BigDecimal extraNocturna = netNocturna.multiply(fExtra).setScale(2, RoundingMode.HALF_UP);

        // ── Dominical / festivo ──
        r.esDomFest = esDomingoOFestivo(empresaId, fecha);
        if (r.esDomFest) {
            r.domFest = ordTotal.setScale(2, RoundingMode.HALF_UP);
            r.extraDiurnaDomFest = extraDiurna;
            r.extraNocturnaDomFest = extraNocturna;
        } else {
            r.ordDiurna = ordDiurna;
            r.ordNocturna = ordNocturna;
            r.extraDiurna = extraDiurna;
            r.extraNocturna = extraNocturna;
        }

        // ── Límite diario de extras ──
        if (extraTotal.compareTo(maxDia) > 0) {
            r.alertas.add(new Alerta("HORAS_EXTRA_SUPERA_LIMITE_DIA", "CRITICA",
                    "Las horas extra del día (" + extraTotal + ") superan el máximo permitido (" + maxDia + ")"));
        }

        return r;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private TurnoTrabajoEntity resolverTurno(Long frenteId, Integer empresaId, LocalDate fecha) {
        return frenteTurnoRepo.findByFrenteIdAndDeletedAtIsNull(frenteId).stream()
                .filter(ft -> ft.getFechaInicio() == null || !ft.getFechaInicio().isAfter(fecha))
                .filter(ft -> ft.getFechaFin() == null || !ft.getFechaFin().isBefore(fecha))
                .findFirst()
                .flatMap(ft -> turnoRepo.findByIdAndEmpresaId(ft.getTurnoId(), empresaId))
                .orElse(null);
    }

    private boolean esDomingoOFestivo(Integer empresaId, LocalDate fecha) {
        if (fecha.getDayOfWeek() == DayOfWeek.SUNDAY) return true;
        return calendarioRepo.findByEmpresaIdAndFechaAndDeletedAtIsNull(empresaId, fecha)
                .map(c -> Boolean.TRUE.equals(c.getAplicaRecargo())
                        && ("FESTIVO_NACIONAL".equals(c.getTipoDia())
                            || "FESTIVO_REGIONAL".equals(c.getTipoDia())
                            || "DOMINGO".equals(c.getTipoDia())))
                .orElse(false);
    }

    /** Solape de [a,b] con [x,y] en minutos. */
    private int solape(int a, int b, int x, int y) {
        return Math.max(0, Math.min(b, y) - Math.max(a, x));
    }

    private BigDecimal horas(int minutos) {
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
