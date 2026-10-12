package com.cloud_technological.aura_pos.services.nomina.vinculacion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.repositories.nomina_vinculaciones.VinculacionReglasQueryRepository.NovedadPermitida;
import com.cloud_technological.aura_pos.services.nomina.vinculacion.ReglasNovedadesPermitidas.Novedad;

class ReglasNovedadesPermitidasTest {

    private static Novedad devengo(String tipo, String cantidad, String valor) {
        return new Novedad(tipo, new BigDecimal(cantidad), new BigDecimal(valor), false);
    }

    @Test
    void sinListaNoRestringeNada() {
        assertTrue(ReglasNovedadesPermitidas.validar(List.of(),
                List.of(devengo("BONO", "1", "500000"))).isEmpty());
    }

    @Test
    void conListaUnaNovedadQuePagaDebeEstarPermitida() {
        List<NovedadPermitida> permitidas = List.of(new NovedadPermitida("HORA_EXTRA_DIURNA", null, null));
        assertFalse(ReglasNovedadesPermitidas.validar(permitidas,
                List.of(devengo("BONO", "1", "100000"))).isEmpty());
        assertTrue(ReglasNovedadesPermitidas.validar(permitidas,
                List.of(devengo("HORA_EXTRA_DIURNA", "4", "40000"))).isEmpty());
    }

    @Test
    void ausenciasLegalesYDeduccionesNuncaSeRestringen() {
        List<NovedadPermitida> permitidas = List.of(new NovedadPermitida("HORA_EXTRA_DIURNA", null, null));
        assertTrue(ReglasNovedadesPermitidas.validar(permitidas, List.of(
                devengo("INCAPACIDAD", "3", "120000"),
                devengo("VACACIONES", "15", "650000"),
                new Novedad("PRESTAMO", BigDecimal.ONE, new BigDecimal("50000"), true))).isEmpty());
    }

    @Test
    void elTopeDeCantidadSeAplicaSobreLaSumaDelPeriodo() {
        List<NovedadPermitida> permitidas = List.of(
                new NovedadPermitida("HORA_EXTRA_DIURNA", new BigDecimal("10"), null));
        assertTrue(ReglasNovedadesPermitidas.validar(permitidas, List.of(
                devengo("HORA_EXTRA_DIURNA", "6", "60000"),
                devengo("HORA_EXTRA_DIURNA", "4", "40000"))).isEmpty());
        assertFalse(ReglasNovedadesPermitidas.validar(permitidas, List.of(
                devengo("HORA_EXTRA_DIURNA", "6", "60000"),
                devengo("HORA_EXTRA_DIURNA", "5", "50000"))).isEmpty());
    }

    @Test
    void elTopeDeValorSeAplicaSobreLaSumaDelPeriodo() {
        List<NovedadPermitida> permitidas = List.of(
                new NovedadPermitida("COMISION", null, new BigDecimal("1000000")));
        assertTrue(ReglasNovedadesPermitidas.validar(permitidas,
                List.of(devengo("COMISION", "1", "1000000"))).isEmpty());
        assertFalse(ReglasNovedadesPermitidas.validar(permitidas,
                List.of(devengo("COMISION", "1", "1000001"))).isEmpty());
    }
}
