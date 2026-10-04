package com.cloud_technological.aura_pos.services.implementations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Factus exige "company" (razón social) para persona jurídica: vacío da 422. */
class FactusRazonSocialTest {

    @Test
    void personaJuridicaLlevaLaRazonSocial() {
        assertEquals("DAR ARQUITECTURA SAS", FactusService.razonSocialFactus("1", "DAR ARQUITECTURA SAS"));
    }

    @Test
    void personaNaturalVaVacia() {
        assertEquals("", FactusService.razonSocialFactus("2", "Juan Pérez"));
    }
}
