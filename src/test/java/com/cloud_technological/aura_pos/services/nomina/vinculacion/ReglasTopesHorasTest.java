package com.cloud_technological.aura_pos.services.nomina.vinculacion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.repositories.nomina_vinculaciones.VinculacionReglasQueryRepository.TopeHoras;
import com.cloud_technological.aura_pos.services.nomina.vinculacion.ReglasTopesHoras.NovedadHoras;

class ReglasTopesHorasTest {

    private static final TopeHoras HED = new TopeHoras("HORA_EXTRA_DIURNA", "Hora extra diurna",
            new BigDecimal("2"), new BigDecimal("12"), new BigDecimal("40"));

    private static NovedadHoras horas(String cantidad, LocalDate fecha) {
        return new NovedadHoras("HORA_EXTRA_DIURNA", new BigDecimal(cantidad), fecha);
    }

    @Test
    void sinTopesNoRestringe() {
        assertTrue(ReglasTopesHoras.validar(List.of(), List.of(horas("30", null))).isEmpty());
    }

    @Test
    void dentroDeLosTopesPasa() {
        LocalDate lunes = LocalDate.of(2026, 10, 19);
        assertTrue(ReglasTopesHoras.validar(List.of(HED),
                List.of(horas("2", lunes), horas("2", lunes.plusDays(1)))).isEmpty());
    }

    @Test
    void pasarElTopeDiarioFalla() {
        LocalDate lunes = LocalDate.of(2026, 10, 19);
        assertFalse(ReglasTopesHoras.validar(List.of(HED),
                List.of(horas("1.5", lunes), horas("1", lunes))).isEmpty());
    }

    @Test
    void pasarElTopeSemanalFalla() {
        LocalDate lunes = LocalDate.of(2026, 10, 19);
        List<NovedadHoras> semana = List.of(horas("2", lunes), horas("2", lunes.plusDays(1)),
                horas("2", lunes.plusDays(2)), horas("2", lunes.plusDays(3)), horas("2", lunes.plusDays(4)),
                horas("2", lunes.plusDays(5)), horas("2", lunes.plusDays(6)));
        assertFalse(ReglasTopesHoras.validar(List.of(HED), semana).isEmpty());
    }

    @Test
    void sinFechaSoloCuentaElTopeDelMes() {
        assertTrue(ReglasTopesHoras.validar(List.of(HED), List.of(horas("40", null))).isEmpty());
        assertFalse(ReglasTopesHoras.validar(List.of(HED), List.of(horas("41", null))).isEmpty());
    }

    @Test
    void otroTipoSinTopeNoSeRevisa() {
        NovedadHoras nocturna = new NovedadHoras("HORA_EXTRA_NOCTURNA", new BigDecimal("50"), null);
        assertTrue(ReglasTopesHoras.validar(List.of(HED), List.of(nocturna)).isEmpty());
    }
}
