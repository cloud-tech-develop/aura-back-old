package com.cloud_technological.aura_pos.services.nomina.vinculacion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ReglasConceptosCargoTest {

    @Test
    void deLeySiempreAplicaAunqueNoEsteEnElCargo() {
        assertTrue(ReglasConceptosCargo.aplica("DED_SALUD", true, Set.of("CN1000"), Set.of("DED_SALUD")));
    }

    @Test
    void sinPlantillaAplicaTodo() {
        assertTrue(ReglasConceptosCargo.aplica("CN1000", false, Set.of(), Set.of()));
    }

    @Test
    void conPlantillaSoloLoQueEstaEnElCargo() {
        assertTrue(ReglasConceptosCargo.aplica("CN1000", false, Set.of("CN1000"), Set.of()));
        assertFalse(ReglasConceptosCargo.aplica("CN1001", false, Set.of("CN1000"), Set.of()));
    }

    @Test
    void loExcluidoNoAplica() {
        assertFalse(ReglasConceptosCargo.aplica("CN1000", false, Set.of("CN1000"), Set.of("CN1000")));
        assertFalse(ReglasConceptosCargo.aplica("CN1000", false, Set.of(), Set.of("CN1000")));
    }
}
