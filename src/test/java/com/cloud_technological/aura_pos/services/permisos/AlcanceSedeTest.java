package com.cloud_technological.aura_pos.services.permisos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Marcas /*SEDE:columna*{@literal /} en el SQL de listados y reportes (PLAN_PERMISOS P9). */
class AlcanceSedeTest {

    private static final String SQL = "SELECT * FROM merma m WHERE m.empresa_id = :e /*SEDE:m.sucursal_id*/ ORDER BY 1";

    private AlcanceSede alcance(String modo, boolean todas, List<Long> sedes) {
        PermisoUsuarioService permisos = mock(PermisoUsuarioService.class);
        SecurityUtils seguridad = mock(SecurityUtils.class);
        when(seguridad.getUsuarioId()).thenReturn(5L);
        when(seguridad.getRol()).thenReturn("CAJERO");
        PermisosUsuarioDto p = new PermisosUsuarioDto();
        p.setTodasLasSedes(todas);
        p.setSucursales(sedes);
        when(permisos.efectivos(5)).thenReturn(p);
        return new AlcanceSede(permisos, seguridad, modo);
    }

    @Test
    void conSedesLimitadasLaMarcaSeVuelveFiltro() {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = alcance("BLOQUEAR", false, List.of(2L, 3L)).aplicar(SQL, params);
        assertEquals("SELECT * FROM merma m WHERE m.empresa_id = :e  AND m.sucursal_id IN (:alcanceSedes) ORDER BY 1", sql);
        assertEquals(List.of(2L, 3L), params.getValue("alcanceSedes"));
    }

    @Test
    void conTodasLasSedesLaMarcaDesaparece() {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = alcance("BLOQUEAR", true, List.of()).aplicar(SQL, params);
        assertFalse(sql.contains("SEDE"));
        assertFalse(sql.contains("alcanceSedes"));
        assertFalse(params.hasValue("alcanceSedes"));
    }

    @Test
    void enModoObservarNoRecorta() {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = alcance("OBSERVAR", false, List.of(2L)).aplicar(SQL, params);
        assertFalse(sql.contains("alcanceSedes"));
    }

    @Test
    void sinSedesAsignadasNoVeNinguna() {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = alcance("BLOQUEAR", false, List.of()).aplicar(SQL, params);
        assertTrue(sql.contains("IN (:alcanceSedes)"));
        assertEquals(List.of(-1L), params.getValue("alcanceSedes"));
    }

    @Test
    void sqlSinMarcaQuedaIgual() {
        String sql = "SELECT 1";
        assertEquals(sql, alcance("BLOQUEAR", false, List.of(2L)).aplicar(sql, new MapSqlParameterSource()));
        assertNull(alcance("BLOQUEAR", false, List.of(2L)).aplicar(null, new MapSqlParameterSource()));
    }
}
