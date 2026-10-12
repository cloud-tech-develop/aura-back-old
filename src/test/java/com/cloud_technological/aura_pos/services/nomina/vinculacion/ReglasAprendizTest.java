package com.cloud_technological.aura_pos.services.nomina.vinculacion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;

/** Reglas del aprendiz SENA según la Ley 2466 de 2025. */
class ReglasAprendizTest {

    private static ContratoLaboralEntity contrato(String tipo, String fase) {
        ContratoLaboralEntity c = new ContratoLaboralEntity();
        c.setTipoContrato(tipo);
        c.setFase(fase);
        return c;
    }

    @Test
    void lectivaNoDescuentaNiCotizaPensionNiCausaPrestaciones() {
        ContratoLaboralEntity c = contrato("APRENDIZAJE", "LECTIVA");
        assertTrue(ReglasAprendiz.conceptoNoAplica(c, "DED_SALUD"));
        assertTrue(ReglasAprendiz.conceptoNoAplica(c, "DED_PENSION"));
        assertTrue(ReglasAprendiz.conceptoNoAplica(c, "APO_PENSION"));
        assertTrue(ReglasAprendiz.conceptoNoAplica(c, "PRO_PRIMA"));
        assertFalse(ReglasAprendiz.conceptoNoAplica(c, "APO_SALUD"));
        assertFalse(ReglasAprendiz.conceptoNoAplica(c, "APO_ARL"));
        assertFalse(ReglasAprendiz.causaPrestaciones(c));
    }

    @Test
    void practicaDescuentaSaludYPensionYCausaPrestaciones() {
        ContratoLaboralEntity c = contrato("APRENDIZAJE", "PRACTICA");
        assertFalse(ReglasAprendiz.conceptoNoAplica(c, "DED_SALUD"));
        assertFalse(ReglasAprendiz.conceptoNoAplica(c, "DED_PENSION"));
        assertFalse(ReglasAprendiz.conceptoNoAplica(c, "APO_PENSION"));
        assertFalse(ReglasAprendiz.conceptoNoAplica(c, "PRO_CESANTIAS"));
        assertTrue(ReglasAprendiz.causaPrestaciones(c));
    }

    @Test
    void nuncaParafiscalesNiFondoDeSolidaridad() {
        ContratoLaboralEntity c = contrato("APRENDIZAJE", "PRACTICA");
        assertTrue(ReglasAprendiz.conceptoNoAplica(c, "APO_CCF"));
        assertTrue(ReglasAprendiz.conceptoNoAplica(c, "APO_SENA"));
        assertTrue(ReglasAprendiz.conceptoNoAplica(c, "APO_ICBF"));
        assertTrue(ReglasAprendiz.conceptoNoAplica(c, "DED_FONDO_SOLIDARIDAD"));
    }

    @Test
    void otrosContratosNoSeAfectan() {
        ContratoLaboralEntity c = contrato("INDEFINIDO", null);
        assertFalse(ReglasAprendiz.conceptoNoAplica(c, "APO_CCF"));
        assertTrue(ReglasAprendiz.causaPrestaciones(c));
        assertFalse(ReglasAprendiz.causaPrestaciones(contrato("PRESTACION_SERVICIOS", null)));
    }
}
