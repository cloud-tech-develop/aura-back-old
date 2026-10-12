package com.cloud_technological.aura_pos.services.empresa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Catálogo de terceros nacionales que recibe toda empresa nueva (paso TERCEROS). */
class TercerosNacionalesServiceTest {

    private final TercerosNacionalesService service =
            new TercerosNacionalesService(mock(NamedParameterJdbcTemplate.class));

    @Test
    void elCatalogoTraeLoMismoQueElSqlDeReferencia() {
        // docs/sql/seed_terceros_bancos_seguridad_social.sql: EPS 24, AFP 7, CESANTIAS 5,
        // ARL 9, CCF 39, BANCO 28 → 103 terceros (5 NIT son EPS y caja a la vez).
        var porNit = service.porNit();
        assertEquals(103, porNit.size());
        Map<String, Long> porRol = porNit.stream().flatMap(t -> t.roles().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertEquals(24L, porRol.get("EPS"));
        assertEquals(7L, porRol.get("AFP"));
        assertEquals(5L, porRol.get("CESANTIAS"));
        assertEquals(9L, porRol.get("ARL"));
        assertEquals(39L, porRol.get("CCF"));
        assertEquals(28L, porRol.get("BANCO"));
    }

    @Test
    void unNitQueEsEpsYCajaQuedaConLosDosRolesYElCodigoDeLaEps() {
        var compensar = service.porNit().stream().filter(t -> t.nit().equals("860066942")).findFirst().orElseThrow();
        assertTrue(compensar.roles().contains("EPS"));
        assertTrue(compensar.roles().contains("CCF"));
        assertEquals("EPS008", compensar.codigo());
    }

    @Test
    void losBancosNoTienenCodigoDeSeguridadSocial() {
        assertTrue(service.porNit().stream().filter(t -> t.roles().equals(java.util.Set.of("BANCO")))
                .allMatch(t -> t.codigo() == null));
    }
}
