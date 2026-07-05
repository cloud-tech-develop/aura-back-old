package com.cloud_technological.aura_pos.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula los festivos nacionales de Colombia para un año:
 * fijos, trasladables a lunes (Ley Emiliani 51 de 1983) y relativos a la Pascua.
 */
public final class FestivosColombiaUtil {

    private FestivosColombiaUtil() {}

    public static class Festivo {
        public final LocalDate fecha;
        public final String nombre;
        public Festivo(LocalDate fecha, String nombre) { this.fecha = fecha; this.nombre = nombre; }
    }

    public static List<Festivo> calcular(int anio) {
        List<Festivo> res = new ArrayList<>();

        // ── Fijos (no se trasladan) ──
        res.add(new Festivo(LocalDate.of(anio, 1, 1), "Año Nuevo"));
        res.add(new Festivo(LocalDate.of(anio, 5, 1), "Día del Trabajo"));
        res.add(new Festivo(LocalDate.of(anio, 7, 20), "Día de la Independencia"));
        res.add(new Festivo(LocalDate.of(anio, 8, 7), "Batalla de Boyacá"));
        res.add(new Festivo(LocalDate.of(anio, 12, 8), "Inmaculada Concepción"));
        res.add(new Festivo(LocalDate.of(anio, 12, 25), "Navidad"));

        // ── Trasladables al lunes siguiente (Ley Emiliani) ──
        res.add(lunes(LocalDate.of(anio, 1, 6), "Día de los Reyes Magos"));
        res.add(lunes(LocalDate.of(anio, 3, 19), "Día de San José"));
        res.add(lunes(LocalDate.of(anio, 6, 29), "San Pedro y San Pablo"));
        res.add(lunes(LocalDate.of(anio, 8, 15), "Asunción de la Virgen"));
        res.add(lunes(LocalDate.of(anio, 10, 12), "Día de la Raza"));
        res.add(lunes(LocalDate.of(anio, 11, 1), "Día de Todos los Santos"));
        res.add(lunes(LocalDate.of(anio, 11, 11), "Independencia de Cartagena"));

        // ── Relativos a la Pascua ──
        LocalDate pascua = domingoPascua(anio);
        res.add(new Festivo(pascua.minusDays(3), "Jueves Santo"));
        res.add(new Festivo(pascua.minusDays(2), "Viernes Santo"));
        res.add(lunes(pascua.plusDays(39), "Ascensión del Señor"));
        res.add(lunes(pascua.plusDays(60), "Corpus Christi"));
        res.add(lunes(pascua.plusDays(68), "Sagrado Corazón de Jesús"));

        return res;
    }

    /** Traslada la fecha al lunes siguiente si no cae en lunes. */
    private static Festivo lunes(LocalDate fecha, String nombre) {
        LocalDate d = fecha;
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return new Festivo(d, nombre);
    }

    /** Domingo de Pascua (algoritmo de Butcher/Meeus, calendario gregoriano). */
    private static LocalDate domingoPascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }
}
