package com.cloud_technological.aura_pos.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PoliticaRolesTest {

    private static void prohibido(String actual, String nuevo, String quien) {
        GlobalException e = assertThrows(GlobalException.class,
                () -> PoliticaRoles.validarAsignacion(actual, nuevo, quien));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Test
    void adminNoCreaPlatformAdminNiSuperAdmin() {
        prohibido(null, "PLATFORM_ADMIN", "ADMIN");
        prohibido(null, "platform_admin ", "ADMIN");
        prohibido(null, "SUPER_ADMIN", "ADMIN");
    }

    @Test
    void nadieDesdeLaEmpresaAsignaPlatformAdmin() {
        prohibido(null, "PLATFORM_ADMIN", "SUPER_ADMIN");
        prohibido("ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN");
    }

    @Test
    void superAdminAsignaSuperAdmin() {
        assertEquals("SUPER_ADMIN", PoliticaRoles.validarAsignacion(null, "super_admin", "SUPER_ADMIN"));
    }

    @Test
    void adminNoLeCambiaElRolAlDueno() {
        prohibido("SUPER_ADMIN", "CAJERO", "ADMIN");
    }

    @Test
    void elRolDePlataformaNoSeTocaDesdeLaEmpresa() {
        prohibido("PLATFORM_ADMIN", "ADMIN", "SUPER_ADMIN");
    }

    @Test
    void sinCambioSeConservaAunquePrivilegiado() {
        assertEquals("SUPER_ADMIN", PoliticaRoles.validarAsignacion("SUPER_ADMIN", "SUPER_ADMIN", "ADMIN"));
        assertEquals("SUPER_ADMIN", PoliticaRoles.validarAsignacion("SUPER_ADMIN", null, "ADMIN"));
        assertEquals("PLATFORM_ADMIN", PoliticaRoles.validarAsignacion("PLATFORM_ADMIN", "", "ADMIN"));
    }

    @Test
    void rolesNormalesSeNormalizanYCargosSePermiten() {
        assertEquals("CAJERO", PoliticaRoles.validarAsignacion(null, " cajero ", "ADMIN"));
        assertEquals("ADMIN", PoliticaRoles.validarAsignacion("CAJERO", "admin", "ADMIN"));
        assertEquals("GERENTE", PoliticaRoles.validarAsignacion(null, "GERENTE", "ADMIN"));
    }

    @Test
    void crearSinRolEsError() {
        GlobalException e = assertThrows(GlobalException.class,
                () -> PoliticaRoles.validarAsignacion(null, " ", "ADMIN"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void esPrivilegiado() {
        assertTrue(PoliticaRoles.esPrivilegiado("super_admin"));
        assertTrue(PoliticaRoles.esPrivilegiado("PLATFORM_ADMIN"));
        assertFalse(PoliticaRoles.esPrivilegiado("ADMIN"));
        assertFalse(PoliticaRoles.esPrivilegiado(null));
    }
}
