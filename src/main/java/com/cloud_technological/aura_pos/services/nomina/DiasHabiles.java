package com.cloud_technological.aura_pos.services.nomina;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import com.cloud_technological.aura_pos.utils.FestivosColombiaUtil;

/**
 * Días hábiles de vacaciones: todos menos domingos y festivos de Colombia (el sábado
 * cuenta). Puro; lo usan el módulo de Vacaciones y el de Prestaciones.
 */
public final class DiasHabiles {

    private DiasHabiles() {
    }

    /** Hábiles entre dos fechas, ambas inclusive (0 si hasta es anterior a desde). */
    public static int entre(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) return 0;
        Set<LocalDate> festivos = festivos(desde.getYear(), hasta.getYear());
        int dias = 0;
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1))
            if (esHabil(d, festivos)) dias++;
        return dias;
    }

    /** Primer día hábil después de la fecha (el día de regreso de unas vacaciones). */
    public static LocalDate siguienteHabil(LocalDate fecha) {
        Set<LocalDate> festivos = festivos(fecha.getYear(), fecha.getYear() + 1);
        LocalDate d = fecha.plusDays(1);
        while (!esHabil(d, festivos)) d = d.plusDays(1);
        return d;
    }

    private static boolean esHabil(LocalDate d, Set<LocalDate> festivos) {
        return d.getDayOfWeek() != DayOfWeek.SUNDAY && !festivos.contains(d);
    }

    private static Set<LocalDate> festivos(int desde, int hasta) {
        Set<LocalDate> out = new HashSet<>();
        for (int y = desde; y <= hasta; y++)
            for (FestivosColombiaUtil.Festivo f : FestivosColombiaUtil.calcular(y)) out.add(f.fecha);
        return out;
    }
}
